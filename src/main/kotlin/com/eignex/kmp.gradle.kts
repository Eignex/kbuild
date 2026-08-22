import com.eignex.kbuild.JsTestFrameworkTimeout
import com.eignex.kbuild.applyKbuildCommonDependencies
import com.eignex.kbuild.getOrCreateEignexBuildExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover")
}

val eignexBuild = project.getOrCreateEignexBuildExtension()

apply(plugin = "com.eignex.publish")
apply(plugin = "com.eignex.lint")

kotlin {
    // Declare targets in your build.gradle.kts:
    //   jvm()
    //   js { browser(); nodejs() }
    //   linuxX64(); macosX64(); macosArm64(); mingwX64()

    applyKbuildCommonDependencies(project, eignexBuild)
}

// Node 25's V8 has stable exnref (wasmWasi's Kotlin 2.3 output); node 24's is experimental
// and flaky under load. No-op without js/wasm targets.
plugins.withType<WasmNodeJsPlugin> {
    the<WasmNodeJsEnvSpec>().version.set(eignexBuild.nodeVersion)
}
plugins.withType<NodeJsPlugin> {
    the<NodeJsEnvSpec>().version.set(eignexBuild.nodeVersion)
}

// Mocha's 2s per-test default is scheduling noise on a loaded runner. `useMocha { timeout }`
// reaches only the nodejs tasks; browser tasks run under Karma, hence both paths below.
val jsTestTimeout = eignexBuild.jsTestTimeout

// useConfigDirectory replaces the project's karma.config.d, so its contents are copied across.
val karmaConfigDir = layout.buildDirectory.dir("tmp/eignex-karma.config.d")
val projectKarmaConfigDir = layout.projectDirectory.dir("karma.config.d")

val writeEignexKarmaConfig = tasks.register("writeEignexKarmaConfig") {
    description = "Materializes the karma config directory the browser test tasks read."
    val out = karmaConfigDir
    val own = projectKarmaConfigDir
    val timeout = jsTestTimeout
        .map { it.removeSuffix("s").toInt() * 1000 }
    inputs.property("timeoutMs", timeout)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        // Mutating the nested key, not config.set, keeps the client.args used by --tests.
        dir.resolve("00-eignex-mocha-timeout.js").writeText(
            """
            config.client = config.client || {};
            config.client.mocha = Object.assign({}, config.client.mocha, { timeout: ${timeout.get()} });
            """.trimIndent() + "\n"
        )
        own.asFile.listFiles { f -> f.isFile && f.name.endsWith(".js") }
            ?.forEach { it.copyTo(dir.resolve(it.name), overwrite = true) }
    }
}

tasks.withType<KotlinJsTest>().configureEach { dependsOn(writeEignexKarmaConfig) }

afterEvaluate {
    if (eignexBuild.useMavenCentral.get()) repositories.mavenCentral()
    if (eignexBuild.abiValidationEnabled.get()) {
        @OptIn(ExperimentalAbiValidation::class)
        kotlin {
            abiValidation {}
        }
    }
    tasks.withType<KotlinJsTest>().configureEach {
        onTestFrameworkSet(JsTestFrameworkTimeout(jsTestTimeout.get(), karmaConfigDir.get().asFile))
    }
    kotlin {
        jvmToolchain(eignexBuild.jvmToolchain.get())
    }
    if (!eignexBuild.lintEnabled.get()) {
        tasks.matching { it.name.startsWith("detekt") || it.name == "checkKdoc" }.configureEach {
            enabled = false
        }
    }
    if (!eignexBuild.koverEnabled.get()) {
        tasks.matching { it.name.startsWith("kover") }.configureEach { enabled = false }
    }
}
