# KBuild

Gradle conventions for Kotlin libraries and CLI applications, with shared testing,
lint, documentation, and publishing defaults. Requires JDK 21+ to run Gradle;
JVM compilation and tests default to JDK 17.

## Installation

Add the [release repository](https://github.com/Eignex/kbuild/releases) to `settings.gradle.kts`:

```kotlin
pluginManagement {
    val kbuildVersion = "1.3.6"
    repositories {
        ivy {
            url = uri("https://github.com/Eignex/kbuild/releases/download")
            patternLayout { artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]") }
            metadataSources { gradleMetadata() }
            content { includeModule("com.eignex", "kbuild") }
        }
        gradlePluginPortal()
        mavenCentral()
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
```

JVM and KMP projects also need the build platform repository in the root
`build.gradle.kts`:

```kotlin
allprojects {
    repositories {
        ivy {
            url = uri("https://github.com/Eignex/kbuild/releases/download")
            patternLayout { artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]") }
            metadataSources { gradleMetadata() }
            content { includeModule("com.eignex", "kbuild-platform") }
        }
        mavenCentral()
    }
}
```

## Usage

In a library's `build.gradle.kts`:

```kotlin
plugins { id("com.eignex.jvm") }

eignexPublish {
    githubRepo.set("Owner/library")
    description.set("My Kotlin library")
}
```

Use `com.eignex.kmp` for multiplatform libraries and declare targets in `kotlin { }`.
Both library conventions include Dokka, Kover, lint, and Maven Central publishing;
KMP also enables ABI validation. Apply `kotlin("plugin.serialization")` without a
version to use the bundled serialization plugin.

For CLI applications, apply `com.eignex.cli` and configure
[eignexCli](src/main/kotlin/com/eignex/kbuild/EignexCliExtension.kt).
Run `releaseAssets` to stage executables, a JVM distribution ZIP, and checksums.

`check` runs tests and lint; `lintDocs` adds documentation checks. Configure
[build](src/main/kotlin/com/eignex/kbuild/EignexBuildExtension.kt) and
[lint](src/main/kotlin/com/eignex/kbuild/EignexLintExtension.kt) defaults through
`eignexBuild` and `eignexLint`.

Publishing uses Apache-2.0 and Eignex developer defaults. Use
[mavenPublish](src/main/kotlin/com/eignex/kbuild/MavenPublishExtension.kt) for custom
metadata or `eignexPublish { publish.set(false) }` for internal modules. For GitHub
Releases, apply [com.eignex.publish.github](build-logic/src/main/kotlin/com/eignex/kbuild/GitHubPublishPlugin.kt).
