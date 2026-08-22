package com.eignex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest

/**
 * TestKit harness for com.eignex.cli. Covers the two things the plugin adds on top of a plain
 * multiplatform build: the generated BuildInfo a CLI reports through `--version`, and the
 * releaseAssets packaging. Nothing here compiles or links native code: the assertions are on
 * generated sources and on the task graph, so they stay runnable on any host.
 */
class CliPluginTest {

    @Test
    fun `build info lands in the main class package with project-derived defaults`(@TempDir dir: File) {
        val probe = writeProbe(dir)
        probe.build("generateCliBuildInfo")

        val generated = probe.file("$GENERATED/com/example/BuildInfo.kt")
        assertTrue(generated.isFile) { "expected generated BuildInfo at $generated" }
        val text = generated.readText()
        // Defaults: name from the project, id from the group the plugin sets, version from the
        // project version, package from the mainClass package.
        assertTrue("""const val NAME: String = "probe"""" in text) { text }
        assertTrue("""const val ID: String = "com.eignex"""" in text) { text }
        assertTrue("""const val VERSION: String = "SNAPSHOT"""" in text) { text }
    }

    @Test
    fun `build info version follows the ciVersion property`(@TempDir dir: File) {
        val probe = writeProbe(dir)
        probe.build("generateCliBuildInfo", "-PciVersion=1.2.3")

        val text = probe.file("$GENERATED/com/example/BuildInfo.kt").readText()
        assertTrue("""const val VERSION: String = "1.2.3"""" in text) { text }
    }

    @Test
    fun `explicit extension values win over the defaults and are escaped`(@TempDir dir: File) {
        val probe = writeProbe(
            dir,
            cliBlock = """
            eignexCli {
                mainClass = "com.example.MainKt"
                buildInfoPackage = "com.other.tool"
                appName = ${"\"\"\""}Pro"be${"\"\"\""}
                appId = "com.other.tool.app"
                version = "9.9.9"
            }
            """.trimIndent(),
        )
        probe.build("generateCliBuildInfo")

        // buildInfoPackage overrides the mainClass-derived package, so the file moves with it.
        val generated = probe.file("$GENERATED/com/other/tool/BuildInfo.kt")
        assertTrue(generated.isFile) { "expected generated BuildInfo at $generated" }
        val text = generated.readText()
        assertTrue(text.startsWith("package com.other.tool")) { text }
        // A quote in a user-supplied value has to be escaped, or the generated file does not parse.
        assertTrue("""const val NAME: String = "Pro\"be"""" in text) { text }
        assertTrue("""const val ID: String = "com.other.tool.app"""" in text) { text }
        assertTrue("""const val VERSION: String = "9.9.9"""" in text) { text }
    }

    @Test
    fun `build info package falls back to the group when mainClass is unset`(@TempDir dir: File) {
        val probe = writeProbe(dir, cliBlock = "eignexCli { entryPoint = \"com.example.main\" }")
        probe.build("generateCliBuildInfo")

        val generated = probe.file("$GENERATED/com/eignex/BuildInfo.kt")
        assertTrue(generated.isFile) { "expected the group-derived package, found ${probe.listGenerated()}" }
    }

    @Test
    fun `build info uses a consumer project group`(@TempDir dir: File) {
        val probe = writeProbe(
            dir,
            cliBlock = """
                group = "org.example"
                eignexCli { entryPoint = "org.example.main" }
            """.trimIndent(),
        )
        probe.build("generateCliBuildInfo")

        val generated = probe.file("$GENERATED/org/example/BuildInfo.kt")
        assertTrue(generated.isFile) { "expected the group-derived package, found ${probe.listGenerated()}" }
        assertTrue("const val ID: String = \"org.example\"" in generated.readText())
    }

    // srcDir(taskProvider) is what makes every target see BuildInfo; if that wiring is lost the
    // generator still runs standalone but no compilation depends on it.
    @Test
    fun `compiling common code depends on the build info generator`(@TempDir dir: File) {
        val output = writeProbe(dir).build("compileKotlinJvm", "--dry-run").output
        assertTrue(":generateCliBuildInfo SKIPPED" in output) {
            "expected generateCliBuildInfo in the jvm compile task graph, got:\n$output"
        }
    }

    @Test
    fun `releaseAssets is registered and pulls in the jvm distribution zip`(@TempDir dir: File) {
        val output = writeProbe(dir).build("releaseAssets", "--dry-run").output
        assertTrue(":jvmDistZip SKIPPED" in output) {
            "expected releaseAssets to depend on jvmDistZip, got:\n$output"
        }
        assertTrue(":releaseAssets SKIPPED" in output) { output }
    }

    // The one test that actually builds something. Only the JVM half: native linking needs a
    // Kotlin/Native toolchain and minutes of CI time, and the packaging code under test (asset
    // naming and the checksum file) is the same code path for both kinds of asset.
    @Test
    fun `releaseAssets packages the jvm zip under the release name with a matching checksum`(@TempDir dir: File) {
        val probe = writeProbe(dir)
        probe.build("releaseAssets", "-PciVersion=4.5.6")

        val assets = probe.file("build/release-assets")
        val zip = assets.resolve("probe-4.5.6-jvm.zip")
        assertTrue(zip.isFile) {
            "expected <name>-<version>-jvm.zip, found ${assets.list()?.toList()}"
        }
        val sums = assets.resolve("SHA256SUMS")
        assertTrue(sums.isFile) { "expected SHA256SUMS in ${assets.list()?.toList()}" }
        // `shasum -a 256 -c SHA256SUMS` has to pass on the uploaded assets, which means the
        // digest is of the copied file and the name column is the asset name, not a path.
        assertEquals("${sha256(zip)}  probe-4.5.6-jvm.zip\n", sums.readText())
    }

    @Test
    fun `release asset prefix is configurable`(@TempDir dir: File) {
        val probe = writeProbe(
            dir,
            cliBlock = """
                eignexCli {
                    mainClass = "com.example.MainKt"
                    releaseAssetPrefix = "custom-tool"
                }
            """.trimIndent(),
        )
        probe.build("releaseAssets", "-PciVersion=4.5.6")

        assertTrue(probe.file("build/release-assets/custom-tool-4.5.6-jvm.zip").isFile)
    }

    @Test
    fun `release assets can use a custom directory`(@TempDir dir: File) {
        val probe = writeProbe(
            dir,
            cliBlock = """
                eignexCli {
                    mainClass = "com.example.MainKt"
                    releaseAssetsDirectory = "custom-assets"
                }
            """.trimIndent(),
        )
        probe.build("releaseAssets", "-PciVersion=4.5.6")

        assertTrue(probe.file("custom-assets/probe-4.5.6-jvm.zip").isFile)
    }

    @Test
    fun `release assets can be disabled`(@TempDir dir: File) {
        val output = writeProbe(
            dir,
            cliBlock = "eignexCli { releaseAssetsEnabled = false }",
        ).build("tasks", "--all").output

        assertTrue("releaseAssets" !in output) { output }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    // Release binaries only; a debug link in the release graph would double the build time and
    // ship an unoptimized binary.
    @Test
    fun `releaseAssets links only host-buildable release binaries`(@TempDir dir: File) {
        val host = hostNativeTarget()
        val foreign = if (host == "linuxX64") "macosArm64" else "linuxX64"
        val probe = writeProbe(dir, targets = "jvm()\n    $host()\n    $foreign()")

        val output = probe.build("releaseAssets", "--dry-run").output
        assertTrue(":linkReleaseExecutable${host.capitalizeFirst()} SKIPPED" in output) {
            "expected the host release link task in the graph, got:\n$output"
        }
        assertFalse(":linkDebugExecutable${host.capitalizeFirst()}" in output) {
            "debug binaries are not release assets, got:\n$output"
        }
        // Cross-linking is impossible, so a foreign target must be filtered out rather than
        // failing the release build on the host that cannot produce it.
        assertFalse(":linkReleaseExecutable${foreign.capitalizeFirst()}" in output) {
            "expected the non-host target to be skipped, got:\n$output"
        }
    }

    private fun hostNativeTarget(): String =
        if (System.getProperty("os.name").startsWith("Mac")) "macosArm64" else "linuxX64"

    private fun String.capitalizeFirst() = replaceFirstChar { it.uppercase() }

    private fun GradleProbe.listGenerated(): List<String> =
        file(GENERATED).walkTopDown().filter { it.isFile }.map { it.path }.toList()

    private fun writeProbe(
        dir: File,
        targets: String = "jvm()",
        cliBlock: String = """
        eignexCli {
            mainClass = "com.example.MainKt"
            entryPoint = "com.example.main"
        }
        """.trimIndent(),
    ): GradleProbe = probe(
        dir,
        """
        plugins {
            id("com.eignex.cli")
        }
        ${writeFakePlatformRepo(dir)}
        kotlin {
            $targets
        }
        $cliBlock
        """
    ).apply {
        write("src/commonMain/kotlin/com/example/Main.kt", "internal fun main() { println(\"hi\") }\n")
    }

    private companion object {
        const val GENERATED = "build/generated/eignexCliBuildInfo/kotlin"
    }
}
