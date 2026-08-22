package com.eignex.kbuild

import org.gradle.api.provider.Property

/** Configuration for the `com.eignex.cli` convention plugin. */
abstract class EignexCliExtension {
    /** Fully qualified JVM main class, e.g. "com.example.MainKt". */
    abstract val mainClass: Property<String>

    /** Kotlin/Native entry point function, e.g. "com.example.main". Defaults to a root-package main. */
    abstract val entryPoint: Property<String>

    /** Version reported via the generated `BuildInfo.VERSION`. Defaults to the project version. */
    abstract val version: Property<String>

    /** Human-facing application name for `BuildInfo.NAME`. Defaults to the project name. */
    abstract val appName: Property<String>

    /** Reverse-DNS application id for `BuildInfo.ID`. Defaults to the project group. */
    abstract val appId: Property<String>

    /**
     * Package the generated `BuildInfo` object is emitted into. Defaults to the package of
     * [mainClass], falling back to the project group when [mainClass] is unset.
     */
    abstract val buildInfoPackage: Property<String>

    /** Whether to register the `releaseAssets` task. Defaults to `true`. */
    abstract val releaseAssetsEnabled: Property<Boolean>

    /** Release asset prefix. Defaults to the project name. */
    abstract val releaseAssetPrefix: Property<String>

    /** Directory for release assets, relative to the project. */
    abstract val releaseAssetsDirectory: Property<String>

    /** Whether native release assets should be stripped. Defaults to `true`. */
    abstract val stripReleaseBinaries: Property<Boolean>
}
