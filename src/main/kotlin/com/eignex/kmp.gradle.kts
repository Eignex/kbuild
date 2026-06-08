import com.eignex.internal.KBUILD_VERSION
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
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

// Node 25's V8 has stable exnref (wasmWasi's Kotlin 2.3 output); node 24's is experimental
// and flaky under load. No-op without js/wasm targets.
val pinnedNodeVersion = "25.0.0"

plugins.withType<WasmNodeJsPlugin> {
    the<WasmNodeJsEnvSpec>().version.set(pinnedNodeVersion)
}
plugins.withType<NodeJsPlugin> {
    the<NodeJsEnvSpec>().version.set(pinnedNodeVersion)
}
