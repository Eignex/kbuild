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

[![Maven Central](https://img.shields.io/maven-central/v/com.eignex/kbuild.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.eignex/kbuild)
[![Build](https://github.com/eignex/kbuild/actions/workflows/build.yml/badge.svg)](https://github.com/eignex/kbuild/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/eignex/kbuild)](https://github.com/eignex/kbuild/blob/main/LICENSE)

> This repository is intended for internal use, but feel free to use however you want.

---

## Overview

Five convention plugins:

1. `jvm`: Kotlin/JVM libraries. Dokka, Kover, and testing defaults.
2. `kmp`: Kotlin Multiplatform libraries. Common testing and toolchains.
3. `cli`: Kotlin Multiplatform CLI applications. JVM and native executables on consumer-declared
   targets, no publishing. `releaseAssets` collects stripped native binaries
   (`<name>-<version>-<os>-<arch>`), the JVM dist zip, and `SHA256SUMS` into `build/release-assets/`.
4. `publish`: Maven Central publishing, including POM generation and GPG signing.
5. `lint`: Shared Detekt configuration with Eignex style suppressions.

The jvm and kmp plugins both apply publish and lint, so in practice you apply one of them for a
library, or cli for an application. The cli plugin needs targets declared by the consumer plus an
`eignexCli { mainClass = "..."; entryPoint = "..." }` block.

For your own projects, fork and publish your own version.

### Generic Maven publishing

Non-Eignex consumers should configure the `mavenPublish` extension. It has no metadata defaults;
the build fails and lists any missing values when publication is configured.

```kotlin
mavenPublish {
    description.set("A useful library")
    projectUrl.set("https://example.com/library")
    licenses {
        license {
            name.set("MIT")
            url.set("https://opensource.org/license/mit")
            distribution.set("repo")
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

Eignex modules retain the Apache-2.0 default. Configuring `licenses` replaces that default and
supports multiple entries; each entry may set `name`, `url`, and `distribution`.

### Convention overrides

The JVM, KMP, and CLI plugins expose shared defaults through `eignexBuild`. Existing Eignex
defaults remain active, but consumers can replace them or disable optional behavior:

```kotlin
eignexBuild {
    jvmToolchain.set(21)
    nodeVersion.set("20.0.0")
    jsTestTimeout.set("45s")
    usePlatformDependencies.set(false)
    lintEnabled.set(false)
    koverEnabled.set(false)
}
```

`eignexLint` can disable the built-in rules or adjust reporting. CLI projects can configure
`releaseAssetPrefix`, `releaseAssetsDirectory`, `stripReleaseBinaries`, and
`releaseAssetsEnabled` on `eignexCli`.
