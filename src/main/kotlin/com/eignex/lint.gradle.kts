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

// detekt's multiplatform type-resolution tasks compile all source sets as one module without the
// serialization plugin or K2 fragment structure, so @Serializable and expect/actual show up as
// "N compiler errors found during analysis" — harmless but noisy. The per-source-set tasks (kept)
// still cover every rule this config enables. JVM-only projects keep their (correct) type-resolution
// tasks: their compilation tasks are detektMain/detektTest, not the jvm-target names matched here.
pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    tasks.withType<Detekt>().configureEach {
        if (name == "detektMainJvm" || name == "detektTestJvm") {
            enabled = false
        }
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

// Cheap KDoc validation that runs in `check` (so broken docs surface on a local build, not
// only in the dokka job): block tags must be valid KDoc tags, and the package in a qualified
// [link] must actually exist in this project. This is a lexical scan — it deliberately does
// NOT resolve symbols (that needs the compiler frontend); full link resolution stays in
// lintDocs' dokka run. External links ([kotlin.*], [java.*]) are skipped via the package-root
// filter, so false positives stay near zero.
val checkKdoc = tasks.register("checkKdoc") {
    group = "verification"
    description = "Checks KDoc block tags are valid and qualified [links] name a real project package."
    val sources = fileTree("src") { include("**/*.kt") }
    val projectDirFile = projectDir
    inputs.files(sources).withPropertyName("kdocSources")
    doLast {
        val validTags = setOf(
            "param", "return", "receiver", "property", "throws", "exception",
            "constructor", "see", "sample", "author", "since", "suppress",
        )
        val files = sources.files
        // Index every package this project declares. A link is only checked when its package
        // sits under the project's own root (the longest common package prefix), so dependency
        // and stdlib refs — including sibling deps that share the group, e.g. com.eignex.skema
        // — and [kotlin.*]/[java.*] are left alone.
        val packageRegex = Regex("""^\s*package\s+([\w.]+)""")
        val knownPackages = files.flatMap { f ->
            f.useLines { lines -> lines.mapNotNull { packageRegex.find(it)?.groupValues?.get(1) }.toList() }
        }.toSet()
        val splits = knownPackages.map { it.split('.') }
        val projectRoot = if (splits.isEmpty()) "" else buildList {
            for (i in 0 until splits.minOf { it.size }) {
                val seg = splits.mapTo(mutableSetOf()) { it[i] }
                if (seg.size == 1) add(seg.first()) else break
            }
        }.joinToString(".")
        fun packageExists(p: String) = knownPackages.any { it == p || it.startsWith("$p.") }

        val tagRegex = Regex("""^\s*(?:/\*\*)?\s*\*?\s*@(\w+)""")
        val linkRegex = Regex("""\[([A-Za-z_][\w.]*(?:\(\))?)]""")
        val offenders = mutableListOf<String>()

        for (file in files) {
            var inKdoc = false
            var inFence = false // inside a ``` code fence within a KDoc block — skip checks there
            file.useLines { lines ->
                lines.forEachIndexed { idx, line ->
                    if (!inKdoc && "/**" in line) inKdoc = true
                    if (!inKdoc) return@forEachIndexed
                    if ("```" in line) {
                        inFence = !inFence
                        return@forEachIndexed
                    }
                    if (inFence) {
                        if ("*/" in line) { inKdoc = false; inFence = false }
                        return@forEachIndexed
                    }
                    val loc = "${file.relativeTo(projectDirFile)}:${idx + 1}"
                    // KDoc block tags are lowercase (@param, @return…); annotations on the
                    // declaration below (@Serializable, @Test) are PascalCase, so the
                    // lowercase-first guard ignores them.
                    tagRegex.find(line)?.groupValues?.get(1)?.let { tag ->
                        if (tag.first().isLowerCase() && tag !in validTags) {
                            offenders += "$loc: unknown KDoc tag @$tag"
                        }
                    }
                    if (projectRoot.isNotEmpty()) {
                        for (m in linkRegex.findAll(line)) {
                            val end = m.range.last + 1
                            if (end < line.length && line[end] == '(') continue // markdown URL: [text](url)
                            val ref = m.groupValues[1].removeSuffix("()")
                            val parts = ref.split('.')
                            val pkgSegs = parts.takeWhile { it.isNotEmpty() && it.first().isLowerCase() }
                            if (pkgSegs.isEmpty()) continue
                            val pkg = pkgSegs.joinToString(".")
                            if (pkg != projectRoot && !pkg.startsWith("$projectRoot.")) continue
                            // If the ref ends in a type segment (PascalCase) the package is
                            // unambiguous. If it's all-lowercase the last segment may be a
                            // top-level function rather than a sub-package, so accept the parent.
                            val ok = if (pkgSegs.size < parts.size) {
                                packageExists(pkg)
                            } else {
                                val parent = pkgSegs.dropLast(1).joinToString(".")
                                packageExists(pkg) || (parent.isNotEmpty() && packageExists(parent))
                            }
                            if (!ok) offenders += "$loc: KDoc link [$ref] references unknown package '$pkg'"
                        }
                    }
                    if ("*/" in line) inKdoc = false
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "KDoc validation failed:\n" + offenders.joinToString("\n").prependIndent("  ")
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

// `check` runs the fast verification — detekt (code + comment rules, incl. "public symbols are
// documented"), the native-name check, and the cheap KDoc check (valid tags + package exists).
// It deliberately does NOT pull `lintDocs`: that task's full dokka link-resolution run is the
// slowest thing in CI, so consumers run it as its own (parallel) job, keeping it off the
// check/build critical path. The cheap checks above still catch the common KDoc mistakes on a
// local build; dokka remains the thorough backstop.
tasks.named("check") {
    dependsOn(tasks.withType<Detekt>())
    dependsOn(checkNativeSafeTestNames)
    dependsOn(checkKdoc)
}
