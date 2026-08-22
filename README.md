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

## Plugins

KBuild provides five convention plugins:

- `com.eignex.jvm`: Kotlin/JVM libraries with Dokka, sources and Javadoc jars, Kover, JUnit
  Platform, publishing, and lint.
- `com.eignex.kmp`: Kotlin Multiplatform libraries with Dokka, Kover, ABI validation, publishing,
  lint, JVM/JS/Wasm test conventions, and common source-set dependencies.
- `com.eignex.cli`: Kotlin Multiplatform CLI applications. It generates build identity metadata,
  configures JVM and Native executables, and provides release packaging. It does not publish.
- `com.eignex.publish`: Maven Central publishing, POM metadata, local staging, snapshot
  publishing, and conditional in-memory GPG signing.
- `com.eignex.lint`: Detekt with the built-in Eignex rules, KDoc checks, and Kotlin/Native-safe
  test-name checks.

Apply one plugin in a consumer module:

```kotlin
plugins {
    id("com.eignex.jvm") // or com.eignex.kmp / com.eignex.cli
}
```

The `jvm` and `kmp` plugins apply `publish` and `lint` automatically. The `cli` plugin applies
`lint` automatically. Consumer projects declare their own Kotlin targets, for example:

```kotlin
kotlin {
    jvm()
    js { browser(); nodejs() }
    wasmWasi { nodejs() }
    // linuxX64(); macosArm64(); mingwX64()
}
```

## Shared build configuration

The JVM, KMP, and CLI plugins expose `eignexBuild`. Its defaults preserve KBuild's Eignex setup,
but every value can be replaced or disabled:

| Property | Default | Purpose |
| --- | --- | --- |
| jvmToolchain | `25` | Kotlin JVM compilation and Detekt |
| usePlatformDependencies | `true` | Add the platform BOM |
| useKotlinTestDependency | `true` | Add `kotlin-test` to test source sets |
| useMavenCentral | `true` | Add Maven Central to repositories |
| platformGroup | `com.eignex` | Platform BOM group |
| defaultGroup | `com.eignex` | CLI build-info group fallback |
| platformArtifact | `kbuild-platform` | Platform BOM artifact |
| platformVersion | Current KBuild version | Platform BOM version |
| nodeVersion | `25.0.0` | KMP JavaScript and Wasm Node.js |
| jsTestTimeout | `120s` | KMP Mocha and Karma tests |
| koverEnabled | `true` | Kover tasks |
| abiValidationEnabled | `true` | KMP ABI validation |
| lintEnabled | `true` | Convention lint tasks |

```kotlin
eignexBuild {
    jvmToolchain.set(21)
    nodeVersion.set("20.0.0")
    jsTestTimeout.set("45s")

    platformGroup.set("org.example")
    platformArtifact.set("example-bom")
    platformVersion.set("1.2.3")
    usePlatformDependencies.set(false)
    useKotlinTestDependency.set(false)
    useMavenCentral.set(false)

    abiValidationEnabled.set(false)
    lintEnabled.set(false)
    koverEnabled.set(false)
}
```

By default, KBuild adds the `com.eignex:kbuild-platform` BOM to `commonMain`/`commonTest` or
JVM compile/test classpaths, adds `kotlin-test` to common tests, adds Maven Central, pins the JVM
toolchain to 25, pins Node.js to 25.0.0, enables ABI validation, Kover, and lint, and uses a 120s
JS test timeout.

## CLI applications

Configure the JVM main class and Native entry point with `eignexCli`:

| Property | Default |
| --- | --- |
| mainClass | No convention; set the JVM executable main class |
| entryPoint | Kotlin/Native executable default |
| version | Project version |
| appName | Project name |
| appId | Project group, or `eignexBuild.defaultGroup` when blank |
| buildInfoPackage | Package of `mainClass`, or the project-group fallback |
| releaseAssetsEnabled | `true` |
| releaseAssetPrefix | Project name |
| releaseAssetsDirectory | `build/release-assets` |
| stripReleaseBinaries | `true` |

```kotlin
eignexCli {
    mainClass = "com.example.MainKt"
    entryPoint = "com.example.main"
    appName = "Example Tool"
    appId = "com.example.tool"
    version = "1.2.3"
    buildInfoPackage = "com.example.tool"
}
```

The plugin generates an internal `BuildInfo` object containing `NAME`, `ID`, `VERSION`, and
`versionLine()`. Unset identity fields derive from the project name, group, version, and main-class
package.

The `releaseAssets` task collects host-buildable Native release executables and the available JVM
distribution ZIP, optionally strips Native binaries on a best-effort basis, and writes
`SHA256SUMS`. Asset packaging is configurable:

```kotlin
eignexCli {
    releaseAssetsEnabled = true
    releaseAssetPrefix = "example"
    releaseAssetsDirectory = "build/release-assets"
    stripReleaseBinaries = true
}
```

Assets use `<prefix>-<version>-<os>-<arch>` for Native binaries and
`<prefix>-<version>-jvm.zip` for the JVM distribution. Only targets linkable on the current host
are included.

## JVM libraries

`com.eignex.jvm` enables `withSourcesJar()` and `withJavadocJar()`, builds the Javadoc jar from
Dokka, uses JUnit Platform for `Test` tasks, and adds Kotlin test support. Publishing and linting
are inherited from the publish and lint sections below.

## Multiplatform libraries

`com.eignex.kmp` enables Kotlin ABI validation, applies the shared platform/test dependencies,
pins Node.js for JS and Wasm targets, and applies the configured JS/Karma test timeout. Existing
`karma.config.d/*.js` files are copied into the generated configuration directory.

## Lint

`com.eignex.lint` enables the Detekt KtLint wrapper and the built-in Eignex rules. It generates a
configuration under `build/tmp/eignex-detekt.yml`, enables HTML reports, disables SARIF by default,
and autocorrects by default. It also runs KDoc validation and rejects Kotlin/Native-incompatible
backtick test names containing `(`, `)`, or `#`.

| Property | Default |
| --- | --- |
| enabled | `true` |
| useEignexConfig | `true` |
| autoCorrect | `true` |
| htmlReport | `true` |
| sarifReport | `false` |

Use `eignexLint` to change the lint policy:

```kotlin
eignexLint {
    enabled.set(true)
    useEignexConfig.set(false)
    autoCorrect.set(false)
    htmlReport.set(false)
    sarifReport.set(true)
}
```

`writeEignexDetektConfig` materializes the configuration used by Detekt. `check` runs Detekt,
`checkNativeSafeTestNames`, and `checkKdoc`. `checkKdoc` validates supported KDoc block tags and
project-local package links. `lintDocs` is a separate, slower task that also runs `dokkaGenerate`
when Dokka is applied and fails on unresolved links, unknown tags, or Dokka warnings/errors.
On KMP, incompatible JVM-shaped Detekt tasks are disabled in favor of source-set tasks.

## Publishing

Eignex modules configure `eignexPublish`. Existing modules get Apache-2.0, Eignex developer, and
GitHub-derived SCM defaults. `publish.set(false)` disables publication and signing for internal
modules.

Non-Eignex consumers should configure `mavenPublish`; it has no generic metadata defaults and
fails with a list of missing fields when publication is configured:

```kotlin
mavenPublish {
    artifactId.set("example-core")
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

The POM supports one or more Maven licenses. Configured entries replace the default Apache entry:

```kotlin
eignexPublish {
    licenses {
        license {
            name.set("Apache-2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
        }
        license {
            name.set("BSD-3-Clause")
            url.set("https://opensource.org/license/bsd-3-clause")
        }
    }
}
```

For compatibility, `licenseName` and `licenseUrl` remain available for a single license. POM
metadata is applied to every JVM and KMP Maven publication.

The `eignexPublish` defaults are:

| Property | Default |
| --- | --- |
| githubRepo | None; `Owner/repo`, used for GitHub URL and SCM defaults |
| publish | `true` |
| artifactId | Project name |
| licenseName | `Apache-2.0` |
| licenseUrl | `https://www.apache.org/licenses/LICENSE-2.0` |
| projectUrl | `https://github.com/<githubRepo>` |
| scmUrl | `projectUrl` |
| scmConnection | `scm:git:https://github.com/<githubRepo>.git` |
| scmDeveloperConnection | `scm:git:ssh://git@github.com/<githubRepo>.git` |
| developerId | `rasros` |
| developerName | `Rasmus Ros` |
| developerUrl | `https://github.com/rasros` |

Set `publish` to `false` for modules that use the conventions but should not create a Maven
publication or validate publication metadata.

### Generic Maven publishing

Non-Eignex consumers should configure the `mavenPublish` extension. It has no generic metadata
defaults; the build fails and lists every missing required value when publication is configured.
A `mavenPublish` value overrides the corresponding `eignexPublish` value when both extensions are
present. The artifact ID falls back to the project name.

```kotlin
mavenPublish {
    artifactId.set("example-core")
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

Required generic metadata is description, projectUrl, scmUrl, scmConnection,
scmDeveloperConnection, developerId, developerName, and developerUrl. Provide either
`licenseName`/`licenseUrl`, or one or more `licenses { license { ... } }` entries. Each license
entry supports name, url, and optional distribution. Configured license entries replace the
Eignex Apache default and support multiple licenses.

For your own projects, fork and publish your own version.
