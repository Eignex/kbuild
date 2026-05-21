package com.eignex

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * TestKit harness that materializes a Gradle project applying com.eignex.lint and asserts
 * detekt fires the expected rule. Minimal seed — extend with one test per rule we lock in.
 */
class LintPluginRulesTest {

    @Test
    fun `UnnecessaryFullyQualifiedName fails detektMain`(@TempDir dir: File) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"probe\"\n")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("com.eignex.lint")
            }
            repositories { mavenCentral() }
            kotlin { jvmToolchain(21) }
            """.trimIndent()
        )
        val src = dir.resolve("src/main/kotlin/HasFqn.kt")
        src.parentFile.mkdirs()
        src.writeText("fun build(): List<String> = kotlin.collections.listOf(\"x\")\n")

        val result = GradleRunner.create()
            .withProjectDir(dir)
            .withArguments("detektMain", "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue("[UnnecessaryFullyQualifiedName]" in result.output) {
            "expected rule id in detekt output, got:\n${result.output}"
        }
    }
}
