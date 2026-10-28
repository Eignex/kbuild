pluginManagement {
    val kbuildVersion = providers.gradleProperty("kbuildVersion").get()
    repositories {
        ivy {
            url = uri(providers.gradleProperty("releaseRepository").get())
            isAllowInsecureProtocol = url.scheme == "http" // Local anonymous HTTP validation only.
            patternLayout { artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]") }
            metadataSources { gradleMetadata() }
            content { includeModule("com.eignex", "kbuild") }
        }
        // A broken candidate must fail instead of silently resolving an old Central copy.
        gradlePluginPortal { content { excludeModule("com.eignex", "kbuild") } }
        mavenCentral { content { excludeModule("com.eignex", "kbuild") } }
    }
    resolutionStrategy.eachPlugin {
        if (requested.id.id in setOf(
            "com.eignex.jvm", "com.eignex.kmp", "com.eignex.cli",
            "com.eignex.publish", "com.eignex.lint", "com.eignex.publish.github",
            "org.jetbrains.kotlin.plugin.serialization",
        )) {
            useModule("com.eignex:kbuild:$kbuildVersion")
        }
    }
}
rootProject.name = "release-resolution"
include("library", "multiplatform", "cli", "publish", "lint")
