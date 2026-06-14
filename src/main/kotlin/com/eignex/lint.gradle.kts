import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask

plugins {
    id("dev.detekt")
}

dependencies {
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.3")
}

// The shared config the detekt tasks consume. Materialized by a task (below) rather than
// written here at configuration time: an eager write is skipped when the configuration
// cache is reused, leaving the consuming tasks pointing at a file that — on a fresh
// checkout that restored only the Gradle home, not build/ — never gets created.
val eignexDetektConfigContent =
    """
        ktlint:
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
          UnnecessaryFullyQualifiedName:
            active: true
          LoopWithTooManyJumpStatements:
            active: false
        complexity:
          active: false
        comments:
          active: true
          AbsentOrWrongFileLicense:
            active: false
          DocumentationOverPrivateFunction:
            active: false
          DocumentationOverPrivateProperty:
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

val eignexDetektConfigFile = layout.buildDirectory.file("tmp/eignex-detekt.yml")

val writeEignexDetektConfig = tasks.register("writeEignexDetektConfig") {
    description = "Materializes the shared Eignex detekt config the detekt tasks read."
    val output = eignexDetektConfigFile
    val content = eignexDetektConfigContent
    inputs.property("content", content)
    outputs.file(output).withPropertyName("eignexDetektConfig")
    doLast {
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    enableCompilerPlugin = true
    config.setFrom(
        files(eignexDetektConfigFile),
        rootProject.files("detekt.yml").filter { it.exists() }
    )
    source.setFrom(fileTree("src") {
        include("**/*.kt")
        include("**/*.kts")
    })
}

tasks.withType<Detekt>().configureEach {
    dependsOn(writeEignexDetektConfig)
    jvmTarget = "21"
    autoCorrect = true
    reports {
        html.required.set(true)
        sarif.required.set(false)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    dependsOn(writeEignexDetektConfig)
    jvmTarget = "21"
}

// Kotlin/Native (and Android dex) reject characters in backtick identifiers that the JVM
// accepts, so a test name like `foo (#389)` compiles for jvm but fails the native compile
// with a cryptic error. Catch it here, early and with a file:line, instead of in the build.
val checkNativeSafeTestNames = tasks.register("checkNativeSafeTestNames") {
    group = "verification"
    description = "Fails on backtick test names with chars Kotlin/Native rejects: ( ) #"
    val testSources = fileTree("src") {
        include("**/*Test/kotlin/**/*.kt")
    }
    // Capture projectDir as a plain File at configuration time; reading project state inside
    // doLast breaks the configuration cache (see the writeEignexDetektConfig note above).
    val projectDirFile = projectDir
    inputs.files(testSources).withPropertyName("testSources")
    doLast {
        val forbidden = Regex("""fun\s+`[^`]*[()#][^`]*`""")
        val offenders = testSources.files.flatMap { file ->
            file.useLines { lines ->
                lines.withIndex()
                    .filter { (_, line) -> forbidden.containsMatchIn(line) }
                    .map { (i, line) -> "${file.relativeTo(projectDirFile)}:${i + 1}: ${line.trim()}" }
                    .toList()
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Backtick test name(s) contain ( ) or # — breaks the Kotlin/Native compile:\n" +
                        offenders.joinToString("\n").prependIndent("  ")
            )
        }
    }
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
    dependsOn(checkNativeSafeTestNames)
}
