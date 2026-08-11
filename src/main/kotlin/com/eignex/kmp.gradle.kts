import com.eignex.internal.KBUILD_VERSION
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import com.eignex.kbuild.JsTestFrameworkTimeout
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
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
    jvmToolchain(25)

    // Records the published surface under api/ and fails the build when the code and the dump
    // disagree, so a change to what consumers see arrives as a diff in review. The Kotlin plugin's
    // own validation, not binary-compatibility-validator, whose bundled ASM cannot read class file
    // major 69 and so breaks on a JVM 25 target.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {}

    // Declare targets in your build.gradle.kts:
    //   jvm()
    //   js { browser(); nodejs() }
    //   linuxX64(); macosX64(); macosArm64(); mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform("com.eignex:kbuild-platform:$KBUILD_VERSION"))
        }
        commonTest.dependencies {
            implementation(project.dependencies.platform("com.eignex:kbuild-platform:$KBUILD_VERSION"))
            implementation(kotlin("test"))
        }
    }
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

// Mocha's per-test default is 2s, which a loaded CI runner exceeds on nothing more than
// scheduling. `useMocha { timeout }` only reaches the nodejs tasks; browser tasks run under Karma,
// which keeps the default, so the timeout has to be set on both paths.
val jsTestTimeout = "120s"

// Karma reads its extra config from a directory of .js files. The generated one is used instead of
// the project's own, so anything already in karma.config.d is copied across rather than dropped.
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

// Only the String and the File are captured; referencing the script's properties from inside the
// action stores a script reference on the task, which the configuration cache cannot serialize.
val capturedTimeout = jsTestTimeout
val capturedKarmaDir = karmaConfigDir.get().asFile

// The plugin registers the ABI check but does not attach it, so `check` would never run it.
tasks.named("check") { dependsOn("checkKotlinAbi") }

tasks.withType<KotlinJsTest>().configureEach {
    dependsOn(writeEignexKarmaConfig)
    onTestFrameworkSet(JsTestFrameworkTimeout(capturedTimeout, capturedKarmaDir))
}
