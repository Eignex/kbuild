import com.eignex.internal.DETEKT_VERSION
import com.eignex.kbuild.EignexLintExtension
import com.eignex.kbuild.getOrCreateEignexBuildExtension
import com.eignex.kbuild.KBUILD_JVM_TOOLCHAIN
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask

plugins {
    id("dev.detekt")
}

val eignexLint = extensions.create<EignexLintExtension>("eignexLint")
val eignexBuild = project.getOrCreateEignexBuildExtension()
eignexLint.enabled.convention(true)
eignexLint.useEignexConfig.convention(true)
eignexLint.autoCorrect.convention(true)
eignexLint.htmlReport.convention(true)
eignexLint.sarifReport.convention(false)

dependencies {
    // A rule set built against a different engine than the one running it is unsupported, so this
    // tracks the detekt version rather than carrying its own.
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:$DETEKT_VERSION")
}

// Written by a task, not here: a configuration-time write is skipped on configuration-cache
// reuse, so a checkout that restored the Gradle home but not build/ never gets the file.
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
    inputs.property("useEignexConfig", eignexLint.useEignexConfig)
    outputs.file(output).withPropertyName("eignexDetektConfig")
    doLast {
        val useEignexConfig = inputs.properties["useEignexConfig"] as Boolean
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(if (useEignexConfig) content else "")
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
    jvmTarget = KBUILD_JVM_TOOLCHAIN.toString()
    autoCorrect = eignexLint.autoCorrect.get()
    reports {
        html.required.set(eignexLint.htmlReport.get())
        sarif.required.set(eignexLint.sarifReport.get())
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    dependsOn(writeEignexDetektConfig)
    jvmTarget = KBUILD_JVM_TOOLCHAIN.toString()
}

afterEvaluate {
    tasks.withType<Detekt>().configureEach {
        jvmTarget = eignexBuild.jvmToolchain.get().toString()
        autoCorrect = eignexLint.autoCorrect.get()
        reports.html.required.set(eignexLint.htmlReport.get())
        reports.sarif.required.set(eignexLint.sarifReport.get())
    }
    if (!eignexLint.enabled.get()) {
        tasks.withType<Detekt>().configureEach { enabled = false }
        tasks.withType<DetektCreateBaselineTask>().configureEach { enabled = false }
        tasks.matching { it.name == "checkKdoc" || it.name == "checkNativeSafeTestNames" }.configureEach {
            enabled = false
        }
    }
}

// On KMP the detekt plugin adds a JVM-shaped pair that puts commonMain and jvmMain in one
// compilation: every expect shares a module with its actual, resolution breaks, and an actual
// reads as undocumented. The per-source-set tasks cover the same files, so no coverage is lost.
pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    tasks.matching { it.name == "detektMainJvm" || it.name == "detektTestJvm" }.configureEach {
        enabled = false
    }
}

// Kotlin/Native (and Android dex) reject backtick-identifier chars the JVM accepts: `foo (#389)`
// compiles for jvm then fails the native compile cryptically. Caught here with a file:line.
val checkNativeSafeTestNames = tasks.register("checkNativeSafeTestNames") {
    group = "verification"
    description = "Fails on backtick test names with chars Kotlin/Native rejects: ( ) #"
    val testSources = fileTree("src") {
        include("**/*Test/kotlin/**/*.kt")
    }
    // A plain File captured now: reading project state in doLast breaks the configuration cache.
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
                "Backtick test name(s) contain ( ) or #, breaking the Kotlin/Native compile:\n" +
                        offenders.joinToString("\n").prependIndent("  ")
            )
        }
    }
}

// Cheap KDoc validation in `check`, so broken docs surface locally and not only in the dokka
// job: block tags must be valid and a qualified [link]'s package must exist here. Lexical only:
// resolving symbols needs the compiler frontend and stays in lintDocs' dokka run.
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
        // Only links under the project's own root (the longest common package prefix) are
        // checked, leaving stdlib and dependency refs (incl. sibling com.eignex.*) alone.
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
            var inFence = false // inside a ``` code fence within a KDoc block; no checks there
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
                    // Block tags are lowercase (@param); annotations (@Test) are PascalCase,
                    // so the lowercase-first guard skips them.
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
                            // A trailing PascalCase segment makes the package unambiguous; an
                            // all-lowercase ref may end in a function, so accept the parent too.
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
    description = "Runs detekt comment rules and Dokka, failing on unresolved KDoc links and unknown tags."
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

// `check` deliberately omits `lintDocs`: its full dokka link-resolution run is the slowest
// thing in CI, so consumers run it as a separate parallel job. The checks above still catch
// the common KDoc mistakes locally; dokka stays the thorough backstop.
tasks.named("check") {
    dependsOn(tasks.withType<Detekt>())
    dependsOn(checkNativeSafeTestNames)
    dependsOn(checkKdoc)
}
