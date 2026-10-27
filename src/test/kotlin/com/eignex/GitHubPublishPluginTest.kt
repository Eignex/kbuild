package com.eignex

import groovy.json.JsonSlurper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.security.MessageDigest

/** Exercises staged publications, aggregation, and upload commands without contacting GitHub. */
class GitHubPublishPluginTest {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `JVM libraries stage complete artifacts in either plugin order`(githubFirst: Boolean, @TempDir dir: File) {
        val plugins = listOf("com.eignex.jvm", "com.eignex.publish.github").let { if (githubFirst) it.reversed() else it }
        val probe = probe(dir, """
            plugins { ${plugins.joinToString("; ") { "id(\"$it\")" }} }
            group = "org.example"
            eignexBuild { usePlatformDependencies.set(false) }
            eignexPublish {
                artifactId.set("published-core")
                description.set("A GitHub-published Kotlin library.")
                githubRepo.set("example/library")
            }
            dependencies { implementation("org.ow2.asm:asm:9.9") }
            tasks.register("verifyGitHubRepo") {
                val configuredRepo = githubPublish.repository
                doLast { check(configuredRepo.get() == "example/library") }
            }
        """)
        probe.write("src/main/kotlin/example/Api.kt", "package example\n/** A published API. */\nclass Api\n")
        val arguments = arrayOf("githubReleaseAssets", "verifyGitHubRepo", "-PciVersion=2.0.0", "--configuration-cache")
        probe.build(*arguments)
        assertTrue("Reusing configuration cache" in probe.build(*arguments).output)
        val assets = probe.file("build/github-release-assets")
        assertEquals(setOf("published-core-2.0.0.jar", "published-core-2.0.0-sources.jar", "published-core-2.0.0-javadoc.jar",
            "published-core-2.0.0.pom", "published-core-2.0.0.module", "SHA256SUMS"), assets.list()!!.toSet())
        assertEquals(probe.file("build/publications/mavenJava/module.json").readText(), assets.resolve("published-core-2.0.0.module").readText())
        assertTrue("\"module\": \"asm\"" in assets.resolve("published-core-2.0.0.module").readText())
        verifyChecksums(assets)
        val plan = probe.build("githubReleaseAssets", "-PciVersion=2.0.0", "--dry-run").output
        assertFalse("uploadMavenCentralBundle" in plan || "ToSonatypeSnapshotsRepository" in plan || "publishToGitHubRelease" in plan)
    }

    @Test
    fun `multiple projects aggregate a library and platform without duplicate manifests`(@TempDir dir: File) {
        val probe = plainProbe(dir, "githubPublish { includeProjects.set(listOf(\":bom\")) }")
        probe.write("settings.gradle.kts", "rootProject.name = \"probe\"\ninclude(\"bom\")\n")
        probe.write("bom/build.gradle.kts", """
            plugins { `java-platform`; id("com.eignex.publish.github") }
            group = "org.example"
            version = rootProject.version
            dependencies { constraints { api("org.ow2.asm:asm:9.9") } }
            publishing { publications { create<MavenPublication>("platform") { from(components["javaPlatform"]) } } }
        """.trimIndent())
        val arguments = arrayOf(":githubReleaseAssets", "--configuration-cache")
        probe.build(*arguments)
        assertTrue("Reusing configuration cache" in probe.build(*arguments).output)
        val assets = probe.file("build/github-release-assets")
        assertTrue(assets.resolve("bom-2.0.0.module").isFile)
        assertTrue("dependencyConstraints" in assets.resolve("bom-2.0.0.module").readText())
        verifyChecksums(assets)
    }

    @Test
    fun `flat asset collisions fail instead of overwriting another coordinate`(@TempDir dir: File) {
        val probe = plainProbe(dir, "githubPublish { includeProjects.set(listOf(\":other\")) }")
        probe.write("settings.gradle.kts", "rootProject.name = \"probe\"\ninclude(\"other\")\n")
        probe.write("other/build.gradle.kts", """
            plugins { `java-library`; id("com.eignex.publish.github") }
            group = "another.group"
            version = rootProject.version
            publishing { publications { create<MavenPublication>("library") { from(components["java"]); artifactId = "probe" } } }
        """.trimIndent())
        assertTrue("Duplicate GitHub release asset" in probe.buildAndFail(":githubReleaseAssets").output)
    }

    @Test
    fun `KMP publication redirects resolve within a flat release`(@TempDir dir: File) {
        val probe = probe(dir, """
            plugins { id("com.eignex.kmp"); id("com.eignex.publish.github") }
            eignexBuild { usePlatformDependencies.set(false) }
            eignexPublish { description.set("A staged KMP library."); githubRepo.set("example/library") }
            kotlin { jvm() }
        """)
        probe.write("src/commonMain/kotlin/Api.kt", "/** A published API. */\nclass Api\n")
        probe.build("githubReleaseAssets", "-PciVersion=2.0.0")
        val assets = probe.file("build/github-release-assets")
        val metadata = JsonSlurper().parse(assets.resolve("probe-2.0.0.module")) as Map<*, *>
        val redirects = (metadata["variants"] as List<*>).map { it as Map<*, *> }.mapNotNull { it["available-at"] as? Map<*, *> }
        assertTrue(redirects.isNotEmpty())
        assertTrue(redirects.all { it["url"] == "probe-jvm-2.0.0.module" })
        assertTrue(assets.resolve("probe-jvm-2.0.0.module").isFile)
        verifyChecksums(assets)
    }

    @Test
    fun `upload invokes gh explicitly and remains executable with the configuration cache`(@TempDir dir: File) {
        val cli = dir.resolve("gh fake")
        val recorded = dir.resolve("gh-arguments.txt")
        val probe = plainProbe(dir, """
            githubPublish {
                repository.set("example/library")
                githubCli.set("${cli.absolutePath}")
            }
        """)
        probe.write(cli.name, "#!/bin/sh\nprintf '%s\\n' \"\$@\" >> '${recorded.absolutePath}'\n").setExecutable(true)
        val arguments = arrayOf("publishToGitHubRelease", "-PciVersion=2.0.0-rc.1", "--configuration-cache")
        probe.build(*arguments)
        assertTrue("Reusing configuration cache" in probe.build(*arguments).output)
        val args = recorded.readLines()
        assertEquals(2, args.count { it == "create" })
        assertTrue(listOf("release", "create", "v2.0.0-rc.1", "--repo", "example/library", "--verify-tag", "--generate-notes", "--prerelease").all { it in args })
        assertTrue(args.any { it.endsWith("/SHA256SUMS") })
        assertTrue(args.any { it.endsWith("/probe-2.0.0-rc.1.module") })
        assertFalse("--clobber" in args)
    }

    @Test
    fun `snapshot uploads fail before invoking gh`(@TempDir dir: File) {
        val probe = plainProbe(dir, "githubPublish { repository.set(\"example/library\"); githubCli.set(\"must-not-run\") }")
        assertTrue("GitHub Releases require a pinned release version" in
            probe.buildAndFail("publishToGitHubRelease", "-PciVersion=2.0.0-SNAPSHOT").output)
    }

    private fun plainProbe(dir: File, configuration: String) = probe(dir, """
        plugins { `java-library`; id("com.eignex.publish.github") }
        group = "org.example"
        version = providers.gradleProperty("ciVersion").getOrElse("2.0.0")
        java { withSourcesJar() }
        publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }
        $configuration
    """).apply {
        write("src/main/java/Api.java", "/** A published API. */\npublic class Api {}\n")
    }

    private fun verifyChecksums(directory: File) {
        val lines = directory.resolve("SHA256SUMS").readLines()
        assertEquals(directory.list()!!.filter { it != "SHA256SUMS" }.sorted(), lines.map { it.substringAfter("  ") })
        lines.forEach { line ->
            val file = directory.resolve(line.substringAfter("  "))
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(digest, line.substringBefore("  "), file.name)
        }
    }
}
