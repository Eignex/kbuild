import com.eignex.kbuild.EignexPublishExtension
import com.eignex.kbuild.MavenPublishExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

plugins {
    `maven-publish`
    signing
    id("io.github.sgtsilvio.gradle.maven-central-publishing")
}

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

val eignexPublish = extensions.create<EignexPublishExtension>("eignexPublish")
val mavenPublish = extensions.create<MavenPublishExtension>("mavenPublish")
eignexPublish.licenseName.convention("Apache-2.0")
eignexPublish.licenseUrl.convention("https://www.apache.org/licenses/LICENSE-2.0")
eignexPublish.projectUrl.convention(eignexPublish.githubRepo.map { "https://github.com/$it" })
eignexPublish.scmUrl.convention(eignexPublish.projectUrl)
eignexPublish.scmConnection.convention(
    eignexPublish.githubRepo.map { "scm:git:https://github.com/$it.git" },
)
eignexPublish.scmDeveloperConnection.convention(
    eignexPublish.githubRepo.map { "scm:git:ssh://git@github.com/$it.git" },
)
eignexPublish.developerId.convention("rasros")
eignexPublish.developerName.convention("Rasmus Ros")
eignexPublish.developerUrl.convention("https://github.com/rasros")

afterEvaluate {
    // Opt out for internal modules (benchmarks, samples): no publication, signing, or
    // projectUrl (or githubRepo, which supplies its default) is required for published metadata.
    if (!eignexPublish.publish.getOrElse(true)) return@afterEvaluate

    fun Property<String>.valueOr(eignexValue: Provider<String>): String? = orElse(eignexValue).orNull

    val missing = mutableListOf<String>()
    fun required(name: String, value: String?): String {
        if (value == null) missing += "mavenPublish.$name (or eignexPublish.$name)"
        return value.orEmpty()
    }

    val artifactId = mavenPublish.artifactId.valueOr(eignexPublish.artifactId) ?: project.name
    val pomDescription = required("description", mavenPublish.description.valueOr(eignexPublish.description))
    val configuredLicenses = when {
        mavenPublish.licenseEntries().isNotEmpty() -> mavenPublish.licenseEntries()
        eignexPublish.licenseEntries().isNotEmpty() -> eignexPublish.licenseEntries()
        else -> emptyList()
    }
    val licenses = if (configuredLicenses.isEmpty()) {
        listOf(
            Triple(
                required("licenseName", mavenPublish.licenseName.valueOr(eignexPublish.licenseName)),
                required("licenseUrl", mavenPublish.licenseUrl.valueOr(eignexPublish.licenseUrl)),
                null,
            ),
        )
    } else {
        configuredLicenses.mapIndexed { index, license ->
            Triple(
                required("licenses[$index].name", license.name.orNull),
                required("licenses[$index].url", license.url.orNull),
                license.distribution.orNull,
            )
        }
    }
    val projectUrl = required("projectUrl", mavenPublish.projectUrl.valueOr(eignexPublish.projectUrl))
    val scmUrl = required("scmUrl", mavenPublish.scmUrl.valueOr(eignexPublish.scmUrl))
    val scmConnection = required("scmConnection", mavenPublish.scmConnection.valueOr(eignexPublish.scmConnection))
    val scmDeveloperConnection = required(
        "scmDeveloperConnection",
        mavenPublish.scmDeveloperConnection.valueOr(eignexPublish.scmDeveloperConnection),
    )
    val developerId = required("developerId", mavenPublish.developerId.valueOr(eignexPublish.developerId))
    val developerName = required("developerName", mavenPublish.developerName.valueOr(eignexPublish.developerName))
    val developerUrl = required("developerUrl", mavenPublish.developerUrl.valueOr(eignexPublish.developerUrl))
    if (missing.isNotEmpty()) {
        throw GradleException(
            "Missing Maven publication metadata: ${missing.joinToString()}. " +
                "Configure mavenPublish { ... } or eignexPublish { ... }.",
        )
    }

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
            description.set(pomDescription)
            url.set(projectUrl)
            licenses {
                licenses.forEach { (name, url, distribution) ->
                    license {
                        this.name.set(name)
                        this.url.set(url)
                        distribution?.let { this.distribution.set(it) }
                    }
                }
            }
            scm {
                url.set(scmUrl)
                connection.set(scmConnection)
                developerConnection.set(scmDeveloperConnection)
            }
            developers {
                developer {
                    id.set(developerId)
                    name.set(developerName)
                    url.set(developerUrl)
                }
            }
        }
    }

    publishing {
        publications {
            if (components.findByName("java") != null) {
                // JVM: one publication from the java component, whose sources and javadoc
                // jars jvm.gradle.kts already wired in.
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    this.artifactId = artifactId
                    configureCommonPom()
                }
            } else {
                // KMP: the Kotlin plugin creates one publication per target, so the javadoc
                // jar and POM attach to each.
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
        // The snapshot repository runs no validation, so a signature buys nothing and doubles
        // the files uploaded per artifact.
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
