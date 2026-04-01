plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
    id("io.github.sgtsilvio.gradle.maven-central-publishing") version "0.4.1"
}

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.1.0")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.5")
    implementation("io.github.sgtsilvio.gradle:gradle-maven-central-publishing:0.4.1")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
}

gradlePlugin {
    website = "https://github.com/Eignex/kbuild"
    vcsUrl = "https://github.com/Eignex/kbuild.git"
    plugins {
        named("jvm")     { id = "com.eignex.jvm" }
        named("kmp")     { id = "com.eignex.kmp" }
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
