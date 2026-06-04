import com.eignex.internal.KBUILD_VERSION
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
    jvmToolchain(21)

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

// Pin Node.js to an LTS for the js/wasm test runners; the Kotlin plugin default
// (Node 25) intermittently crashes wasmWasiNodeTest. No-op without js/wasm targets.
val pinnedNodeVersion = "24.10.0"

plugins.withType<WasmNodeJsPlugin> {
    the<WasmNodeJsEnvSpec>().version.set(pinnedNodeVersion)
}
plugins.withType<NodeJsPlugin> {
    the<NodeJsEnvSpec>().version.set(pinnedNodeVersion)
}
tasks.withType<KotlinJsTest>().configureEach {
    // Node 24 needs this flag to run Kotlin 2.3 exnref exception-handling output.
    if (name == "wasmWasiNodeTest") nodeJsArgs += "--experimental-wasm-exnref"
}
