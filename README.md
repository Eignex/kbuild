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

Gradle conventions for Kotlin libraries and CLI applications. Requires JDK 21+ to run Gradle;
consumer compilation and tests default to JDK 17.

| Plugin | Provides |
| --- | --- |
| `com.eignex.jvm` | Kotlin/JVM, Dokka, sources/Javadoc, JUnit Platform, Kover, publishing, lint |
| `com.eignex.kmp` | Kotlin Multiplatform, ABI validation, Dokka, Kover, JS/Wasm tests, publishing, lint |
| `com.eignex.cli` | JVM/Native executables, generated build identity, release packaging, lint |
| `com.eignex.publish` | Maven Central publishing, POM metadata, staging, snapshots, signing |
| `com.eignex.publish.github` | GitHub Release assets, metadata, checksums, explicit upload |
| `com.eignex.lint` | Detekt/KtLint, Eignex rules, KDoc checks, Native-safe test names |

## Installing from GitHub Releases

**From v1.3.6, KBuild and its platform are distributed through GitHub Releases.** No account or
token is needed. Earlier Maven Central versions remain available with their existing setup.

In `settings.gradle.kts`:

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
        )) {
            useModule("com.eignex:kbuild:${requested.version ?: kbuildVersion}")
        }
    }
    plugins {
        id("com.eignex.jvm") version kbuildVersion
        id("com.eignex.kmp") version kbuildVersion
        id("com.eignex.cli") version kbuildVersion
        id("com.eignex.publish") version kbuildVersion
        id("com.eignex.lint") version kbuildVersion
        id("com.eignex.publish.github") version kbuildVersion
    }
}
```

Apply the plugin in a module's `build.gradle.kts`:

```kotlin
plugins { id("com.eignex.jvm") }
```

For KMP or CLI projects, use the corresponding plugin and declare targets in `kotlin { }`.
JVM and KMP libraries automatically depend on the matching `com.eignex:kbuild-platform`.
Plugin repositories do not resolve this dependency; add its repository in the root build:

```kotlin
allprojects {
    repositories {
        ivy {
            url = uri("https://github.com/Eignex/kbuild/releases/download")
            patternLayout { artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]") }
            metadataSources { gradleMetadata() }
            content { includeModule("com.eignex", "kbuild-platform") }
        }
        mavenCentral {
            metadataSources { gradleMetadata(); mavenPom() }
        }
    }
}
```

Keep `gradleMetadata()` to preserve variants and platform constraints. Consumers of published
libraries retaining the KBuild platform also need this repository, even if the library comes from
Maven Central. Configure their Maven repositories with the metadata sources shown above so Gradle
reads module metadata before POM platform imports.

This Ivy layout is for Gradle. For Maven consumers, set `eignexBuild.usePlatformDependencies` to
`false` and manage dependency versions with a Maven-compatible platform.

## Build configuration

`eignexBuild` configures the JVM, KMP, and CLI conventions:

| Property | Default |
| --- | --- |
| `jvmToolchain` | `17` |
| `jvmTarget` | `jvmToolchain` |
| `usePlatformDependencies`, `useKotlinTestDependency`, `useMavenCentral` | `true` |
| `platformGroup`, `platformArtifact`, `platformVersion` | `com.eignex`, `kbuild-platform`, KBuild version |
| `nodeVersion`, `jsTestTimeout` | `26.12.1`, `120s` |
| `koverEnabled`, `abiValidationEnabled`, `lintEnabled` | `true` |

```kotlin
eignexBuild {
    jvmToolchain.set(25)
    jvmTarget.set(17)
    usePlatformDependencies.set(false)
    lintEnabled.set(false)
}
```

The toolchain controls compilation, ordinary tests, and Detekt; `jvmTarget` controls the published
baseline. A lower Kotlin bytecode target does not restrict referenced JDK APIs. Explicit Kotlin
compilation overrides take precedence. See [all build options](src/main/kotlin/com/eignex/kbuild/EignexBuildExtension.kt).

### Multi-release and modular JARs

```kotlin
eignexBuild {
    jvmToolchain.set(25)
    jvmTarget.set(17)
    multiReleaseJvmVersion.set(25)
    packagedTestJdks.set(listOf(17, 25))
    // Optional library-owned Java module descriptor:
    jvmModuleDescriptor.set(layout.projectDirectory.file("src/jvmModule/module-info.java"))
}
```

Put newer code in `src/java25/{kotlin,java}`, or `src/jvmJava25/{kotlin,java}` for a KMP target
named `jvm`. It can access the main compilation's internal declarations. Classes and resources
are packaged under `META-INF/versions/25/` in the same JAR; sources go under `java25/` in the
sources JAR. Keep public APIs consistent and versioned implementation packages unexported.

Module descriptors default to `jvmModuleRelease = maxOf(jvmTarget, 9)` and are versioned when
the baseline is below 9. Override `jvmModuleRelease` and `jvmModuleVersioned` as needed; a root
descriptor cannot require a newer JDK than the baseline.

`packagedTestJdks` adds `testJarOnJava17` / `testJarOnJava25` (`jvmTestJarOnJava17` / `jvmTestJarOnJava25` for KMP) to
`check`. These use the packaged JAR and the ordinary test framework. Configure custom JVM flags,
system properties, and filters on these tasks too. Install the JDKs or enable toolchain provisioning.
See the [multi-release sample](samples/jvm-multi-release) and [independent consumer](samples/multi-release-consumer).

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

The generated internal `BuildInfo` exposes `NAME`, `ID`, `VERSION`, and `versionLine()`. Defaults
come from the project name, group, version, and main-class package.

`releaseAssets` stages Native executables for the current host, the JVM distribution ZIP, and
`SHA256SUMS` under `build/release-assets`. Names are `<prefix>-<version>-<os>-<arch>` and
`<prefix>-<version>-jvm.zip`. Native binaries are stripped when possible. Configure packaging and
identity through [eignexCli](src/main/kotlin/com/eignex/kbuild/EignexCliExtension.kt).

## Lint

`check` runs Detekt, `checkKdoc`, and `checkNativeSafeTestNames`. Detekt uses KtLint and Eignex
rules, autocorrects, and writes HTML reports by default. KDoc checks validate tags and local
package links; Native test names cannot contain `(`, `)`, or `#`.

```kotlin
eignexLint {
    autoCorrect.set(false)
    sarifReport.set(true)
}
```

`writeEignexDetektConfig` generates `build/tmp/eignex-detekt.yml`. Run `lintDocs` for the slower
Dokka link/warning checks. See [lint options](src/main/kotlin/com/eignex/kbuild/EignexLintExtension.kt).

## Publishing

JVM and KMP conventions apply `com.eignex.publish`. Eignex projects can use GitHub-derived
metadata with Apache-2.0 and Eignex developer defaults:

```kotlin
eignexPublish {
    githubRepo.set("Owner/library")
    description.set("My Kotlin library")
    // publish.set(false) for internal modules; skips publication and metadata validation.
}
```

For other projects, configure `mavenPublish`. Values override `eignexPublish`; missing required
metadata fails configuration. Artifact IDs default to the project name.

```kotlin
mavenPublish {
    artifactId.set("example-core")
    description.set("A useful library")
    projectUrl.set("https://example.com/library")
    licenses {
        license {
            name.set("MIT")
            url.set("https://opensource.org/license/mit")
            distribution.set("repo") // Optional.
        }
    }
    scmUrl.set("https://example.com/library/source")
    scmConnection.set("scm:git:https://example.com/library.git")
    scmDeveloperConnection.set("scm:git:ssh://git@example.com/library.git")
    developerId.set("example")
    developerName.set("Example Org")
    developerUrl.set("https://example.com")
}
```

Multiple license entries are supported; they replace the default license. `licenseName` and
`licenseUrl` remain available for a single license. See [Maven metadata options](src/main/kotlin/com/eignex/kbuild/MavenPublishExtension.kt).
Central publishing supports local staging, snapshots, and conditional in-memory signing. Bundles
retain payloads, sources, documentation, metadata, signatures, and required MD5/SHA-1 checksums;
optional SHA-256/SHA-512 sidecars and signature checksums are omitted.

### GitHub Releases

Apply `com.eignex.publish.github` alongside the JVM/KMP conventions above. Its repository defaults
to `eignexPublish.githubRepo`; existing Central publishing tasks remain available.

```shell
./gradlew :core:githubReleaseAssets -PciVersion=1.2.3
# Create and push v1.2.3, then upload with authenticated gh:
./gradlew :core:publishToGitHubRelease -PciVersion=1.2.3
```

`githubReleaseAssets` stages publications, sources, documentation, POM/module metadata, and
`SHA256SUMS` under `build/github-release-assets` without credentials. `publishToGitHubRelease`
uses `gh release create --verify-tag`; authenticate `gh` or provide `GH_TOKEN` with `contents: write`.
Use the exact project task path (omit `:core` for a single-project build). Upload is explicit,
rejects snapshots, and does not replace existing releases.

To select publications or combine modules:

```kotlin
githubPublish {
    repository.set("Owner/library")
    publicationNames.set(listOf("mavenJava"))
    includeProjects.set(listOf(":platform"))
}
```

By default, all Maven publications except plugin markers are included. Aggregated projects must
apply the helper, share a version, and produce distinct filenames. KMP target redirects are
flattened; missing targets and filename collisions fail staging.

For existing Maven publications, apply the helper alone and set `githubPublish.repository`.
It applies standard `maven-publish` without Central or Eignex metadata conventions. Other
[helper options](build-logic/src/main/kotlin/com/eignex/kbuild/GitHubPublishPlugin.kt) include
`releaseVersion`, `releaseTag` (default `v<version>`), `assetsDirectory`, and release notes/prerelease flags.

Consumers use the installation section's Ivy layout with their library's release URL and module
filter, plus `implementation("org.example:core:1.2.3")`. Adjust `v[revision]` for custom tags;
retain the KBuild platform repository if the library depends on it.

## Building KBuild

```shell
./gradlew build
./samples/smoke-test.sh
```

Foojay provisions build toolchains. Sample smoke tests need JDK 25 in `JAVA_HOME` and an Android
SDK (build-tools 37.0.0, platform android-36) in `ANDROID_HOME` or `ANDROID_SDK_ROOT`. They cover
JVM, KMP, CLI, modular/multi-release JARs, and Android D8/R8. Deprecations and Dokka warnings fail validation.

To stage and verify a release locally, set JDK 25 in `JAVA_HOME` and JDK 17 in `JAVA_HOME_17_X64`:

```shell
./gradlew releaseAssets -PciVersion=1.3.6
bash .github/scripts/check-publication-files.sh build/release-assets --version 1.3.6
bash .github/scripts/check-release-resolution.sh --version 1.3.6
```

Checks require Bash 4.2+, `jq`, GNU coreutils, `tar`, and `unzip`; they use the pinned wrapper and
isolated Gradle homes. Add `--repository-url https://github.com/Eignex/kbuild/releases/download`
to verify a published release, or `--gradle /path/to/gradle` to probe another Gradle distribution.

KBuild uses the same GitHub helper through the independent `build-logic` included build. Tags
`v<version>` run validation, publish KBuild/platform assets and checksums, then verify public
anonymous resolution. Manual `dryRun=true` runs validate without publishing.
