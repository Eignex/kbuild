package com.eignex

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * TestKit harness for com.eignex.jvm. The plugin's whole job is composition — it pulls in publish
 * and lint, wires the sources/javadoc jars a Central release needs, and pins the test framework —
 * so the tests here assert that each of those survived, not how the composition is written.
 */
class JvmPluginTest {

    // Central rejects a release without sources and javadoc jars, and an empty javadoc jar passes
    // that check while being useless, hence the dokka dependency.
    @Test
    fun `publishing pulls in the sources jar and a dokka-built javadoc jar`(@TempDir dir: File) {
        writeProbe(dir)
        val output = runProbe(dir, "publishMavenJavaPublicationToLocalStagingRepository", "--dry-run")
        assertTrue(":sourcesJar SKIPPED" in output) { "expected a sources jar, got:\n$output" }
        assertTrue(":javadocJar SKIPPED" in output) { "expected a javadoc jar, got:\n$output" }
        assertTrue(":dokkaGenerate SKIPPED" in output) {
            "expected the javadoc jar to be built from dokka, got:\n$output"
        }
    }

    @Test
    fun `check runs detekt and the tests`(@TempDir dir: File) {
        writeProbe(dir)
        val output = runProbe(dir, "check", "--dry-run")
        // com.eignex.lint is applied by this plugin, so a jvm module gets the rules for free.
        assertTrue(":detekt SKIPPED" in output) { "expected detekt in the check graph, got:\n$output" }
        assertTrue(":test SKIPPED" in output) { output }
    }

    @Test
    fun `the platform bom is on the compile and test classpaths`(@TempDir dir: File) {
        writeProbe(dir)
        val compile = runProbe(dir, "dependencies", "--configuration", "compileClasspath")
        assertTrue("com.eignex:kbuild-platform" in compile) { compile }
        val test = runProbe(dir, "dependencies", "--configuration", "testCompileClasspath")
        assertTrue("com.eignex:kbuild-platform" in test) { test }
    }

    // The only test in this class that runs a real build. Gradle's default runner is JUnit 4, so
    // without useJUnitPlatform a Jupiter test is simply never discovered — a failure mode that is
    // invisible to any configuration-time assertion.
    @Test
    fun `the test task discovers jupiter tests`(@TempDir dir: File) {
        writeProbe(
            dir,
            // Version pinned here because the probe resolves against a stub platform BOM; the real
            // com.eignex:kbuild-platform constrains junit for consumers.
            extraDependencies = """
            dependencies {
                "testImplementation"("org.junit.jupiter:junit-jupiter:6.1.2")
                "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:6.1.2")
            }
            """.trimIndent(),
        )
        val test = dir.resolve("src/test/kotlin/com/example/SampleTest.kt")
        test.parentFile.mkdirs()
        test.writeText(
            """
            package com.example

            import org.junit.jupiter.api.Test
            import kotlin.test.assertEquals

            internal class SampleTest {
                @Test
                fun runs() = assertEquals(2, greeting().length)
            }
            """.trimIndent() + "\n"
        )

        runProbe(dir, "test")

        // The result XML, not the log: with JUnit 4 the task still succeeds having discovered
        // nothing, and the class name shows up in compiler output either way.
        val results = dir.resolve("build/test-results/test/TEST-com.example.SampleTest.xml")
        assertTrue(results.isFile) {
            "expected the jupiter test to be executed, found ${dir.resolve("build/test-results/test").list()?.toList()}"
        }
        assertTrue("""tests="1" skipped="0" failures="0" errors="0"""" in results.readText()) {
            results.readText()
        }
    }

    private fun writeProbe(dir: File, extraDependencies: String = "") {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"probe\"\n")
        val platformRepo = writeFakePlatformRepo(dir)
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.eignex.jvm")
            }
            $platformRepo
            eignexPublish {
                description.set("A probe module.")
                githubRepo.set("Eignex/probe")
            }
            $extraDependencies
            """.trimIndent() + "\n"
        )
        val src = dir.resolve("src/main/kotlin/com/example/Sample.kt")
        src.parentFile.mkdirs()
        // Documented and internal: com.eignex.lint's KDoc rules apply to this source too.
        src.writeText("package com.example\n\n/** Returns a greeting. */\ninternal fun greeting(): String = \"hi\"\n")
    }

    private fun runProbe(dir: File, vararg args: String): String =
        GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(*args, "--stacktrace")
            .withPluginClasspath()
            .build()
            .output
}
