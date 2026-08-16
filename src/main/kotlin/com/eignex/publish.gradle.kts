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
    // Opt out for internal modules (benchmarks, samples) that use the conventions but never
    // publish; skips the publication/signing wiring and the githubRepo requirement.
    if (!eignexPublish.publish.getOrElse(true)) return@afterEvaluate

    val artifactId = eignexPublish.artifactId.getOrElse(project.name)
    val githubRepo = eignexPublish.githubRepo.get()

    fun createJavadocJarTask(pubName: String): TaskProvider<Jar> {
        return tasks.register<Jar>("${pubName}JavadocJar") {
            archiveClassifier.set("javadoc")
            destinationDirectory.set(layout.buildDirectory.dir("javadoc-jars/$pubName"))

            tasks.findByName("dokkaGenerate")?.let { dokkaTask ->
                dependsOn(dokkaTask)
                from(layout.buildDirectory.dir("dokka/html"))
            }
        }
    }

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
                // JVM project: a single publication from the java component (the sources and
                // javadoc jars are already wired into it by jvm.gradle.kts).
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    this.artifactId = artifactId
                    configureCommonPom()
                }
            } else {
                // KMP project: the Kotlin plugin creates one publication per target, so the
                // javadoc jar and POM have to be attached to each of them.
                withType<MavenPublication>().configureEach {
                    val javadocJarTask = createJavadocJarTask(name)
                    artifact(javadocJarTask)
                    configureCommonPom()
                }
            }
        }

        repositories {
            maven {
                name = "localStaging"
                url = uri(layout.buildDirectory.dir("staging-repo"))
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
}
