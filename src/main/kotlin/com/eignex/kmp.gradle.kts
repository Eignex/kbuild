import com.eignex.kbuild.JsTestFrameworkTimeout
import com.eignex.kbuild.KBUILD_JVM_TOOLCHAIN
import com.eignex.kbuild.applyKbuildCommonDependencies
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

apply(plugin = "com.eignex.publish")
apply(plugin = "com.eignex.lint")

repositories { mavenCentral() }

kotlin {
    jvmToolchain(KBUILD_JVM_TOOLCHAIN)

    // The compiler's own validation, not binary-compatibility-validator, whose bundled ASM cannot
    // read class file major 69 and so fails outright on a JVM 25 target.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {}

    // Declare targets in your build.gradle.kts:
    //   jvm()
    //   js { browser(); nodejs() }
    //   linuxX64(); macosX64(); macosArm64(); mingwX64()

    applyKbuildCommonDependencies(project)
}

// Node 25's V8 has stable exnref (wasmWasi's Kotlin 2.3 output); node 24's is experimental
// and flaky under load. No-op without js/wasm targets.
val pinnedNodeVersion = "25.0.0"

plugins.withType<WasmNodeJsPlugin> {
    the<WasmNodeJsEnvSpec>().version.set(pinnedNodeVersion)
}
plugins.withType<NodeJsPlugin> {
    the<NodeJsEnvSpec>().version.set(pinnedNodeVersion)
}

// Mocha's 2s per-test default is scheduling noise on a loaded runner. `useMocha { timeout }` reaches
// only the nodejs tasks; browser tasks run under Karma, which keeps the default, hence both paths.
val jsTestTimeout = "120s"

// useConfigDirectory replaces the project's karma.config.d rather than adding to it, so anything
// already there is copied across.
val karmaConfigDir = layout.buildDirectory.dir("tmp/eignex-karma.config.d")
val projectKarmaConfigDir = layout.projectDirectory.dir("karma.config.d")

val writeEignexKarmaConfig = tasks.register("writeEignexKarmaConfig") {
    val out = karmaConfigDir
    val own = projectKarmaConfigDir
    val timeoutMs = jsTestTimeout.removeSuffix("s").toInt() * 1000
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        // Mutating the nested key rather than config.set keeps the client.args the Kotlin plugin
        // uses for --tests filtering.
        dir.resolve("00-eignex-mocha-timeout.js").writeText(
            """
            config.client = config.client || {};
            config.client.mocha = Object.assign({}, config.client.mocha, { timeout: $timeoutMs });
            """.trimIndent() + "\n"
        )
        own.asFile.listFiles { f -> f.isFile && f.name.endsWith(".js") }
            ?.forEach { it.copyTo(dir.resolve(it.name), overwrite = true) }
    }
}

tasks.withType<KotlinJsTest>().configureEach {
    dependsOn(writeEignexKarmaConfig)
    onTestFrameworkSet(JsTestFrameworkTimeout(jsTestTimeout, karmaConfigDir.get().asFile))
}
