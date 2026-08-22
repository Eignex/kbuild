package com.eignex.kbuild

import com.eignex.internal.KBUILD_VERSION
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** JDK the plugins target. Shared so the Kotlin toolchain and detekt's jvmTarget cannot drift. */
internal const val KBUILD_JVM_TOOLCHAIN = 25

/**
 * Wires in the dependency baseline every multiplatform convention shares: the kbuild platform
 * BOM on `commonMain`/`commonTest`, and `kotlin-test` on `commonTest`.
 */
internal fun Project.getOrCreateEignexBuildExtension(): EignexBuildExtension =
    extensions.findByType(EignexBuildExtension::class.java)
        ?: extensions.create("eignexBuild", EignexBuildExtension::class.java).also {
        it.jvmToolchain.convention(KBUILD_JVM_TOOLCHAIN)
        it.usePlatformDependencies.convention(true)
        it.useKotlinTestDependency.convention(true)
        it.useMavenCentral.convention(true)
        it.platformGroup.convention("com.eignex")
        it.defaultGroup.convention("com.eignex")
        it.platformArtifact.convention("kbuild-platform")
        it.platformVersion.convention(KBUILD_VERSION)
        it.nodeVersion.convention("25.0.0")
        it.jsTestTimeout.convention("120s")
        it.koverEnabled.convention(true)
        it.abiValidationEnabled.convention(true)
        it.lintEnabled.convention(true)
    }

internal fun KotlinMultiplatformExtension.applyKbuildCommonDependencies(
    project: Project,
    build: EignexBuildExtension,
) {
    project.afterEvaluate {
        if (!build.usePlatformDependencies.get()) return@afterEvaluate
        fun kbuildPlatform() = project.dependencies.platform(
            "${build.platformGroup.get()}:${build.platformArtifact.get()}:${build.platformVersion.get()}",
        )
        sourceSets.named("commonMain") {
            dependencies {
                implementation(kbuildPlatform())
            }
        }
        sourceSets.named("commonTest") {
            dependencies {
                implementation(kbuildPlatform())
                if (build.useKotlinTestDependency.get()) implementation(kotlin("test"))
            }
        }
    }
}
