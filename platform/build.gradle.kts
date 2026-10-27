plugins {
    `java-platform`
    `maven-publish`
    id("com.eignex.publish.github")
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
}
