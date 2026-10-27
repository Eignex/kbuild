package com.eignex.kbuild

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty

/** Shared defaults and opt-outs used by the JVM, KMP, and CLI convention plugins. */
abstract class EignexBuildExtension {
    /** JVM toolchain used by Kotlin and lint. Defaults to 17. */
    abstract val jvmToolchain: Property<Int>

    /** Published JVM bytecode baseline. Defaults to [jvmToolchain]. */
    abstract val jvmTarget: Property<Int>

    /** Optional newer JVM release packaged under `META-INF/versions/<release>`. */
    abstract val multiReleaseJvmVersion: Property<Int>

    /** Optional library-owned `module-info.java`, compiled separately against the JVM outputs. */
    abstract val jvmModuleDescriptor: RegularFileProperty

    /** Java release of the module descriptor. Defaults to the baseline, with a minimum of 9. */
    abstract val jvmModuleRelease: Property<Int>

    /** Places the descriptor under its release directory. Defaults to true for baselines below 9. */
    abstract val jvmModuleVersioned: Property<Boolean>

    /** JDKs on which to run tests against the packaged JVM JAR. Defaults to an empty list. */
    abstract val packagedTestJdks: ListProperty<Int>

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

    /** Node.js version used by JS and Wasm tests. Defaults to `26.12.1`. */
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
