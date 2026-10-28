<p align="center">
  <a href="https://eignex.com/">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/Eignex/.github/refs/heads/main/profile/banner-white.svg">
      <source media="(prefers-color-scheme: light)" srcset="https://raw.githubusercontent.com/Eignex/.github/refs/heads/main/profile/banner.svg">
      <img alt="Eignex" src="https://raw.githubusercontent.com/Eignex/.github/refs/heads/main/profile/banner.svg" style="max-width: 100%; width: 22em;">
    </picture>
  </a>
</p>

# KBuild

[![GitHub Release](https://img.shields.io/github/v/release/Eignex/kbuild)](https://github.com/Eignex/kbuild/releases)
[![Build](https://github.com/eignex/kbuild/actions/workflows/build.yml/badge.svg)](https://github.com/eignex/kbuild/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/eignex/kbuild)](https://github.com/eignex/kbuild/blob/main/LICENSE)

Gradle conventions for Kotlin libraries and CLI applications. Sets up testing,
lint, documentation, and publishing with shared Eignex defaults. Requires JDK
21+ to run Gradle; JVM compilation and tests default to JDK 17.

| Plugin | Use for |
| --- | --- |
| `com.eignex.jvm` | Kotlin/JVM libraries |
| `com.eignex.kmp` | Kotlin Multiplatform libraries |
| `com.eignex.cli` | JVM and Native CLI applications |
| `com.eignex.publish` | Maven Central publishing |
| `com.eignex.publish.github` | GitHub Release publishing |
| `com.eignex.lint` | Detekt, KtLint, and KDoc checks |

## Installation

KBuild is distributed through GitHub Releases. Add this to `settings.gradle.kts`:

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

Use `com.eignex.kmp` for multiplatform libraries and declare targets in
`kotlin { }`. Both conventions include Dokka, Kover, lint, and Maven Central
publishing. KMP also enables ABI validation. Apply `kotlin("plugin.serialization")`
without a version to use KBuild's bundled serialization plugin.

Override defaults through `eignexBuild`:

```kotlin
eignexBuild {
    jvmToolchain.set(25)
    jvmTarget.set(17)
    lintEnabled.set(false)
}
```

`check` runs tests and lint; `lintDocs` adds Dokka link and warning checks.
See [build options](src/main/kotlin/com/eignex/kbuild/EignexBuildExtension.kt),
[lint options](src/main/kotlin/com/eignex/kbuild/EignexLintExtension.kt), and the
[multi-release JAR sample](samples/jvm-multi-release) for more configuration.

## CLI applications

```kotlin
plugins { id("com.eignex.cli") }

kotlin { jvm(); linuxX64(); macosArm64() }

eignexCli {
    mainClass = "com.example.MainKt"
    entryPoint = "com.example.main"
    appName = "Example Tool"
    appId = "com.example.tool"
}
```

KBuild generates an internal `BuildInfo` with the application's name, ID, and
version. Run `releaseAssets` to stage host Native executables, a JVM distribution
ZIP, and checksums in `build/release-assets`. See [CLI options](src/main/kotlin/com/eignex/kbuild/EignexCliExtension.kt).

## Publishing

`eignexPublish` uses GitHub metadata with Apache-2.0 and Eignex developer defaults.
Use [mavenPublish](src/main/kotlin/com/eignex/kbuild/MavenPublishExtension.kt) for
custom metadata, or `eignexPublish { publish.set(false) }` for internal modules.

For GitHub Releases, also apply `com.eignex.publish.github`:

```shell
./gradlew githubReleaseAssets -PciVersion=1.2.3
# After creating and pushing tag v1.2.3, upload with authenticated gh:
./gradlew publishToGitHubRelease -PciVersion=1.2.3
```

For a subproject, prefix the tasks with its path (for example, `:core:`).
See [GitHub publishing options](build-logic/src/main/kotlin/com/eignex/kbuild/GitHubPublishPlugin.kt)
for selecting publications and combining modules.
