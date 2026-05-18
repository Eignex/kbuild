import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}

val eignexDetektConfig =
    layout.buildDirectory.file("tmp/eignex-detekt.yml").get().asFile
if (!eignexDetektConfig.exists()) {
    eignexDetektConfig.parentFile.mkdirs()
    eignexDetektConfig.writeText(
        """
        formatting:
          active: true
          NoWildcardImports:
            active: false
        style:
          WildcardImport:
            active: false
          MagicNumber:
            active: false
          ReturnCount:
            active: false
        complexity:
          active: false
        comments:
          active: true
          AbsentOrWrongFileLicense:
            active: false
          CommentOverPrivateFunction:
            active: false
          CommentOverPrivateProperty:
            active: false
          DeprecatedBlockTag:
            active: true
          EndOfSentenceFormat:
            active: true
          KDocReferencesNonPublicProperty:
            active: true
          OutdatedDocumentation:
            active: true
          UndocumentedPublicClass:
            active: true
          UndocumentedPublicFunction:
            active: true
          UndocumentedPublicProperty:
            active: true
    """.trimIndent()
    )
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(
        files(eignexDetektConfig),
        rootProject.files("detekt.yml").filter { it.exists() }
    )
    source.setFrom(fileTree("src") {
        include("**/*.kt")
        include("**/*.kts")
    })
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    autoCorrect = true
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
}

val lintDocs = tasks.register("lintDocs") {
    group = "verification"
    description = "Runs detekt comment rules and Dokka (with failOnWarning) to validate KDoc."
    dependsOn(tasks.withType<Detekt>())
}

pluginManager.withPlugin("org.jetbrains.dokka") {
    extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension>("dokka") {
        dokkaSourceSets.configureEach {
            reportUndocumented.set(false)
        }
    }
    // Dokka 2 dropped failOnWarning; emulate by scanning the worker log.
    tasks.withType<org.jetbrains.dokka.gradle.tasks.DokkaGenerateTask>().configureEach {
        doLast {
            val log = workerLogFile.asFile.orNull ?: return@doLast
            if (!log.exists()) return@doLast
            val offenders = log.useLines { lines ->
                lines.filter { line ->
                    "Unresolved link" in line ||
                            "Unknown tag" in line ||
                            Regex("""\[(warn|error)]""", RegexOption.IGNORE_CASE).containsMatchIn(line)
                }.toList()
            }
            if (offenders.isNotEmpty()) {
                throw GradleException(
                    "Dokka reported ${offenders.size} issue(s):\n" +
                            offenders.joinToString("\n").prependIndent("  ")
                )
            }
        }
    }
    lintDocs.configure {
        dependsOn(tasks.named("dokkaGenerate"))
    }
}

tasks.named("check") {
    dependsOn(lintDocs)
}
