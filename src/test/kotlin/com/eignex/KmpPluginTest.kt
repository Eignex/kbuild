package com.eignex

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `karma config sets the mocha timeout to 120 seconds`(@TempDir dir: File) {
        val probe = kmpProbe(dir)

        val result = probe.build(WRITE_KARMA_CONFIG)

        result.assertOutcome(":$WRITE_KARMA_CONFIG", TaskOutcome.SUCCESS)
        assertEquals(
            """
            config.client = config.client || {};
            config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 120000 });
            """.trimIndent() + "\n",
            probe.file("$GENERATED_KARMA_DIR/00-eignex-mocha-timeout.js").readText()
        )
    }

    @Test
    fun `karma timeout is configurable`(@TempDir dir: File) {
        val probe = kmpProbe(dir, "eignexBuild { jsTestTimeout.set(\"45s\") }")

        probe.build(WRITE_KARMA_CONFIG)

        assertTrue("timeout: 45000" in probe.file("$GENERATED_KARMA_DIR/00-eignex-mocha-timeout.js").readText())
    }

    @Test
    fun `karma config copies the project's own entries verbatim`(@TempDir dir: File) {
        val probe = kmpProbe(dir)
        val own = "config.reporters = ['dots'];\n"
        probe.write("karma.config.d/zz-project.js", own)

        probe.build(WRITE_KARMA_CONFIG)

        // useConfigDirectory replaces the project's directory rather than adding to it, so the
        // generated one has to carry these across or they are silently dropped.
        assertEquals(own, probe.file("$GENERATED_KARMA_DIR/zz-project.js").readText())
    }

    // The Kotlin plugin attaches this itself on the current version. Asserted anyway, because the
    // property worth holding is that `check` covers the ABI, not who wired it up.
    @Test
    fun `check runs the ABI check`(@TempDir dir: File) {
        val result = kmpProbe(dir).build("check", "--dry-run")

        assertTrue(":checkKotlinAbi" in result.dryRunTasks()) {
            "expected checkKotlinAbi in the check task graph, got:\n${result.output}"
        }
    }

    @Test
    fun `ABI validation can be disabled`(@TempDir dir: File) {
        val result = kmpProbe(dir, "eignexBuild { abiValidationEnabled.set(false) }")
            .build("check", "--dry-run")

        assertTrue(":checkKotlinAbi" !in result.dryRunTasks().joinToString("\n")) {
            "ABI validation should be disabled, got:\n${result.output}"
        }
    }

    /** The task paths a `--dry-run` build reported, each printed as `:path SKIPPED`. */
    private fun BuildResult.dryRunTasks(): List<String> =
        output.lineSequence().mapNotNull { line ->
            line.trim().removeSuffix(" SKIPPED").takeIf { it != line.trim() && it.startsWith(":") }
        }.toList()

    private companion object {
        const val WRITE_KARMA_CONFIG = "writeEignexKarmaConfig"
        const val GENERATED_KARMA_DIR = "build/tmp/eignex-karma.config.d"
    }
}
