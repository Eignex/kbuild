package com.eignex.kbuild

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/** One Maven license entry. */
open class MavenLicenseSpec(objects: ObjectFactory) {
    val name: Property<String> = objects.property(String::class.java)
    val url: Property<String> = objects.property(String::class.java)
    val distribution: Property<String> = objects.property(String::class.java)
}

/** DSL container for Maven license entries. */
open class MavenLicenses(private val objects: ObjectFactory) {
    private val entries = mutableListOf<MavenLicenseSpec>()

    fun license(action: Action<in MavenLicenseSpec>) {
        MavenLicenseSpec(objects).also {
            action.execute(it)
            entries += it
        }
    }

    internal fun entries(): List<MavenLicenseSpec> = entries
}

/** Generic Maven POM metadata for the `com.eignex.publish` convention plugin. */
abstract class MavenPublishExtension {
    @get:Inject
    protected abstract val objects: ObjectFactory

    private val configuredLicenses = mutableListOf<MavenLicenseSpec>()

    fun licenses(action: Action<in MavenLicenses>) {
        MavenLicenses(objects).also {
            action.execute(it)
            configuredLicenses += it.entries()
        }
    }

    internal fun licenseEntries(): List<MavenLicenseSpec> = configuredLicenses

    /** Maven artifact ID. Defaults to the Gradle project name. */
    abstract val artifactId: Property<String>

    /** POM description. */
    abstract val description: Property<String>

    /** Maven license name. */
    abstract val licenseName: Property<String>

    /** Maven license URL. */
    abstract val licenseUrl: Property<String>

    /** Project URL in the POM. */
    abstract val projectUrl: Property<String>

    /** SCM URL in the POM. */
    abstract val scmUrl: Property<String>

    /** SCM read-only connection in the POM. */
    abstract val scmConnection: Property<String>

    /** SCM developer connection in the POM. */
    abstract val scmDeveloperConnection: Property<String>

    /** Developer ID in the POM. */
    abstract val developerId: Property<String>

    /** Developer name in the POM. */
    abstract val developerName: Property<String>

    /** Developer URL in the POM. */
    abstract val developerUrl: Property<String>
}
