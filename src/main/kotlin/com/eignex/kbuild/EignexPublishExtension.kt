package com.eignex.kbuild

import org.gradle.api.provider.Property

/** Eignex-flavoured defaults for the `com.eignex.publish` convention plugin. */
abstract class EignexPublishExtension : MavenPublishExtension() {

    /** GitHub repository in "Owner/repo" form, used to derive URL and SCM defaults. */
    abstract val githubRepo: Property<String>

    /**
     * Whether to set up Maven publication and signing. Defaults to `true`. Set to `false` for
     * internal modules (benchmarks, samples) that use the build conventions but are never
     * published. That skips the publication wiring and metadata validation.
     */
    abstract val publish: Property<Boolean>
}
