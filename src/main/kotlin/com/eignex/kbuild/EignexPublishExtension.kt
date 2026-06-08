package com.eignex.kbuild

import org.gradle.api.provider.Property

abstract class EignexPublishExtension {
    /** Maven artifact ID. Defaults to the Gradle project name. */
    abstract val artifactId: Property<String>

    /** POM description. */
    abstract val description: Property<String>

    /** GitHub repository in "Owner/repo" form, e.g. "Eignex/kencode". */
    abstract val githubRepo: Property<String>

    /**
     * Whether to set up Maven publication and signing. Defaults to `true`. Set to `false` for
     * internal modules (benchmarks, samples) that use the build conventions but are never
     * published — this skips the publication wiring and the [githubRepo] requirement.
     */
    abstract val publish: Property<Boolean>
}
