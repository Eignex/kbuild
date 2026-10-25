package com.eignex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Verifies the files downstream projects actually bundle for Central. */
class PublicationFilesTest {
    @ParameterizedTest
    @ValueSource(strings = ["jvm", "kmp"])
    fun `Central bundles retain every payload with only required checksum sidecars`(plugin: String, @TempDir dir: File) {
        val kmp = plugin == "kmp"
        val probe = probe(dir, """
            plugins { id("com.eignex.$plugin") }
            eignexBuild { usePlatformDependencies.set(false) }
            eignexPublish {
                description.set("Publication file policy probe.")
                githubRepo.set("Eignex/probe")
            }
            ${if (kmp) "kotlin { jvm(); js { nodejs() }; linuxX64() }" else ""}
        """)
        probe.write("src/${if (kmp) "commonMain" else "main"}/kotlin/Published.kt", "internal class Published\n")
        val arguments = arrayOf(
            "publishAllPublicationsToMavenCentralStagingRepository", "mavenCentralBundle",
            "-PciVersion=1.0.0", "--configuration-cache",
        )
        probe.build(*arguments)
        assertTrue("Reusing configuration cache" in probe.build(*arguments).output)
        ZipFile(probe.file("build/maven-central-publishing/bundle.zip")).use { zip ->
            val files = zip.entries().asSequence().filterNot { it.isDirectory }.associateBy { it.name }
            assertTrue(files.keys.none { it.endsWith(".sha256") || it.endsWith(".sha512") || ".asc." in it })
            val payloads = files.filterKeys { !it.endsWith(".md5") && !it.endsWith(".sha1") }
            val publications = if (kmp) 4 else 1
            assertEquals(publications, payloads.keys.count { it.endsWith(".pom") })
            assertEquals(publications, payloads.keys.count { it.endsWith(".module") })
            assertEquals(publications, payloads.keys.count { it.endsWith("-sources.jar") })
            assertEquals(publications, payloads.keys.count { it.endsWith("-javadoc.jar") })
            if (kmp) {
                assertTrue(payloads.keys.any { it.endsWith(".klib") })
                assertTrue(payloads.keys.any { it.endsWith("-kotlin-tooling-metadata.json") })
            }
            assertEquals(payloads.size * 3, files.size) // These local test publications are unsigned.
            for ((name, entry) in payloads) {
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                assertEquals(probe.file("build/maven-central-publishing/staging-repository/$name").readBytes().toList(), bytes.toList())
                for (algorithm in listOf("md5", "sha1")) {
                    val digest = MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }
                    val checksum = zip.getInputStream(files.getValue("$name.$algorithm")).bufferedReader().use { it.readText().trim() }
                    assertEquals(digest, checksum, name)
                }
            }
        }
    }
}
