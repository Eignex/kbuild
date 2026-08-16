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
