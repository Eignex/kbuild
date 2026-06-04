plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
    id("io.github.sgtsilvio.gradle.maven-central-publishing") version "0.5.0"
}

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.2.0")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.8")
    implementation("io.github.sgtsilvio.gradle:gradle-maven-central-publishing:0.5.0")
    implementation("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.3")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val generatedVersionDir = layout.buildDirectory.dir("generated/kbuildVersion/kotlin")

val generateKbuildVersion by tasks.registering {
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

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier = "sources"
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier = "javadoc"
}

publishing {
    publications {
        withType<MavenPublication>().configureEach {
            if (name == "pluginMaven") {
                artifact(sourcesJar)
                artifact(javadocJar)
            }
            pom {
                name.set(artifactId)
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

signing {
    val key = findProperty("signingKey") as String?
    val pass = findProperty("signingPassword") as String?
    if (key != null && pass != null) {
        useInMemoryPgpKeys(key, pass)
        sign(publishing.publications)
    } else {
        logger.lifecycle("Signing disabled: signingKey or signingPassword not defined.")
    }
}
