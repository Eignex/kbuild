package com.eignex.kbuild

import org.gradle.api.provider.Property

abstract class EignexPublishExtension {
    /** Maven artifact ID. Defaults to the Gradle project name. */
    abstract val artifactId: Property<String>

    /** POM description. */
    abstract val description: Property<String>

    /** GitHub repository in "Owner/repo" form, e.g. "Eignex/kencode". */
    abstract val githubRepo: Property<String>
}
