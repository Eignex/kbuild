import com.eignex.internal.KBUILD_VERSION
import com.eignex.kbuild.EignexCliExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.kover")
}

apply(plugin = "com.eignex.lint")

repositories { mavenCentral() }

val eignexCli = extensions.create<EignexCliExtension>("eignexCli")

kotlin {
    // 21, not newer: dists may be launched by wrappers on the system JVM,
    // so the bytecode must stay runnable there.
    jvmToolchain(21)

    // Declare targets in your build.gradle.kts:
    //   jvm()
    //   linuxX64(); macosX64(); macosArm64()
    // The executable conventions below apply to whatever targets exist.

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

// Eager all {}, not configureEach: the binaries DSL applies the distribution plugin,
// which calls afterEvaluate — forbidden inside lazy configuration actions.
kotlin.targets.withType<KotlinJvmTarget>().all {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    binaries {
        executable {
            mainClass.set(eignexCli.mainClass)
        }
    }
}

// entryPoint is not a lazy property, so read the extension after the consumer's script has run.
afterEvaluate {
    kotlin.targets.withType<KotlinNativeTarget>().configureEach {
        binaries.executable {
            eignexCli.entryPoint.orNull?.let { entryPoint = it }
        }
    }
}
