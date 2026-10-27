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
        if (requested.id.id in setOf("com.eignex.jvm", "com.eignex.kmp", "com.eignex.cli", "com.eignex.publish", "com.eignex.lint")) {
            useModule("com.eignex:kbuild:${requested.version ?: kbuildVersion}")
        }
    }
    plugins {
        id("com.eignex.jvm") version kbuildVersion
        id("com.eignex.kmp") version kbuildVersion
        id("com.eignex.cli") version kbuildVersion
        id("com.eignex.publish") version kbuildVersion
        id("com.eignex.lint") version kbuildVersion
    }
}
rootProject.name = "release-resolution"
include("library", "multiplatform", "cli", "publish", "lint")
