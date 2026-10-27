package com.eignex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.util.jar.JarFile

/** Builds real mixed-release artifacts and executes them through the JVM's JAR class loader. */
class JvmBaselineTest {
    @ParameterizedTest
    @ValueSource(strings = ["jvm", "kmp", "cli"])
    fun `default compilation and test toolchains use Java 17`(plugin: String, @TempDir dir: File) {
        val multiplatform = plugin != "jvm"
        val main = if (multiplatform) "jvmMain" else "main"
        val probe = probe(dir, """
            plugins { id("com.eignex.$plugin") }
            eignexBuild { usePlatformDependencies.set(false) }
            ${if (plugin != "cli") "eignexPublish { publish.set(false) }" else ""}
            ${if (multiplatform) "kotlin { jvm() }" else ""}
            tasks.register("verifyDefaultJvm") {
                doLast {
                    tasks.withType<JavaCompile>().forEach {
                        check(it.javaCompiler.get().metadata.languageVersion.asInt() == 17)
                        check(it.options.release.get() == 17)
                    }
                    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().forEach {
                        check(it.compilerOptions.jvmTarget.get().target == "17")
                    }
                    tasks.withType<Test>().forEach {
                        check(it.javaLauncher.get().metadata.languageVersion.asInt() == 17)
                    }
                    tasks.withType<dev.detekt.gradle.Detekt>().forEach {
                        check(it.jvmTarget.get() == "17")
                    }
                }
            }
        """)
        probe.write("src/$main/kotlin/example/KotlinBaseline.kt", "package example\ninternal class KotlinBaseline\n")
        probe.write("src/$main/java/example/JavaBaseline.java", "package example; final class JavaBaseline {}\n")
        probe.build(if (multiplatform) "jvmJar" else "jar", "verifyDefaultJvm")
        val jarName = if (multiplatform) "probe-jvm-SNAPSHOT.jar" else "probe-SNAPSHOT.jar"
        JarFile(probe.file("build/libs/$jarName")).use { jar ->
            for (name in listOf("KotlinBaseline", "JavaBaseline")) {
                val header = jar.getInputStream(jar.getJarEntry("example/$name.class")).use { it.readNBytes(8) }
                assertEquals(61, ((header[6].toInt() and 255) shl 8) or (header[7].toInt() and 255), name)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["jvm", "kmp"])
    fun `one published artifact selects the implementation on each JDK`(plugin: String, @TempDir dir: File) {
        val kmp = plugin == "kmp"
        val main = if (kmp) "jvmMain" else "main"
        val versioned = if (kmp) "jvmJava25" else "java25"
        val test = if (kmp) "jvmTest" else "test"
        val prefix = if (kmp) "jvm" else ""
        val publication = if (kmp) "Jvm" else "MavenJava"
        val probe = probe(dir, """
            plugins { id("com.eignex.$plugin") }
            eignexBuild {
                jvmToolchain.set(25)
                jvmTarget.set(17)
                multiReleaseJvmVersion.set(25)
                packagedTestJdks.set(listOf(17, 25))
                usePlatformDependencies.set(false)
            }
            eignexPublish {
                description.set("Mixed release probe.")
                githubRepo.set("Eignex/probe")
            }
            ${if (kmp) "kotlin { jvm() }; tasks.named<Test>(\"jvmTest\") { useJUnitPlatform() }" else ""}
            dependencies {
                "${if (kmp) "jvmTestImplementation" else "testImplementation"}"("org.junit.jupiter:junit-jupiter:6.1.3")
                "${if (kmp) "jvmTestRuntimeOnly" else "testRuntimeOnly"}"("org.junit.platform:junit-platform-launcher:6.1.3")
            }
        """)
        probe.write("src/$main/kotlin/example/Api.kt", """
            package example
            /** Reports the selected backend. */
            public fun backend(): String = Backend.value()
            /** Portable implementation. */
            internal object Backend {
                /** Identifies the portable implementation. */
                fun value(): String = "portable"
            }
        """.trimIndent())
        probe.write("src/$main/java/example/BaseJava.java", """
            package example;
            public final class BaseJava { public static String value() { return "base"; } }
        """.trimIndent())
        probe.write("src/$versioned/kotlin/example/Backend.kt", """
            package example
            import java.lang.foreign.Arena
            /** Desktop implementation. */
            internal object Backend {
                /** Exercises a desktop-only JDK API. */
                fun value(): String = Arena.ofConfined().use { "ffm" }
            }
        """.trimIndent())
        probe.write("src/$versioned/java/example/DesktopJava.java", """
            package example;
            import java.lang.foreign.Arena;
            final class DesktopJava {
                public static String value() { try (var arena = Arena.ofConfined()) { return "desktop"; } }
            }
        """.trimIndent())
        probe.write("src/$test/kotlin/example/BackendTest.kt", """
            package example
            import org.junit.jupiter.api.Test
            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Assertions.assertTrue
            internal class BackendTest {
                @Test fun selectsBackend() {
                    assertTrue(ApiMarker::class.java.getResource("ApiMarker.class")!!.toString().startsWith("jar:"))
                    assertEquals(if (Runtime.version().feature() >= 25) "ffm" else "portable", backend())
                    assertEquals("base", BaseJava.value())
                }
            }
        """.trimIndent())
        probe.write("src/$main/kotlin/example/ApiMarker.kt", "package example\n/** JAR location marker. */\ninternal class ApiMarker\n")
        val tasks = arrayOf(
            "${prefix}${if (kmp) "Test" else "test"}JarOnJava17",
            "${prefix}${if (kmp) "Test" else "test"}JarOnJava25",
            "publish${publication}PublicationToLocalStagingRepository",
            "--configuration-cache",
            "-PciVersion=1.0.0",
        )
        probe.build(*tasks)
        val second = probe.build(*tasks)
        assertTrue("Reusing configuration cache" in second.output) { second.output }
        val coordinate = if (kmp) "probe-jvm" else "probe"
        val repo = probe.file("build/staging-repo/com/eignex/$coordinate/1.0.0")
        JarFile(repo.resolve("$coordinate-1.0.0.jar")).use { jar ->
            assertEquals("true", jar.manifest.mainAttributes.getValue("Multi-Release"))
            fun major(path: String): Int = jar.getInputStream(jar.getJarEntry(path)).use { stream ->
                val header = stream.readNBytes(8)
                ((header[6].toInt() and 255) shl 8) or (header[7].toInt() and 255)
            }
            assertEquals(61, major("example/ApiKt.class"))
            assertEquals(61, major("example/BaseJava.class"))
            assertEquals(69, major("META-INF/versions/25/example/Backend.class"))
            assertEquals(69, major("META-INF/versions/25/example/DesktopJava.class"))
            assertFalse(jar.entries().asSequence().any { it.name.startsWith("META-INF/versions/25/META-INF/") })
        }
        val metadata = repo.resolve("$coordinate-1.0.0.module").readText()
        assertTrue("\"org.gradle.jvm.version\": 17" in metadata) { metadata }
        assertFalse("\"org.gradle.jvm.version\": 25" in metadata) { metadata }
        JarFile(repo.resolve("$coordinate-1.0.0-sources.jar")).use { jar ->
            assertTrue(jar.getJarEntry("java25/example/Backend.kt") != null)
            assertTrue(jar.getJarEntry("java25/example/DesktopJava.java") != null)
        }
        for (jdk in listOf(17, 25)) {
            val name = "${prefix}${if (kmp) "Test" else "test"}JarOnJava$jdk"
            val result = probe.file("build/test-results/$name/TEST-example.BackendTest.xml").readText()
            assertTrue("tests=\"1\" skipped=\"0\" failures=\"0\" errors=\"0\"" in result) { result }
        }
    }

    @Test
    fun `an explicit per-compilation target survives the baseline`(@TempDir dir: File) {
        val probe = kmpProbe(dir, """
            eignexBuild {
                jvmToolchain.set(25)
                jvmTarget.set(17)
                usePlatformDependencies.set(false)
            }
            kotlin {
                jvm {
                    compilations.create("desktop") {
                        compileTaskProvider.configure {
                            compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
                        }
                    }
                }
            }
        """)
        probe.write("src/jvmDesktop/kotlin/Desktop.kt", "internal class Desktop\n")
        probe.build("compileDesktopKotlinJvm", "compileKotlinJvm")
        fun major(path: String) = probe.file(path).readBytes()[7].toInt()
        assertEquals(61, major("build/classes/kotlin/jvm/main/Probe.class"))
        assertEquals(69, major("build/classes/kotlin/jvm/desktop/Desktop.class"))
    }

    @Test
    fun `invalid version sections fail before compilation`(@TempDir dir: File) {
        val probe = kmpProbe(dir, "eignexBuild { jvmTarget.set(17); multiReleaseJvmVersion.set(17) }")
        assertTrue("multiReleaseJvmVersion must" in probe.buildAndFail("tasks").output)
    }
}
