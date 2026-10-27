plugins {
    `kotlin-dsl`
    `maven-publish`
    id("com.eignex.publish.github")
    alias(libs.plugins.dokka)
}

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.asm)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.maven.central.publishing.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Left unpinned, kotlin-dsl targets whatever JDK the daemon runs, so the published bytecode
// follows the CI JDK. This value is the floor Gradle enforces on daemons loading the plugin.
kotlin {
    jvmToolchain(25)
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21 }
}

java {
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    val testJdks = listOf(17, 21, 25).map { jdk ->
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(jdk)) }
            .get().metadata.installationPath.asFile.absolutePath
    }
    systemProperty("kbuild.test.javaInstallations", testJdks.joinToString(","))
}

val generatedVersionDir = layout.buildDirectory.dir("generated/kbuildVersion/kotlin")

val generateKbuildVersion = tasks.register("generateKbuildVersion") {
    val outDir = generatedVersionDir
    val ver = version.toString()
    // The convention plugins are precompiled scripts, which get no version-catalog accessors, so
    // the versions they need are handed over as generated constants instead.
    val detektVer = libs.versions.detekt.get()
    inputs.property("version", ver)
    inputs.property("detektVersion", detektVer)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/eignex/internal/KbuildVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.eignex.internal\n\n" +
                "internal const val KBUILD_VERSION: String = \"$ver\"\n" +
                "internal const val DETEKT_VERSION: String = \"$detektVer\"\n"
        )
    }
}

sourceSets.main {
    kotlin.srcDir("build-logic/src/main/kotlin")
    kotlin.srcDir(generateKbuildVersion)
}

gradlePlugin {
    website = "https://github.com/Eignex/kbuild"
    vcsUrl = "https://github.com/Eignex/kbuild.git"
    plugins {
        named("jvm")     { id = "com.eignex.jvm" }
        named("kmp")     { id = "com.eignex.kmp" }
        named("cli")     { id = "com.eignex.cli" }
        named("publish") { id = "com.eignex.publish" }
        named("lint")    { id = "com.eignex.lint" }
        create("githubPublish") {
            id = "com.eignex.publish.github"
            implementationClass = "com.eignex.kbuild.GitHubPublishPlugin"
        }
    }
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier = "sources"
    from(sourceSets.main.get().allSource)
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier = "javadoc"
    // Without a source this is a 261-byte manifest, which Central accepts and nobody can read.
    dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
    from(layout.buildDirectory.dir("dokka/html"))
}

// Gradle's generated precompiled-script adapters link to script classes Dokka cannot resolve.
// Document the authored API rather than those implementation adapters.
dokka {
    dokkaSourceSets.configureEach {
        sourceRoots.setFrom(
            layout.projectDirectory.dir("src/main/kotlin"),
            layout.projectDirectory.dir("build-logic/src/main/kotlin"),
        )
    }
}

tasks.withType<org.jetbrains.dokka.gradle.tasks.DokkaGenerateTask>().configureEach {
    doLast {
        val log = workerLogFile.asFile.orNull ?: return@doLast
        if (!log.exists()) return@doLast
        val diagnostics = log.readLines().filter {
            Regex("""\[(warn|error)]""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }
        check(diagnostics.isEmpty()) { "Dokka reported issues:\n${diagnostics.joinToString("\n")}" }
    }
}

// Exercise publication-only artifacts in ordinary builds and release dry runs as well.
tasks.named("assemble") { dependsOn(sourcesJar, javadocJar) }

// The bootstrap included build and published helper compile the same plugin source.
githubPublish {
    repository.set("Eignex/kbuild")
    publicationNames.set(listOf("pluginMaven"))
    includeProjects.set(listOf(":kbuild-platform"))
    assetsDirectory.set(layout.buildDirectory.dir("release-assets"))
}

// Keep the existing CI entry point; staging and uploading are reusable helper tasks.
tasks.register("releaseAssets") { dependsOn("githubReleaseAssets") }

publishing {
    publications {
        withType<MavenPublication>().configureEach {
            if (name == "pluginMaven") {
                artifact(sourcesJar)
                artifact(javadocJar)
            }
            // Read lazily: the plugin-marker publications have not been given their artifactId
            // yet while this runs, and inside the pom block `artifactId` is the project's.
            val publication = this
            pom {
                name.set(providers.provider { publication.artifactId })
                description.set("Convention plugins for Eignex Kotlin projects")
                url.set("https://github.com/Eignex/kbuild")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                scm {
                    url.set("https://github.com/Eignex/kbuild")
                    connection.set("scm:git:https://github.com/Eignex/kbuild.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Eignex/kbuild.git")
                }
                developers {
                    developer {
                        id.set("rasros")
                        name.set("Rasmus Ros")
                        url.set("https://github.com/rasros")
                    }
                }
            }
        }
    }
}
