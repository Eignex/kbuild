# KBuild

Gradle conventions for Kotlin libraries and CLI applications: testing, lint,
documentation and publishing. Requires JDK 21+; JVM compilation and tests default to JDK 17.

## Setup

Use the [plugin repository setup](samples/release-resolution/settings.gradle.kts)
and [platform repository setup](samples/release-resolution/build.gradle.kts), with
these properties in `gradle.properties`:

```properties
kbuildVersion=<release version>
releaseRepository=https://github.com/Eignex/kbuild/releases/download
```

Choose a version from [Releases](https://github.com/Eignex/kbuild/releases).

## Usage

- `com.eignex.jvm`: [JVM library example](samples/jvm-positive/build.gradle.kts).
- `com.eignex.kmp`: [multiplatform library example](samples/kmp-positive/build.gradle.kts).
- `com.eignex.cli`: [CLI example](samples/cli-positive/build.gradle.kts); `releaseAssets` stages distributions.

Apply `kotlin("plugin.serialization")` without a version to use the bundled plugin.
`check` runs tests and lint; `lintDocs` validates documentation. Lint is read-only
by default; `formatKotlin` applies corrections. Override rules in a root `detekt.yml`.

Configure [build](src/main/kotlin/com/eignex/kbuild/EignexBuildExtension.kt),
[lint](src/main/kotlin/com/eignex/kbuild/EignexLintExtension.kt),
[CLI](src/main/kotlin/com/eignex/kbuild/EignexCliExtension.kt) and
[publishing](src/main/kotlin/com/eignex/kbuild/MavenPublishExtension.kt) through their extensions.
Use `eignexPublish { publish.set(false) }` for internal modules.
