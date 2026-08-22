package com.eignex.kbuild

import org.gradle.api.provider.Property

/** Shared defaults and opt-outs used by the JVM, KMP, and CLI convention plugins. */
abstract class EignexBuildExtension {
    /** JVM toolchain used by Kotlin and lint. Defaults to 25. */
    abstract val jvmToolchain: Property<Int>

    /** Whether to add the platform BOM and kotlin-test dependencies. */
    abstract val usePlatformDependencies: Property<Boolean>

    /** Whether to add `kotlin-test` to test source sets. Defaults to `true`. */
    abstract val useKotlinTestDependency: Property<Boolean>

    /** Whether the convention adds Maven Central to repositories. Defaults to `true`. */
    abstract val useMavenCentral: Property<Boolean>

    /** Group of the platform BOM. Defaults to `com.eignex`. */
    abstract val platformGroup: Property<String>

    /** Fallback project group used by generated CLI metadata. Defaults to `com.eignex`. */
    abstract val defaultGroup: Property<String>

    /** Artifact of the platform BOM. Defaults to `kbuild-platform`. */
    abstract val platformArtifact: Property<String>

    /** Version of the platform BOM. Defaults to this build's platform version. */
    abstract val platformVersion: Property<String>

    /** Node.js version used by JS and Wasm tests. Defaults to `25.0.0`. */
    abstract val nodeVersion: Property<String>

    /** JavaScript test timeout. Defaults to `120s`. */
    abstract val jsTestTimeout: Property<String>

    /** Whether the convention's Kover plugin tasks should be enabled. */
    abstract val koverEnabled: Property<Boolean>

    /** Whether Kotlin ABI validation is enabled for KMP projects. Defaults to `true`. */
    abstract val abiValidationEnabled: Property<Boolean>

    /** Whether the convention's Detekt tasks should be enabled. */
    abstract val lintEnabled: Property<Boolean>
}
