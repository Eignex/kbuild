import com.eignex.kbuild.EignexPublishExtension

plugins {
    `maven-publish`
    signing
    id("io.github.sgtsilvio.gradle.maven-central-publishing")
}

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

val eignexPublish = extensions.create<EignexPublishExtension>("eignexPublish")

afterEvaluate {
    val artifactId = eignexPublish.artifactId.getOrElse(project.name)
    val githubRepo = eignexPublish.githubRepo.get()

    fun MavenPublication.configureCommonPom() {
        pom {
            name.set(artifactId)
            description.set(eignexPublish.description.getOrElse(""))
            url.set("https://github.com/$githubRepo")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            scm {
                url.set("https://github.com/$githubRepo")
                connection.set("scm:git:https://github.com/$githubRepo.git")
                developerConnection.set("scm:git:ssh://git@github.com/$githubRepo.git")
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

    publishing {
        publications {
            if (components.findByName("java") != null) {
                // JVM project: single publication
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    this.artifactId = artifactId
                    configureCommonPom()
                }
            } else {
                // KMP project: publications are auto-created per target; configure POM on each
                // We rely on the KMP plugin to set the artifactId suffixes (e.g., -jvm, -js) here
                withType<MavenPublication>().configureEach {
                    configureCommonPom()
                }
            }
        }

        repositories {
            maven {
                name = "localStaging"
                url = uri(layout.buildDirectory.dir("staging-repo"))
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
}
