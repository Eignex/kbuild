import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    `java-platform`
    `maven-publish`
    signing
}

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

javaPlatform {
    allowDependencies()
}

dependencies {
    // Re-export upstream BOMs so consumers transitively get their pinned versions.
    api(platform(libs.kotlinx.coroutines.bom))
    api(platform(libs.kotlinx.serialization.bom))

    // Direct recommendations for libs that don't ship a BOM.
    constraints {
        api(libs.junit.jupiter)
        api(libs.junit.platform.launcher)
    }
}

publishing {
    publications {
        create<MavenPublication>("javaPlatform") {
            from(components["javaPlatform"])
            pom {
                name.set("kbuild-platform")
                description.set("Recommended version pins for Eignex Kotlin projects")
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
        // The root project owns the only Portal bundle and upload task.
        maven {
            name = "mavenCentralStaging"
            url = uri(rootProject.layout.buildDirectory.dir("maven-central-publishing/staging-repository"))
        }
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

// The root cleanup must finish before either project writes to the shared staging directory.
tasks.withType<PublishToMavenRepository>().matching {
    it.name.endsWith("ToMavenCentralStagingRepository")
}.configureEach {
    dependsOn(":cleanMavenCentralStagingRepository")
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
