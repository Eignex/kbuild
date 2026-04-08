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

[![Build](https://github.com/eignex/kbuild/actions/workflows/build.yml/badge.svg)](https://github.com/eignex/kbuild/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/eignex/kencode)](https://github.com/eignex/kencode/blob/main/LICENSE)

> This repository is intended for internal use, but feel free to use however you want.

---

## Overview

kbuild provides four main convention plugins:

1. `jvm`: For pure Kotlin/JVM libraries. Includes Dokka, Kover, and testing defaults.
2. `kmp`: For Kotlin Multiplatform projects. Sets up common testing and toolchains.
3. `publish`: Standardized Maven Central publishing logic, including automated POM generation and GPG signing.
4. `lint`: Shared Detekt configuration with Eignex-specific style suppressions.

Both publishing and linting plugins are loaded by either jvm and kmp plugins. So in practice you apply either jvm or
kmp.

To configure for your own projects you will need to fork and publish your own version.
