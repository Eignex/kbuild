import com.eignex.internal.KBUILD_VERSION
import com.eignex.kbuild.EignexCliExtension
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.kover")
}

apply(plugin = "com.eignex.lint")

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

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

    // Release packaging: collect renamed, stripped native executables and the JVM dist zip
    // into build/release-assets/, ready to upload as GitHub release assets.
    val baseName = project.name
    val assetVersion = version.toString()
    val assetsDir = layout.buildDirectory.dir("release-assets")

    // Only host-linkable targets: macOS binaries cannot be cross-linked from Linux and vice versa.
    val hostManager = HostManager()
    val nativeExecutables = kotlin.targets.withType<KotlinNativeTarget>()
        .filter { hostManager.isEnabled(it.konanTarget) }
        .flatMap { it.binaries.filterIsInstance<Executable>() }
        .filter { it.buildType == NativeBuildType.RELEASE }
    val jvmDistZip = tasks.findByName("jvmDistZip") as Zip?

    tasks.register("releaseAssets") {
        group = "distribution"
        description = "Collects stripped native executables and the JVM dist zip into build/release-assets."
        nativeExecutables.forEach { dependsOn(it.linkTaskProvider) }
        jvmDistZip?.let { dependsOn(it) }

        val nativeAssets = nativeExecutables.map { binary ->
            // linux_x64 -> linux-x64, mingw_x64 -> windows-x64
            val osArch = binary.target.konanTarget.name.replace("mingw", "windows").replace('_', '-')
            binary.outputFile to "$baseName-$assetVersion-$osArch"
        }
        val jvmZip = jvmDistZip?.archiveFile
        // macOS needs -x (non-global symbols only): a full strip fails on linked Mach-O executables.
        val stripCommand = if (HostManager.hostIsMac) listOf("strip", "-x") else listOf("strip")

        doLast {
            val dir = assetsDir.get().asFile
            dir.deleteRecursively()
            dir.mkdirs()
            val assets = mutableListOf<File>()
            for ((outputFile, assetName) in nativeAssets) {
                val asset = outputFile.copyTo(dir.resolve(assetName))
                asset.setExecutable(true)
                // Best effort: keep the unstripped binary if strip is unavailable.
                // Discard output: an unread pipe fills up and deadlocks waitFor.
                runCatching {
                    ProcessBuilder(stripCommand + asset.absolutePath)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                        .waitFor()
                }
                assets += asset
            }
            jvmZip?.let { assets += it.get().asFile.copyTo(dir.resolve("$baseName-$assetVersion-jvm.zip")) }
            val digest = MessageDigest.getInstance("SHA-256")
            dir.resolve("SHA256SUMS").writeText(
                assets.joinToString("") { asset ->
                    val sum = digest.digest(asset.readBytes()).joinToString("") { "%02x".format(it) }
                    "$sum  ${asset.name}\n"
                }
            )
        }
    }
}
