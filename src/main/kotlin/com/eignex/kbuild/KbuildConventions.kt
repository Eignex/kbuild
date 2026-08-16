package com.eignex.kbuild

import com.eignex.internal.KBUILD_VERSION
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * JDK the convention plugins compile and run against. Shared so the Kotlin toolchain and the
 * detekt jvmTarget cannot drift apart.
 */
internal const val KBUILD_JVM_TOOLCHAIN = 25

/**
 * Wires the kbuild platform BOM into `commonMain`/`commonTest` and `kotlin-test` into
 * `commonTest` — the dependency baseline every multiplatform convention (kmp, cli) shares.
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
