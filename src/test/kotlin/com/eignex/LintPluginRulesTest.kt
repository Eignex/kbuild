package com.eignex

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * TestKit harness that materializes a Gradle project applying com.eignex.lint and asserts
 * detekt fires the expected rule. One test per rule we want to lock in.
 */
class LintPluginRulesTest {

    @Test
    fun `UnnecessaryFullyQualifiedName fails detektMain`(@TempDir dir: File) {
        writeSource(dir, "HasFqn.kt", "fun build(): List<String> = kotlin.collections.listOf(\"x\")\n")
        runAndAssertRule(dir, task = "detektMain", rule = "UnnecessaryFullyQualifiedName")
    }

    @Test
    fun `UndocumentedPublicClass fails detekt`(@TempDir dir: File) {
        writeSource(dir, "Undocumented.kt", "public class Undocumented\n")
        runAndAssertRule(dir, task = "detekt", rule = "UndocumentedPublicClass")
    }

    @Test
    fun `EndOfSentenceFormat fails detekt`(@TempDir dir: File) {
        writeSource(
            dir,
            "BadSentence.kt",
            """
            /** A KDoc without a terminating period */
            public class BadSentence
            """.trimIndent() + "\n"
        )
        runAndAssertRule(dir, task = "detekt", rule = "EndOfSentenceFormat")
    }

    @Test
    fun `DeprecatedBlockTag fails detekt`(@TempDir dir: File) {
        writeSource(
            dir,
            "UsesDeprecatedTag.kt",
            """
            /**
             * Uses the deprecated KDoc tag.
             *
             * @deprecated use something else.
             */
            public class UsesDeprecatedTag
            """.trimIndent() + "\n"
        )
        runAndAssertRule(dir, task = "detekt", rule = "DeprecatedBlockTag")
    }

    private fun writeSource(dir: File, name: String, body: String) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"probe\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.20"
                id("com.eignex.lint")
            }
            repositories { mavenCentral() }
            kotlin { jvmToolchain(21) }
            """.trimIndent()
        )
        val src = dir.resolve("src/main/kotlin/$name")
        src.parentFile.mkdirs()
        src.writeText(body)
    }

    private fun runAndAssertRule(dir: File, task: String, rule: String) {
        val result: BuildResult = GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(task, "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()
        assertTrue("[$rule]" in result.output) {
            "expected [$rule] in detekt output, got:\n${result.output}"
        }
    }
}
