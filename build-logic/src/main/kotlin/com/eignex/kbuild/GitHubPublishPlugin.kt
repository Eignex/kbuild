package com.eignex.kbuild

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

/** GitHub Release packaging and explicit publication of existing Maven publications. */
abstract class GitHubPublishExtension {
    /** GitHub `owner/repository`; required only by the upload task. */
    abstract val repository: Property<String>

    /** Shared release version. Defaults to the project version. */
    abstract val releaseVersion: Property<String>

    /** Release tag. Defaults to `v<releaseVersion>` and must already exist remotely. */
    abstract val releaseTag: Property<String>

    /** Optional selection of Maven publication names; defaults to all except plugin markers. */
    abstract val publicationNames: ListProperty<String>

    /** Other projects whose GitHub release assets belong to the same release. */
    abstract val includeProjects: ListProperty<String>

    /** Output directory. Defaults to `build/github-release-assets`. */
    abstract val assetsDirectory: DirectoryProperty

    /** Marks the GitHub release as a prerelease. Defaults to whether its version contains `-`. */
    abstract val prerelease: Property<Boolean>

    /** Whether GitHub generates release notes. Defaults to true. */
    abstract val generateReleaseNotes: Property<Boolean>

    /** GitHub CLI executable. Defaults to `gh`; authentication stays with the CLI. */
    abstract val githubCli: Property<String>
}

/** A generated publication file and its final name in the flat release directory. */
abstract class GitHubPublicationFile {
    /** Name of the versioned release asset. */
    @get:Input
    abstract val filename: Property<String>

    /** Artifact or generated metadata supplied by Gradle's publishing tasks. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty
}

/** Collects publications without invoking Maven repository uploads or requiring credentials. */
@CacheableTask
abstract class GitHubReleaseAssets : DefaultTask() {
    /** Selected publication artifacts and their generated metadata. */
    @get:Nested
    abstract val publicationFiles: ListProperty<GitHubPublicationFile>

    /** Asset directories produced by included projects. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val includedAssets: ConfigurableFileCollection

    /** Expected release version for every staged coordinate. */
    @get:Input
    abstract val releaseVersion: Property<String>

    /** Flat output directory, including a SHA256SUMS manifest. */
    @get:OutputDirectory
    abstract val assetsDirectory: DirectoryProperty

    /** Stages the artifacts and rewrites KMP metadata redirects for a flat repository. */
    @TaskAction
    fun stage() {
        val files = linkedMapOf<String, File>()
        fun add(name: String, source: File) {
            require(name.isNotBlank() && name != "SHA256SUMS" && File(name).name == name && '\\' !in name) {
                "Invalid GitHub release asset name: $name"
            }
            check(files.putIfAbsent(name, source) == null) {
                "Duplicate GitHub release asset: $name. Use distinct artifact IDs across projects."
            }
        }
        publicationFiles.get().forEach { add(it.filename.get(), it.sourceFile.get().asFile) }
        includedAssets.files.forEach { directory ->
            directory.listFiles().orEmpty().filter { it.isFile && it.name != "SHA256SUMS" }
                .sortedBy { it.name }.forEach { add(it.name, it) }
        }
        check(files.isNotEmpty()) { "No Maven publications selected for GitHub Release assets." }
        val directory = assetsDirectory.get().asFile
        check(files.values.none { it.toPath().startsWith(directory.toPath()) }) {
            "GitHub assetsDirectory must not contain its own publication inputs."
        }
        directory.deleteRecursively()
        directory.mkdirs()
        for ((name, source) in files) source.copyTo(directory.resolve(name))
        // JVM/platform metadata is preserved byte for byte. KMP root metadata otherwise points
        // at Maven directory paths that do not exist among flat GitHub Release assets.
        directory.listFiles().orEmpty().filter { it.extension == "module" }.forEach { file ->
            @Suppress("UNCHECKED_CAST")
            val metadata = JsonSlurper().parse(file) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val component = metadata["component"] as Map<String, Any?>
            check(component["version"] == releaseVersion.get()) {
                "Publication ${component["module"]} has version ${component["version"]}, expected ${releaseVersion.get()}."
            }
            var changed = false
            @Suppress("UNCHECKED_CAST")
            val variants = metadata["variants"] as List<Map<String, Any?>>
            variants.forEach variantLoop@{ variant ->
                @Suppress("UNCHECKED_CAST")
                val redirect = variant["available-at"] as? MutableMap<String, Any?> ?: return@variantLoop
                val target = "${redirect["module"]}-${redirect["version"]}.module"
                check(target in files) { "Missing GitHub release publication referenced by ${file.name}: $target" }
                if (redirect["url"] != target) {
                    redirect["url"] = target
                    changed = true
                }
            }
            if (changed) file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n")
        }
        directory.resolve("SHA256SUMS").writeText(files.keys.sorted().joinToString("") { name ->
            val digest = MessageDigest.getInstance("SHA-256").digest(directory.resolve(name).readBytes())
                .joinToString("") { "%02x".format(it) }
            "$digest  $name\n"
        })
    }
}

/** Creates a release using GitHub CLI authentication, only when explicitly requested. */
@DisableCachingByDefault(because = "Creates a remote GitHub Release and must never be cached")
abstract class PublishToGitHubRelease : DefaultTask() {
    /** GitHub `owner/repository` to publish to. */
    @get:Input
    abstract val repository: Property<String>

    /** Existing remote tag used for the release. */
    @get:Input
    abstract val releaseTag: Property<String>

    /** Pinned version being published. */
    @get:Input
    abstract val releaseVersion: Property<String>

    /** Whether GitHub should mark the release as a prerelease. */
    @get:Input
    abstract val prerelease: Property<Boolean>

    /** Whether GitHub should generate release notes. */
    @get:Input
    abstract val generateReleaseNotes: Property<Boolean>

    /** Authenticated GitHub CLI executable. */
    @get:Input
    abstract val githubCli: Property<String>

    /** Staged assets to upload, including SHA256SUMS. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsDirectory: DirectoryProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    /** Uploads a new release without replacing existing releases or assets. */
    @TaskAction
    fun publish() {
        require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repository.get())) {
            "githubPublish.repository must be owner/repository."
        }
        require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?").matches(releaseVersion.get()) &&
            !releaseVersion.get().contains("SNAPSHOT")) { "GitHub Releases require a pinned release version." }
        val arguments = mutableListOf(githubCli.get(), "release", "create", releaseTag.get(),
            "--repo", repository.get(), "--verify-tag")
        if (generateReleaseNotes.get()) arguments += "--generate-notes"
        if (prerelease.get()) arguments += "--prerelease"
        arguments += assetsDirectory.get().asFile.listFiles().orEmpty().filter { it.isFile }
            .sortedBy { it.name }.map { it.absolutePath }
        execOperations.exec { commandLine(arguments) }.assertNormalExitValue()
    }
}

/** Packages existing Maven publications and exposes an explicit GitHub Release upload task. */
class GitHubPublishPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("maven-publish")
        val extension = project.extensions.create("githubPublish", GitHubPublishExtension::class.java)
        extension.publicationNames.convention(emptyList())
        extension.includeProjects.convention(emptyList())
        extension.assetsDirectory.convention(project.layout.buildDirectory.dir("github-release-assets"))
        extension.releaseTag.convention(extension.releaseVersion.map { "v$it" })
        extension.prerelease.convention(extension.releaseVersion.map { '-' in it })
        extension.generateReleaseNotes.convention(true)
        extension.githubCli.convention("gh")
        val assets = project.tasks.register("githubReleaseAssets", GitHubReleaseAssets::class.java) {
            group = "publishing"
            description = "Stages publications and metadata as anonymous GitHub Release assets."
            assetsDirectory.set(extension.assetsDirectory)
            releaseVersion.set(extension.releaseVersion)
            publicationFiles.convention(emptyList())
        }
        project.tasks.register("publishToGitHubRelease", PublishToGitHubRelease::class.java) {
            group = "publishing"
            description = "Creates a GitHub Release using gh; the version tag must already exist."
            repository.set(extension.repository)
            releaseTag.set(extension.releaseTag)
            releaseVersion.set(extension.releaseVersion)
            prerelease.set(extension.prerelease)
            generateReleaseNotes.set(extension.generateReleaseNotes)
            githubCli.set(extension.githubCli)
            assetsDirectory.set(assets.flatMap { it.assetsDirectory })
        }
        // Publication identities and artifacts must be read after all convention plugins have
        // finished configuring them, regardless of plugin application order.
        project.gradle.projectsEvaluated {
            extension.releaseVersion.convention(project.version.toString())
            val publications = project.extensions.getByType(PublishingExtension::class.java).publications
                .withType(MavenPublication::class.java)
            val selected = extension.publicationNames.get()
            require((selected - publications.names).isEmpty()) { "Unknown GitHub publication names: ${selected - publications.names}" }
            publications.filter { if (selected.isEmpty()) !it.artifactId.endsWith(".gradle.plugin") else it.name in selected }
                .forEach { publication ->
                    require(publication.version == extension.releaseVersion.get()) {
                        "GitHub publication ${publication.name} version ${publication.version} must match releaseVersion ${extension.releaseVersion.get()}."
                    }
                    val suffix = publication.name.replaceFirstChar { it.uppercase() }
                    val metadata = project.tasks.named("generateMetadataFileFor${suffix}Publication", GenerateModuleMetadata::class.java)
                    val pom = project.tasks.named("generatePomFileFor${suffix}Publication", GenerateMavenPom::class.java)
                    fun add(filename: String, file: File) {
                        val entry = project.objects.newInstance(GitHubPublicationFile::class.java)
                        entry.filename.set(filename)
                        entry.sourceFile.set(file)
                        assets.configure { publicationFiles.add(entry) }
                    }
                    val prefix = "${publication.artifactId}-${publication.version}"
                    add("$prefix.module", metadata.get().outputFile.get().asFile)
                    add("$prefix.pom", pom.get().destination)
                    assets.configure { dependsOn(metadata, pom) }
                    publication.artifacts.forEach { artifact ->
                        val classifier = artifact.classifier?.takeIf { it.isNotEmpty() }?.let { "-$it" }.orEmpty()
                        add("$prefix$classifier.${artifact.extension}", artifact.file)
                        assets.configure { dependsOn(artifact.buildDependencies) }
                    }
                }
            extension.includeProjects.get().forEach { path ->
                val included = project.project(path)
                require(included != project && included.pluginManager.hasPlugin("com.eignex.publish.github")) {
                    "Included GitHub project $path must be another project applying com.eignex.publish.github."
                }
                val includedTask = included.tasks.named("githubReleaseAssets", GitHubReleaseAssets::class.java)
                assets.configure {
                    dependsOn(includedTask)
                    includedAssets.from(includedTask.flatMap { it.assetsDirectory })
                }
            }
        }
    }
}
