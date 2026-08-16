package com.eignex

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * TestKit harness for com.eignex.publish. The probe is a plain `java-library` with no sources, so
 * every assertion here is about publication metadata rather than compilation. Signing is exercised
 * only up to the point of task wiring; no real key is ever handed to GPG.
 */
class PublishPluginTest {

    @Test
    fun `the generated pom carries the extension metadata and the fixed license, scm and developer`(
        @TempDir dir: File,
    ) {
        val probe = writeProbe(dir)
        probe.build("generatePomFileForMavenJavaPublication")

        val pom = probe.file(POM).readText()
        assertTrue("<artifactId>probe</artifactId>" in pom) { pom }
        assertTrue("<name>probe</name>" in pom) { pom }
        assertTrue("<description>A probe module.</description>" in pom) { pom }
        // Every URL in the pom is derived from githubRepo; a wrong one breaks Central validation.
        assertTrue("<url>https://github.com/Eignex/probe</url>" in pom) { pom }
        assertTrue("scm:git:https://github.com/Eignex/probe.git" in pom) { pom }
        assertTrue("scm:git:ssh://git@github.com/Eignex/probe.git" in pom) { pom }
        assertTrue("<name>Apache-2.0</name>" in pom) { pom }
        assertTrue("<id>rasros</id>" in pom) { pom }
    }

    @Test
    fun `artifactId overrides the project name in the pom`(@TempDir dir: File) {
        val probe = writeProbe(dir, publishBlock = extension(artifactId = "probe-core"))
        probe.build("generatePomFileForMavenJavaPublication")

        val pom = probe.file(POM).readText()
        assertTrue("<artifactId>probe-core</artifactId>" in pom) { pom }
        // name follows the artifactId, not the project name.
        assertTrue("<name>probe-core</name>" in pom) { pom }
    }

    // githubRepo has no default on purpose: a published pom pointing at the wrong repository is
    // worse than a build that stops.
    @Test
    fun `a missing githubRepo fails configuration`(@TempDir dir: File) {
        val probe = writeProbe(dir, publishBlock = "eignexPublish { description.set(\"A probe module.\") }")
        val output = probe.buildAndFail("generatePomFileForMavenJavaPublication").output
        assertTrue("githubRepo" in output) {
            "expected a failure naming the unset githubRepo, got:\n$output"
        }
    }

    // Internal modules opt out; they must not need githubRepo and must get no publication.
    @Test
    fun `publish false skips the publication without requiring githubRepo`(@TempDir dir: File) {
        val probe = writeProbe(dir, publishBlock = "eignexPublish { publish.set(false) }")
        val output = probe.buildAndFail("generatePomFileForMavenJavaPublication").output
        assertTrue("Task 'generatePomFileForMavenJavaPublication' not found" in output) {
            "expected no mavenJava publication, got:\n$output"
        }
    }

    @Test
    fun `publishing to the local staging repository lays out the maven coordinates`(@TempDir dir: File) {
        val probe = writeProbe(dir)
        probe.build("publishMavenJavaPublicationToLocalStagingRepository", "-PciVersion=2.0.0")

        val moduleDir = probe.file("build/staging-repo/com/eignex/probe/2.0.0")
        val files = moduleDir.list()?.toList().orEmpty()
        assertTrue("probe-2.0.0.jar" in files) { "expected the jar in $files" }
        assertTrue("probe-2.0.0.pom" in files) { "expected the pom in $files" }
    }

    @Test
    fun `snapshots are published unsigned`(@TempDir dir: File) {
        val probe = writeProbe(dir)
        // No ciVersion, so the version is SNAPSHOT: signing every snapshot doubles the uploads for
        // a repository that never validates them.
        val output = probe.build("tasks", "--all", "-PsigningKey=not-a-key", "-PsigningPassword=nope").output
        assertTrue("Signing skipped: SNAPSHOT is a snapshot." in output) { output }
        assertFalse("signMavenJavaPublication" in output) {
            "expected no signing task for a snapshot, got:\n$output"
        }
    }

    @Test
    fun `a release without credentials is published unsigned rather than failing`(@TempDir dir: File) {
        val output = writeProbe(dir).build("tasks", "--all", "-PciVersion=2.0.0").output
        assertTrue("Signing disabled: signingKey or signingPassword not defined." in output) { output }
        assertFalse("signMavenJavaPublication" in output) {
            "expected no signing task without credentials, got:\n$output"
        }
    }

    // Wiring only: the fake key is never used, because --dry-run stops before the signatory is
    // asked for a signature. Real GPG signing stays out of the test suite.
    @Test
    fun `a release with credentials wires signing into the publication`(@TempDir dir: File) {
        val output = writeProbe(dir).build(
            "publishMavenJavaPublicationToLocalStagingRepository",
            "--dry-run",
            "-PciVersion=2.0.0",
            "-PsigningKey=not-a-key",
            "-PsigningPassword=nope",
        ).output
        assertTrue(":signMavenJavaPublication SKIPPED" in output) {
            "expected the publication to be signed, got:\n$output"
        }
        assertFalse("Signing disabled" in output) { output }
    }

    // A KMP project has no `java` component, so the plugin takes its other branch: the Kotlin
    // plugin creates one publication per target plus a `kotlinMultiplatform` root, and the POM
    // and javadoc jar have to be attached to each of them rather than to a single `mavenJava`.
    @Test
    fun `every publication of a KMP project carries the common pom`(@TempDir dir: File) {
        val probe = writeKmpProbe(dir)

        probe.build(*KMP_PUBLICATIONS.map { "generatePomFileFor${it.capitalizeFirst()}Publication" }.toTypedArray())

        for (pub in KMP_PUBLICATIONS) {
            val pom = probe.file("build/publications/$pub/pom-default.xml").readText()
            assertTrue("<description>A probe module.</description>" in pom) { "$pub:\n$pom" }
            assertTrue("<url>https://github.com/Eignex/probe</url>" in pom) { "$pub:\n$pom" }
            assertTrue("scm:git:https://github.com/Eignex/probe.git" in pom) { "$pub:\n$pom" }
            assertTrue("<name>Apache-2.0</name>" in pom) { "$pub:\n$pom" }
            assertTrue("<id>rasros</id>" in pom) { "$pub:\n$pom" }
        }
    }

    // Central rejects any publication without a javadoc jar, so each of them needs its own,
    // and the task is registered from inside a configureEach over the publications, which is
    // late enough that a publication first realized during execution would break it.
    @Test
    fun `every publication of a KMP project gets its own javadoc jar`(@TempDir dir: File) {
        val probe = writeKmpProbe(dir)

        val output = probe.build("publishAllPublicationsToLocalStagingRepository", "--dry-run").output

        for (pub in KMP_PUBLICATIONS) {
            assertTrue(":${pub}JavadocJar SKIPPED" in output) {
                "expected a javadoc jar for the $pub publication, got:\n$output"
            }
        }
    }

    // An asymmetry worth pinning down rather than discovering during a release: on a KMP project
    // the coordinates come from the Kotlin plugin (project name + target), so eignexPublish's
    // artifactId reaches only the POM's <name>. On a JVM project it sets both.
    @Test
    fun `artifactId renames the KMP pom without moving its coordinates`(@TempDir dir: File) {
        val probe = writeKmpProbe(dir, publishBlock = extension(artifactId = "probe-core"))

        probe.build("generatePomFileForJvmPublication")

        val pom = probe.file("build/publications/jvm/pom-default.xml").readText()
        assertTrue("<artifactId>probe-jvm</artifactId>" in pom) { pom }
        assertTrue("<name>probe-core</name>" in pom) { pom }
    }

    private fun String.capitalizeFirst() = replaceFirstChar { it.uppercase() }

    private fun extension(artifactId: String? = null) = buildString {
        appendLine("eignexPublish {")
        artifactId?.let { appendLine("    artifactId.set(\"$it\")") }
        appendLine("    description.set(\"A probe module.\")")
        appendLine("    githubRepo.set(\"Eignex/probe\")")
        appendLine("}")
    }

    private fun writeProbe(dir: File, publishBlock: String = extension()): GradleProbe = probe(
        dir,
        """
        plugins {
            `java-library`
            id("com.eignex.publish")
        }
        $publishBlock
        """
    )

    /**
     * A multiplatform probe with publishing switched on, unlike [kmpProbe]. The targets are the
     * two that need no toolchain download and exist on every host: a native target's publication
     * is only created where it can be cross-compiled, which would make this host-dependent.
     *
     * The fake platform repo is required here, not just convenient: generating a KMP POM
     * resolves the dependency graph, and commonMain pulls in the kbuild platform BOM.
     */
    private fun writeKmpProbe(dir: File, publishBlock: String = extension()): GradleProbe = probe(
        dir,
        """
        plugins {
            id("com.eignex.kmp")
        }
        ${writeFakePlatformRepo(dir)}
        $publishBlock
        kotlin {
            jvm()
            js { nodejs() }
        }
        """
    ).apply {
        write("src/commonMain/kotlin/Probe.kt", "internal class Probe\n")
    }

    private companion object {
        const val POM = "build/publications/mavenJava/pom-default.xml"

        /** What `kotlin { jvm(); js() }` produces: one per target, plus the metadata root. */
        val KMP_PUBLICATIONS = listOf("kotlinMultiplatform", "jvm", "js")
    }
}
