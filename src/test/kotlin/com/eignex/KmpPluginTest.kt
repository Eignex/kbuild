package com.eignex

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * TestKit harness for com.eignex.kmp. Covers behaviour that would otherwise regress in silence: a
 * karma timeout that stops reaching browser tests reintroduces the flake it was added for, and an
 * ABI check missing from `check` simply never runs.
 */
class KmpPluginTest {

    @Test
    fun `karma config carries the mocha timeout`(@TempDir dir: File) {
        writeProbe(dir)
        runProbe(dir, "writeEignexKarmaConfig")

        val generated = dir.resolve("build/tmp/eignex-karma.config.d/00-eignex-mocha-timeout.js")
        assertTrue(generated.isFile) { "expected a generated karma config at $generated" }
        assertTrue("timeout: 120000" in generated.readText()) {
            "expected the mocha timeout in the generated config, got:\n${generated.readText()}"
        }
    }

    @Test
    fun `karma config keeps the project's own entries`(@TempDir dir: File) {
        writeProbe(dir)
        val own = dir.resolve("karma.config.d/zz-project.js")
        own.parentFile.mkdirs()
        own.writeText("config.reporters = ['dots'];\n")

        runProbe(dir, "writeEignexKarmaConfig")

        // useConfigDirectory replaces the project's directory rather than adding to it, so the
        // generated one has to carry these across or they are silently dropped.
        val copied = dir.resolve("build/tmp/eignex-karma.config.d/zz-project.js")
        assertTrue(copied.isFile) { "expected the project's own karma config to be copied to $copied" }
        assertTrue("config.reporters" in copied.readText()) {
            "expected the copied file to keep its contents, got:\n${copied.readText()}"
        }
    }

    // The Kotlin plugin attaches this itself on the current version. Asserted anyway, because the
    // property worth holding is that `check` covers the ABI, not who wired it up.
    @Test
    fun `check runs the ABI check`(@TempDir dir: File) {
        writeProbe(dir)
        val result = runProbe(dir, "check", "--dry-run")
        assertTrue("checkKotlinAbi" in result) {
            "expected checkKotlinAbi in the check task graph, got:\n$result"
        }
    }

    private fun writeProbe(dir: File) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"probe\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.eignex.kmp")
            }
            eignexPublish { publish.set(false) }
            kotlin { jvm() }
            """.trimIndent() + "\n"
        )
        val src = dir.resolve("src/commonMain/kotlin/Probe.kt")
        src.parentFile.mkdirs()
        src.writeText("internal class Probe\n")
    }

    private fun runProbe(dir: File, vararg args: String): String =
        GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(*args, "--stacktrace")
            .withPluginClasspath()
            .build()
            .output
}
