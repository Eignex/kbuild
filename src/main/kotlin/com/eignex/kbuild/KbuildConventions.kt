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
internal fun KotlinMultiplatformExtension.applyKbuildCommonDependencies(project: Project) {
    fun kbuildPlatform() = project.dependencies.platform("com.eignex:kbuild-platform:$KBUILD_VERSION")
    sourceSets.named("commonMain") {
        dependencies {
            implementation(kbuildPlatform())
        }
    }
    sourceSets.named("commonTest") {
        dependencies {
            implementation(kbuildPlatform())
            implementation(kotlin("test"))
        }
    }
}
