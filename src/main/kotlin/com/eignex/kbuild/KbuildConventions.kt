package com.eignex.kbuild

import com.eignex.internal.KBUILD_VERSION
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Default build JDK, independent of an explicitly configured bytecode baseline. */
internal const val KBUILD_JVM_TOOLCHAIN = 17

/** Creates the shared convention defaults. */
internal fun Project.getOrCreateEignexBuildExtension(): EignexBuildExtension =
    extensions.findByType(EignexBuildExtension::class.java)
        ?: extensions.create("eignexBuild", EignexBuildExtension::class.java).also {
        it.jvmToolchain.convention(KBUILD_JVM_TOOLCHAIN)
        it.jvmTarget.convention(it.jvmToolchain)
        it.jvmModuleRelease.convention(it.jvmTarget.map { version -> version.coerceAtLeast(9) })
        it.jvmModuleVersioned.convention(it.jvmTarget.map { version -> version < 9 })
        it.packagedTestJdks.convention(emptyList())
        it.usePlatformDependencies.convention(true)
        it.useKotlinTestDependency.convention(true)
        it.useMavenCentral.convention(true)
        it.platformGroup.convention("com.eignex")
        it.defaultGroup.convention("com.eignex")
        it.platformArtifact.convention("kbuild-platform")
        it.platformVersion.convention(KBUILD_VERSION)
        it.nodeVersion.convention("26.12.1")
        it.jsTestTimeout.convention("120s")
        it.koverEnabled.convention(true)
        it.abiValidationEnabled.convention(true)
        it.lintEnabled.convention(true)
    }

/** Keeps the build's version baseline out of published dependency variants. */
internal fun Project.createKbuildPlatformConfiguration(build: EignexBuildExtension): Configuration {
    val platform = configurations.create("kbuildPlatform") {
        isCanBeResolved = false
        isCanBeConsumed = false
        dependencies.add(project.dependencies.platform(
            "${build.platformGroup.get()}:${build.platformArtifact.get()}:${build.platformVersion.get()}",
        ))
    }
    // Includes the Java source sets KGP creates for multiplatform JVM targets.
    pluginManager.withPlugin("java-base") {
        extensions.getByType(SourceSetContainer::class.java).configureEach {
            configurations.named(compileClasspathConfigurationName) { extendsFrom(platform) }
            configurations.named(runtimeClasspathConfigurationName) { extendsFrom(platform) }
        }
    }
    return platform
}

internal fun KotlinMultiplatformExtension.applyKbuildCommonDependencies(
    project: Project,
    build: EignexBuildExtension,
) {
    project.afterEvaluate {
        if (build.usePlatformDependencies.get()) {
            val platform = project.createKbuildPlatformConfiguration(build)
            targets.configureEach {
                compilations.configureEach {
                    listOfNotNull(compileDependencyConfigurationName, runtimeDependencyConfigurationName).forEach { name ->
                        project.configurations.named(name) { extendsFrom(platform) }
                    }
                }
            }
            // KGP resolves shared metadata and NPM manifests separately from compilation classpaths.
            project.configurations.matching {
                it.name.endsWith("ResolvableDependenciesMetadata") || it.name.endsWith("NpmAggregated")
            }.configureEach { extendsFrom(platform) }
        }
        sourceSets.named("commonTest") {
            dependencies {
                if (build.useKotlinTestDependency.get()) implementation(kotlin("test"))
            }
        }
    }
}
