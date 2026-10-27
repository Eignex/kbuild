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

## Plugins

KBuild provides six convention plugins:

- `com.eignex.jvm`: Kotlin/JVM libraries with Dokka, sources and Javadoc jars, Kover, JUnit
  Platform, publishing, and lint.
- `com.eignex.kmp`: Kotlin Multiplatform libraries with Dokka, Kover, ABI validation, publishing,
  lint, JVM/JS/Wasm test conventions, and common source-set dependencies.
- `com.eignex.cli`: Kotlin Multiplatform CLI applications. It generates build identity metadata,
  configures JVM and Native executables, and provides release packaging. It does not publish.
- `com.eignex.publish`: Maven Central publishing, POM metadata, local staging, snapshot
  publishing, and conditional in-memory GPG signing.
- `com.eignex.publish.github`: reusable GitHub Release assets, metadata, checksums, and explicit
  uploads through GitHub CLI. It can package JVM libraries, platforms, and Gradle plugins.
- `com.eignex.lint`: Detekt with the built-in Eignex rules, KDoc checks, and Kotlin/Native-safe
  test-name checks.

## Installing from GitHub Releases

Future KBuild versions are distributed as public [GitHub Release assets](https://github.com/Eignex/kbuild/releases).
Downloads require no GitHub login, token, or GitHub Packages configuration. Use a pinned version
whose release assets have been uploaded and verified; the examples below use `1.3.7` as the
migration example, not as a claim that this version is already available. Existing Maven Central
versions remain usable with their existing repository configuration.

In `settings.gradle.kts`, configure plugin resolution before applying KBuild:

```kotlin
pluginManagement {
    val kbuildVersion = "1.3.7"
    repositories {
        ivy {
            url = uri("https://github.com/Eignex/kbuild/releases/download")
            patternLayout {
                artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]")
            }
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

Then apply a plugin in a module without repeating the version:

```kotlin
plugins {
    id("com.eignex.jvm") // or com.eignex.kmp / com.eignex.cli
}
```

Plugin repositories do not resolve ordinary dependencies. Add the platform repository to each
consuming project's `repositories`, or use this in the root `build.gradle.kts` for all modules:

```kotlin
allprojects {
    repositories {
        ivy {
            url = uri("https://github.com/Eignex/kbuild/releases/download")
            patternLayout {
                artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]")
            }
            metadataSources { gradleMetadata() }
            content { includeModule("com.eignex", "kbuild-platform") }
        }
        mavenCentral {
            metadataSources { gradleMetadata(); mavenPom() }
        }
    }
}
```

Both repositories use Gradle Module Metadata to preserve variants, transitive plugin dependencies,
and platform constraints. Do not replace `gradleMetadata()` with artifact-only resolution.
Content filters keep unrelated coordinates off GitHub. Only the existing six plugin IDs map
to `com.eignex:kbuild`; separate plugin marker assets are unnecessary.

The JVM and KMP conventions add the matching `com.eignex:kbuild-platform` version automatically.
Without the conventions, use the same dependency repository and import it explicitly:

```kotlin
dependencies {
    implementation(platform("com.eignex:kbuild-platform:1.3.7"))
}
```

Published downstream libraries retain their platform dependency. Gradle users of those libraries
must add the platform repository above, even when the library itself comes from Maven Central;
no credentials are needed. Configure that library's Maven repository (including Maven Central)
with `metadataSources { gradleMetadata(); mavenPom() }` as shown above. Direct metadata lookup
avoids parsing the library POM's platform import before Gradle follows its metadata marker;
a Maven POM import cannot resolve from an Ivy repository. Apply the same setting to a custom
Maven repository hosting downstream libraries. Repository declarations are not inherited from
a library publication.

This distribution uses Gradle's Ivy layout and metadata; Maven and other tools cannot resolve
this platform directly. Libraries targeting those consumers should disable
`eignexBuild.usePlatformDependencies` and manage their dependency versions through a
Maven-compatible platform. KBuild's downstream Central publishing conventions remain available.

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
| jvmToolchain | `17` | JDK for compilation, tests, and Detekt |
| jvmTarget | `jvmToolchain` | Default bytecode baseline and published JVM version |
| multiReleaseJvmVersion | Unset | Opt-in newer JVM implementation in the same JAR |
| packagedTestJdks | `[]` | JDKs for tests against the packaged JVM JAR |
| usePlatformDependencies | `true` | Add the platform BOM |
| useKotlinTestDependency | `true` | Add `kotlin-test` to test source sets |
| useMavenCentral | `true` | Add Maven Central to repositories |
| platformGroup | `com.eignex` | Platform BOM group |
| defaultGroup | `com.eignex` | CLI build-info group fallback |
| platformArtifact | `kbuild-platform` | Platform BOM artifact |
| platformVersion | Current KBuild version | Platform BOM version |
| nodeVersion | `26.12.1` | KMP JavaScript and Wasm Node.js |
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
toolchain to 17, pins Node.js to 26.12.1, enables ABI validation, Kover, and lint, and uses a 120s
JS test timeout. The default bytecode baseline is also Java 17. The Gradle daemon loading
KBuild requires JDK 21 or newer; the consumer compilation and test toolchains default to 17.

## JVM baselines and multi-release JARs

The build JDK and published runtime baseline can be configured independently:

```kotlin
eignexBuild {
    jvmToolchain.set(25)
    jvmTarget.set(17)
    multiReleaseJvmVersion.set(25)
    packagedTestJdks.set(listOf(17, 25))
}
```

This works with `com.eignex.jvm`, `com.eignex.kmp`, and JVM targets in `com.eignex.cli`.
Kotlin compilation targets use conventions, so explicit target or per-compilation overrides
still win. Java uses `--release`, and published JVM attributes describe the baseline.
Detekt and ordinary tests continue to use the build JDK. A lower Kotlin bytecode target alone
does not restrict the JDK APIs referenced by Kotlin source; audit portable code's API usage
(and its dependencies), especially for Android.

Setting `multiReleaseJvmVersion` adds a `java25` compilation for each JVM target. Put only the
newer implementation in `src/java25/kotlin` and `src/java25/java` for the JVM plugin, or
`src/jvmJava25/kotlin` and `src/jvmJava25/java` for a KMP target named `jvm`. Substitute the
configured version and target name as needed. The compilation can use the main output and its
internal declarations without recompiling the shared sources. Its classes and resources go under
`META-INF/versions/25/`, with `Multi-Release: true` in the manifest. Versioned `META-INF` content,
including Kotlin module metadata, is excluded. Keep public APIs consistent across versions.

There is still one artifact per JVM target; no extra target, backend publication, AAR, or native
library is added. Sources from the newer compilation are included under `java25/` in the ordinary
sources JAR. Dokka, ABI validation, lint, and ordinary coverage tasks retain their normal roles.

For a modular JAR, provide the library's own descriptor:

```kotlin
eignexBuild {
    jvmModuleDescriptor.set(layout.projectDirectory.file("src/jvmModule/module-info.java"))
    // jvmModuleRelease defaults to maxOf(jvmTarget, 9).
    // jvmModuleVersioned defaults to true when jvmTarget is below 9.
}
```

The descriptor chooses its module name, requirements, exports, and opened packages. KBuild
compiles it against the target's Kotlin/Java outputs and module dependencies, then records all
packages, including those introduced only by newer implementations. Ordinary Gradle JAR tasks
package the completed descriptor reproducibly. Descriptor sources are included in the sources
JAR; no extra Maven publication is created. Set `jvmModuleRelease` and `jvmModuleVersioned` to
place the descriptor in `META-INF/versions/<release>/` instead of at the root. A root descriptor
cannot target a newer release than the baseline. Keep versioned-only implementation packages
unexported: Kotlin `internal` declarations are public in JVM bytecode. The JDK JAR validator may
advise that concealed public classes are still visible on the classpath; this is expected for
that layout and does not indicate a validation failure.

`packagedTestJdks` adds `testJarOnJava17` / `testJarOnJava25` for the JVM plugin, or
`jvmTestJarOnJava17` / `jvmTestJarOnJava25` for KMP. These tasks run the target's test classes using
the same test framework (including its framework options) as the ordinary test task, replacing
loose production outputs with the actual JAR, and are attached to `check`. Configure any custom
system properties, JVM arguments (such as Vector API module flags), or test filtering on these tasks as well as your ordinary test task. Install the requested JDKs
or configure toolchain provisioning in the consuming build.

The `samples/jvm-multi-release` KMP library exercises Java 17 portable code and Java 25 FFM
code. `samples/multi-release-consumer` resolves its staged Maven publication as an independent
Java 17 consumer and executes on both JDKs. The smoke test also runs Android D8 and R8 for
API 24 and asserts that desktop references are absent from the dex output. Build-tools 37
provides D8/R8 with [Kotlin 2.4 metadata support](https://developer.android.com/build/kotlin-support).
The check covers this sample's packaging; libraries still need an Android API audit and device
tests for their own APIs.
To run the sample validation:

```shell
cd samples
../gradlew :jvm-multi-release:check :jvm-multi-release:publishJvmPublicationToLocalStagingRepository :jvm-multi-release:copyAndroidLibraries
../gradlew -p multi-release-consumer runOnJava17 runOnJava25 runModuleOnJava17 runModuleOnJava25
# Requires JAVA_HOME and ANDROID_HOME (or ANDROID_SDK_ROOT).
# Defaults: build-tools 37.0.0 and platform android-36; override with
# ANDROID_BUILD_TOOLS and ANDROID_PLATFORM.
python3 jvm-multi-release/android-smoke.py
```

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

### Publishing a JVM library to GitHub Releases

Apply the GitHub helper beside the JVM conventions. Add `com.eignex.publish.github` to the
plugin resolution configuration above, using the same pinned KBuild version:

```kotlin
plugins {
    id("com.eignex.jvm")
    id("com.eignex.publish.github")
}

group = "org.example"
eignexPublish {
    description.set("My Kotlin library")
    githubRepo.set("Owner/library")
    // artifactId defaults to the project name.
}
```

`githubPublish.repository` inherits `eignexPublish.githubRepo`. `githubReleaseAssets` collects the
existing Maven publications into `build/github-release-assets`, including the JAR, sources,
documentation, POM, Gradle Module Metadata, and `SHA256SUMS`. This task requires no credentials
and invokes no Maven repository upload. The existing Central publishing tasks remain available.

```shell
# For a subproject named core; omit :core for a single-project build.
./gradlew :core:githubReleaseAssets -PciVersion=1.2.3
# Create and push v1.2.3 first, then explicitly publish the staged assets:
./gradlew :core:publishToGitHubRelease -PciVersion=1.2.3
```

The upload task uses `gh release create --verify-tag` with generated release notes and a
prerelease flag for versions containing `-`. Authenticate `gh` normally, or pass `GH_TOKEN`
in CI with `contents: write`. Snapshots are rejected. Existing releases/assets are not replaced.
Uploading is never attached to `build`, `publish`, or the staging task; use the exact project task
path to publish one module rather than selecting upload tasks across a multi-project build.

For a library already configuring its own Maven publications, apply only
`com.eignex.publish.github` and set `githubPublish.repository`; it adds no Central plugin,
metadata defaults, signing, or publishing destinations. The standard `maven-publish` plugin is
used to generate the POM and Gradle metadata for the existing software component.

The helper's configurable properties are:

| Property | Default |
| --- | --- |
| repository | `eignexPublish.githubRepo` when those conventions are applied; otherwise required for upload |
| releaseVersion | Project version |
| releaseTag | `v<releaseVersion>` |
| publicationNames | All Maven publications except plugin markers |
| includeProjects | `[]`; other projects applying this helper whose assets belong to this release |
| assetsDirectory | `build/github-release-assets` |
| prerelease | Whether the release version contains `-` |
| generateReleaseNotes | `true` |
| githubCli | `gh` |

Select publications or aggregate several modules when needed:

```kotlin
githubPublish {
    repository.set("Owner/library")
    publicationNames.set(listOf("mavenJava"))
    includeProjects.set(listOf(":platform"))
}
```

Included publications must share the release version and have distinct artifact filenames.
Each project retains its own staging task; the aggregate regenerates one checksum manifest.
KMP publication redirects are rewritten to the flat asset filenames, preserving variants and
dependencies. Missing target publications and filename collisions fail the staging task.

Consumers add an Ivy repository for the library's own GitHub releases:

```kotlin
repositories {
    ivy {
        url = uri("https://github.com/Owner/library/releases/download")
        patternLayout { artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]") }
        metadataSources { gradleMetadata() }
        content { includeModule("org.example", "core") }
    }
    mavenCentral()
}
dependencies { implementation("org.example:core:1.2.3") }
```

If the library retains the default KBuild platform dependency, consumers also need its repository
as described above. The `v[revision]` pattern assumes the default `releaseTag`; adjust it when
using another tag convention.

KBuild uses this same helper for its own release. `build-logic` is an independent included build
that compiles the helper before the main KBuild plugin is built. The main plugin packages that
same source, so publishing KBuild needs neither an older installed KBuild version nor a
circular dependency on its own output. Root `releaseAssets` remains an alias for this helper's
aggregate staging task, and release CI calls `:publishToGitHubRelease` explicitly.

### Generic Maven publishing

All JVM and KMP publications created by `com.eignex.publish` use the same Maven Central bundle
policy: keep payloads, sources, documentation, Gradle/Kotlin metadata, detached signatures, and
required MD5/SHA-1 checksums; omit optional SHA-256/SHA-512 sidecars and checksums of signatures.
Each signed payload therefore takes four bundle files instead of six. This applies to downstream
libraries automatically, including target-specific publications. Direct local staging and snapshot
repository uploads bypass the Central bundle and retain Gradle's normal checksum behavior.

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

## Building KBuild

KBuild itself is released through GitHub, while `com.eignex.publish` continues to publish downstream
libraries to Maven Central, including signing and the existing bundle file policy.

`./gradlew releaseAssets -PciVersion=1.3.7` stages these files in `build/release-assets`:

```text
kbuild-1.3.7.jar
kbuild-1.3.7-sources.jar
kbuild-1.3.7-javadoc.jar
kbuild-1.3.7.module
kbuild-1.3.7.pom
kbuild-platform-1.3.7.module
kbuild-platform-1.3.7.pom
SHA256SUMS
```

A `v1.3.7` tag uploads these assets to the matching public GitHub Release. Relative URLs in the
generated `.module` files match the asset names without rewriting metadata. Release CI builds and
tests, validates every asset and checksum, then proves anonymous resolution before uploading.
It repeats the consumer checks against the actual public download URLs afterward. Manual runs
with `dryRun=true` stage and validate assets without publishing; a manual publish requires a
version tag. Main pushes no longer upload KBuild snapshots to Sonatype. Existing Central releases
and downstream library publishing are unaffected.

The release checks use Bash 4.2 or newer, `jq`, GNU coreutils, `tar`, `unzip`, and the JDK's
`jwebserver`; they do not require Python. To validate a candidate locally, set JDK 25 in
`JAVA_HOME` and JDK 17 in `JAVA_HOME_17_X64`, and install the wrapper's Gradle version
(CI installs the JDKs and Gradle with `setup-java` and `setup-gradle`):

```shell
./gradlew releaseAssets -PciVersion=1.3.7
bash .github/scripts/check-publication-files.sh build/release-assets --version 1.3.7
bash .github/scripts/check-release-resolution.sh --version 1.3.7 --gradle "$(command -v gradle)"
# After publishing, also check the real public URLs:
bash .github/scripts/check-release-resolution.sh --version 1.3.7 --gradle "$(command -v gradle)" \
  --repository-url https://github.com/Eignex/kbuild/releases/download
```

The resolution check serves the exact release layout over anonymous HTTP, uses two separate empty
Gradle user homes and an isolated home directory, and forwards no credentials. It resolves all six
plugin IDs and their transitive plugins, checks imported BOMs and direct platform constraints,
publishes JVM and KMP libraries to local Maven staging, builds a downstream Central bundle, and
runs an independent Java consumer of the published libraries and their transitive platform.
CI verifies this layout before any consumers are switched; migrate a consumer only after the
chosen public release passes the same check. `publishToMavenLocal` remains available for local
experiments.

Run `./gradlew build`; Foojay toolchain resolution provisions JDKs 17, 21, and 25 when needed.
The build also assembles publication sources and API documentation, failing on Dokka warnings
or errors. The TestKit suite forwards the JDK installation paths to its isolated builds.
Gradle deprecations fail validation through `org.gradle.warning.mode=fail`. The WASI sample uses Wasmtime to avoid
Node's experimental WASI adapter. Run `./samples/smoke-test.sh`
with `JAVA_HOME` pointing to JDK 25 and the Android SDK configured as described above to verify
the JVM, multiplatform, CLI, publication, and Android consumer samples.
