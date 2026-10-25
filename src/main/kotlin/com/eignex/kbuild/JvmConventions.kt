package com.eignex.kbuild

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Applies the bytecode baseline without replacing explicit compilation/task overrides. */
internal fun Project.configureKbuildJvm(build: EignexBuildExtension) {
    val baseline = build.jvmTarget.get()
    val bytecode = JvmTarget.fromTarget(if (baseline == 8) "1.8" else baseline.toString())
    val release = build.multiReleaseJvmVersion.orNull
    require(baseline <= build.jvmToolchain.get()) { "jvmTarget must not exceed jvmToolchain" }
    if (release != null) {
        require(release >= 9 && release > baseline && release <= build.jvmToolchain.get()) {
            "multiReleaseJvmVersion must be at least 9, greater than jvmTarget, and no greater than jvmToolchain"
        }
    }
    extensions.findByType(JavaPluginExtension::class.java)?.apply {
        sourceCompatibility = JavaVersion.toVersion(baseline)
        targetCompatibility = JavaVersion.toVersion(baseline)
    }
    tasks.withType(JavaCompile::class.java).configureEach {
        options.release.convention(baseline)
    }
    extensions.findByType(KotlinJvmProjectExtension::class.java)?.apply {
        compilerOptions.jvmTarget.convention(bytecode)
        val main = target.compilations.getByName("main")
        if (release != null) {
            val name = "java$release"
            val sources = project.extensions.getByType(SourceSetContainer::class.java).maybeCreate(name)
            val versioned = target.compilations.getByName(name)
            versioned.associateWith(main)
            tasks.named(versioned.compileKotlinTaskName, KotlinJvmCompile::class.java).configure {
                compilerOptions.jvmTarget.convention(JvmTarget.fromTarget(release.toString()))
                // KGP otherwise uses the finished JAR as a friend, creating a packaging cycle.
                friendPaths.setFrom(main.output.classesDirs)
            }
            versioned.compileJavaTaskProvider?.configure { options.release.convention(release) }
            packageVersionedClasses("jar", "sourcesJar", release, versioned.output.allOutputs, sources.allSource)
        }
        val test = target.compilations.getByName("test")
        registerPackagedTests(
            "", "jar", main.output.allOutputs, test.output.classesDirs, test.runtimeDependencyFiles, build,
        )
    }
    extensions.findByType(KotlinMultiplatformExtension::class.java)?.apply {
        targets.withType(KotlinJvmTarget::class.java).all {
            compilerOptions.jvmTarget.convention(bytecode)
            val main = compilations.getByName("main")
            val publishedJvmVersion = main.compileTaskProvider.map {
                it.compilerOptions.jvmTarget.get().target.removePrefix("1.").toInt()
            }
            val variants = setOf(
                apiElementsConfigurationName, runtimeElementsConfigurationName,
                "$apiElementsConfigurationName-published", "$runtimeElementsConfigurationName-published",
            )
            configurations.matching { it.name in variants }.all {
                attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, publishedJvmVersion)
            }
            if (release != null) {
                val versioned = compilations.maybeCreate("java$release")
                versioned.associateWith(main)
                tasks.named(versioned.compileKotlinTaskName, KotlinJvmCompile::class.java).configure {
                    compilerOptions.jvmTarget.convention(JvmTarget.fromTarget(release.toString()))
                    // KGP otherwise uses the finished JAR as a friend, creating a packaging cycle.
                    friendPaths.setFrom(main.output.classesDirs)
                }
                versioned.compileJavaTaskProvider?.configure { options.release.convention(release) }
                val sources = files(
                    versioned.defaultSourceSet.kotlin.matching { include("**/*.kt", "**/*.kts") },
                    fileTree("src/${versioned.defaultSourceSet.name}/java") { include("**/*.java") },
                )
                packageVersionedClasses(
                    artifactsTaskName, "${name}SourcesJar", release, versioned.output.allOutputs, sources,
                )
            }
            val test = compilations.getByName("test")
            registerPackagedTests(
                name, artifactsTaskName, main.output.allOutputs, test.output.classesDirs, test.runtimeDependencyFiles, build,
            )
        }
    }
}

private fun Project.packageVersionedClasses(
    jarName: String,
    sourcesJarName: String,
    release: Int,
    output: FileCollection,
    sources: FileCollection,
) {
    tasks.named(jarName, Jar::class.java).configure {
        manifest.attributes["Multi-Release"] = "true"
        from(output) {
            into("META-INF/versions/$release")
            // A versioned META-INF is forbidden by the MR-JAR specification.
            exclude("META-INF/**")
        }
    }
    tasks.named(sourcesJarName, Jar::class.java).configure {
        from(sources) { into("java$release") }
    }
}

private fun Project.registerPackagedTests(
    prefix: String,
    jarName: String,
    mainOutput: FileCollection,
    testClasses: FileCollection,
    runtime: FileCollection,
    build: EignexBuildExtension,
) {
    val jar = tasks.named(jarName, Jar::class.java)
    val toolchains = extensions.getByType(JavaToolchainService::class.java)
    val originalTest = tasks.named(if (prefix.isEmpty()) "test" else "${prefix}Test", Test::class.java)
    build.packagedTestJdks.get().distinct().forEach { jdk ->
        val taskName = if (prefix.isEmpty()) "testJarOnJava$jdk" else "${prefix}TestJarOnJava$jdk"
        val packagedTest = tasks.register(taskName, Test::class.java) {
            group = "verification"
            description = "Runs tests against the packaged JAR on Java $jdk."
            testClassesDirs = testClasses
            // Include test outputs/resources and dependencies, but never loose production classes.
            classpath = files(jar.flatMap { it.archiveFile }) + (runtime - mainOutput)
            javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(jdk)) })
            when (val framework = originalTest.get().options) {
                is JUnitPlatformOptions -> useJUnitPlatform { copyFrom(framework) }
                is JUnitOptions -> useJUnit { copyFrom(framework) }
                is TestNGOptions -> useTestNG { copyFrom(framework) }
            }
        }
        tasks.named("check").configure { dependsOn(packagedTest) }
    }
}
