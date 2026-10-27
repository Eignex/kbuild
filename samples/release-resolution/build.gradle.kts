allprojects {
    repositories {
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
