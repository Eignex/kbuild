plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
    id("io.github.sgtsilvio.gradle.maven-central-publishing") version "0.5.0"
    id("org.jetbrains.dokka") version "2.2.0"
}

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.2.0")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.9")
    implementation("io.github.sgtsilvio.gradle:gradle-maven-central-publishing:0.5.0")
    implementation("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.3")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Pinned, not inherited: without this the kotlin-dsl plugin targets whatever JDK the daemon runs,
// so the published bytecode silently follows the CI JDK. Consumers resolve on this value.
kotlin {
    jvmToolchain(25)
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
    // Without a source the jar is a 261-byte manifest and nothing else, which Central accepts
    // but leaves the published artifact with no api docs at all.
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
    // The snapshot repository runs no component validation, so a signature buys nothing there
    // and each one doubles the files uploaded per artifact.
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
