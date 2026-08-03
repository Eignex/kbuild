plugins {
    `java-platform`
    `maven-publish`
    signing
    id("io.github.sgtsilvio.gradle.maven-central-publishing")
}

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

javaPlatform {
    allowDependencies()
}

dependencies {
    // Re-export upstream BOMs so consumers transitively get their pinned versions.
    api(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0"))
    api(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.11.0"))

    // Direct recommendations for libs that don't ship a BOM.
    constraints {
        api("org.junit.jupiter:junit-jupiter:6.1.2")
        api("org.junit.platform:junit-platform-launcher:6.1.2")
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
