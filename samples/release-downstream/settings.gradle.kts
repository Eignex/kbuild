rootProject.name = "release-downstream"
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri(providers.gradleProperty("downstreamRepository").get())
            // Avoid parsing a Maven POM's BOM imports before following its metadata marker.
            metadataSources { gradleMetadata(); mavenPom() }
            content { includeGroup("org.example") }
        }
        ivy {
            url = uri(providers.gradleProperty("releaseRepository").get())
            isAllowInsecureProtocol = url.scheme == "http"
            patternLayout { artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]") }
            metadataSources { gradleMetadata() }
            content { includeModule("com.eignex", "kbuild-platform") }
        }
        mavenCentral { content { excludeModule("com.eignex", "kbuild-platform") } }
    }
}
