rootProject.name = "release-downstream"
val pomOnly = providers.gradleProperty("pomOnly").map(String::toBoolean).getOrElse(false)
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri(providers.gradleProperty("downstreamRepository").get())
            if (pomOnly) metadataSources { mavenPom(); ignoreGradleMetadataRedirection() }
            content {
                includeGroup("org.example")
                if (!pomOnly) excludeModule("org.example", "library")
            }
        }
        if (!pomOnly) ivy {
            url = uri(providers.gradleProperty("libraryReleaseRepository").get())
            isAllowInsecureProtocol = url.scheme == "http"
            patternLayout { artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]") }
            metadataSources { gradleMetadata() }
            content { includeModule("org.example", "library") }
        }
        // Never let Central's older KBuild versions hide a leaked platform dependency.
        mavenCentral { content { excludeModule("com.eignex", "kbuild-platform") } }
    }
}
