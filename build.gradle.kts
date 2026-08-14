plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
    alias(libs.plugins.maven.central.publishing)
    alias(libs.plugins.dokka)
}

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
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
}

val generatedVersionDir = layout.buildDirectory.dir("generated/kbuildVersion/kotlin")

val generateKbuildVersion = tasks.register("generateKbuildVersion") {
    val outDir = generatedVersionDir
    val ver = version.toString()
    inputs.property("version", ver)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/eignex/internal/KbuildVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.eignex.internal\n\ninternal const val KBUILD_VERSION: String = \"$ver\"\n"
        )
    }
}

sourceSets.main {
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
    repositories {
        maven {
            name = "sonatypeSnapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            credentials {
                username = findProperty("mavenCentralUsername") as String? ?: ""
                password = findProperty("mavenCentralPassword") as String? ?: ""
            }
        }
    }
}

signing {
    val key = findProperty("signingKey") as String?
    val pass = findProperty("signingPassword") as String?
    // Snapshots are never validated, and each signature doubles the files uploaded.
    val isSnapshot = version.toString().endsWith("SNAPSHOT")
    if (key != null && pass != null && !isSnapshot) {
        useInMemoryPgpKeys(key, pass)
        sign(publishing.publications)
    } else if (isSnapshot) {
        logger.lifecycle("Signing skipped: $version is a snapshot.")
    } else {
        logger.lifecycle("Signing disabled: signingKey or signingPassword not defined.")
    }
}
