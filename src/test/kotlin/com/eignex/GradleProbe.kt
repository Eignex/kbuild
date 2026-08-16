package com.eignex

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File

/**
 * A throwaway Gradle project the TestKit tests build against.
 *
 * Every test needs the same pieces — a settings file, a build script applying one of the
 * convention plugins, and a runner wired to the plugin-under-test classpath — so they are
 * assembled here once instead of in each test class.
 */
internal class GradleProbe(private val projectDir: File) {

    /** Writes [content] to [path], relative to the project, creating parent directories. */
    fun write(path: String, content: String): File = file(path).apply {
        parentFile.mkdirs()
        writeText(content)
    }

    /** The project-relative [path] as a file, whether or not it exists yet. */
    fun file(path: String): File = projectDir.resolve(path)

    /** Runs [args] and requires the build to succeed. */
    fun build(vararg args: String): BuildResult = runner(args).build()

    /** Runs [args] and requires the build to fail. */
    fun buildAndFail(vararg args: String): BuildResult = runner(args).buildAndFail()

    private fun runner(args: Array<out String>): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*args, "--stacktrace")
            .withPluginClasspath()
}

/** Asserts that [task] ran with [outcome], naming the task in the failure message. */
internal fun BuildResult.assertOutcome(task: String, outcome: TaskOutcome) {
    assertEquals(outcome, task(task)?.outcome, "outcome of $task in:\n$output")
}

/** A probe project named `probe` whose build script is [buildScript]. */
internal fun probe(dir: File, buildScript: String): GradleProbe = GradleProbe(dir).apply {
    write("settings.gradle.kts", "rootProject.name = \"probe\"\n")
    write("build.gradle.kts", buildScript.trimIndent() + "\n")
}

/**
 * A probe applying com.eignex.kmp with a single JVM target. Publishing is switched off so no
 * signing or credentials are needed, and one common source file gives the ABI check something
 * to look at.
 */
internal fun kmpProbe(dir: File): GradleProbe = probe(
    dir,
    """
    plugins {
        id("com.eignex.kmp")
    }
    eignexPublish { publish.set(false) }
    kotlin { jvm() }
    """
).apply {
    write("src/commonMain/kotlin/Probe.kt", "internal class Probe\n")
}

/**
 * A probe applying com.eignex.lint on top of a plain Kotlin/JVM project. The Kotlin plugin needs
 * no version: TestKit injects it along with the plugin under test.
 */
internal fun lintProbe(dir: File): GradleProbe = probe(
    dir,
    """
    plugins {
        kotlin("jvm")
        id("com.eignex.lint")
    }
    repositories { mavenCentral() }
    kotlin { jvmToolchain(21) }
    """
)
