package com.eignex

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.lang.module.ModuleDescriptor
import java.util.Properties
import java.util.jar.JarFile

/** Exercises concealed, versioned-only Kotlin classes through the published modular JAR. */
class ModularMultiReleaseTest {
    @ParameterizedTest
    @CsvSource("jvm,false", "jvm,true", "kmp,false", "kmp,true")
    fun `module package metadata covers every release`(plugin: String, versioned: Boolean, @TempDir dir: File) {
        val kmp = plugin == "kmp"
        val main = if (kmp) "jvmMain" else "main"
        val newer = if (kmp) "jvmJava25" else "java25"
        val test = if (kmp) "jvmTest" else "test"
        val jarTask = if (kmp) "jvmJar" else "jar"
        val prefix = if (kmp) "jvmTest" else "test"
        val coordinate = if (kmp) "probe-jvm" else "probe"
        val publication = if (kmp) "Jvm" else "MavenJava"
        val descriptorSource = if (versioned) "src/jvmModule/module-info.java" else "src/$main/java/module-info.java"
        val probe = probe(dir, """
            plugins { id("com.eignex.$plugin") }
            eignexBuild {
                jvmToolchain.set(25)
                jvmTarget.set(17)
                multiReleaseJvmVersion.set(25)
                jvmModuleDescriptor.set(layout.projectDirectory.file("$descriptorSource"))
                jvmModuleVersioned.set($versioned)
                packagedTestJdks.set(listOf(17, 25))
                usePlatformDependencies.set(false)
            }
            eignexPublish {
                description.set("A modular multi-release library.")
                githubRepo.set("Eignex/probe")
            }
            ${if (kmp) "kotlin { jvm() }; tasks.named<Test>(\"jvmTest\") { useJUnitPlatform() }" else ""}
            dependencies {
                "${if (kmp) "jvmTestImplementation" else "testImplementation"}"("org.junit.jupiter:junit-jupiter:6.1.3")
                "${if (kmp) "jvmTestRuntimeOnly" else "testRuntimeOnly"}"("org.junit.platform:junit-platform-launcher:6.1.3")
            }
            val runtime = configurations.named("${if (kmp) "jvmRuntimeClasspath" else "runtimeClasspath"}")
            tasks.register<Copy>("copyModuleDependencies") {
                from(runtime)
                into(layout.buildDirectory.dir("module-dependencies"))
            }
        """)
        probe.write(descriptorSource, """
            /** Library-owned module declaration. */
            module example.library {
                requires kotlin.stdlib;
                exports example.api;
            }
        """.trimIndent())
        probe.write("src/$main/kotlin/example/api/Api.kt", """
            package example.api
            /** Reports the implementation selected by the JVM. */
            public fun backend(): String = Backend.value()
            /** Portable implementation. */
            internal object Backend { fun value(): String = "portable" }
        """.trimIndent())
        probe.write("src/$newer/kotlin/example/api/Backend.kt", """
            package example.api
            import example.internal.desktop.DesktopBackend
            /** Versioned implementation. */
            internal object Backend { fun value(): String = DesktopBackend.value() }
        """.trimIndent())
        probe.write("src/$newer/kotlin/example/internal/desktop/DesktopBackend.kt", """
            package example.internal.desktop
            import java.lang.foreign.Arena
            /** New class in a concealed package, public in Kotlin's JVM bytecode. */
            internal object DesktopBackend { fun value(): String = Arena.ofConfined().use { "desktop" } }
        """.trimIndent())
        probe.write("src/$test/kotlin/example/api/PackagedTest.kt", """
            package example.api
            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Test
            internal class PackagedTest {
                @Test fun selectsBackend() {
                    assertEquals(if (Runtime.version().feature() >= 25) "desktop" else "portable", backend())
                }
            }
        """.trimIndent())
        val arguments = arrayOf(
            "${prefix}JarOnJava17", "${prefix}JarOnJava25", "copyModuleDependencies",
            "publish${publication}PublicationToLocalStagingRepository", "--configuration-cache", "-PciVersion=1.0.0",
        )
        probe.build(*arguments)
        assertTrue("Reusing configuration cache" in probe.build(*arguments).output)
        val publicationDir = probe.file("build/staging-repo/com/eignex/$coordinate/1.0.0")
        val jar = publicationDir.resolve("$coordinate-1.0.0.jar")
        val descriptorPath = if (versioned) "META-INF/versions/17/module-info.class" else "module-info.class"
        JarFile(jar).use { archive ->
            val descriptor = ModuleDescriptor.read(archive.getInputStream(archive.getJarEntry(descriptorPath)))
            assertEquals("example.library", descriptor.name())
            assertEquals(setOf("example.api", "example.internal.desktop"), descriptor.packages())
            assertEquals(setOf("example.api"), descriptor.exports().map { it.source() }.toSet())
            if (versioned) assertFalse(archive.entries().asSequence().any { it.name == "module-info.class" })
        }
        JarFile(publicationDir.resolve("$coordinate-1.0.0-sources.jar")).use { archive ->
            assertTrue(archive.getJarEntry(if (versioned) "java17/module-info.java" else "module-info.java") != null)
        }
        // The JDK advisory about concealed public bytecode on the classpath is expected; validation must succeed.
        run(dir, jdk(25).resolve("bin/jar"), "--validate", "--file", jar)

        val consumer = probe.write("consumer/src/consumer/Main.java", """
            package consumer;
            public class Main {
                public static void main(String[] args) {
                    String actual = example.api.ApiKt.backend();
                    if (!actual.equals(args[0])) throw new AssertionError(actual);
                    if (!example.api.ApiKt.class.getModule().getName().equals("example.library")) {
                        throw new AssertionError("Library is not an explicit module");
                    }
                }
            }
        """.trimIndent())
        val consumerModule = probe.write("consumer/src/module-info.java", "module consumer { requires example.library; }\n")
        val dependencies = probe.file("build/module-dependencies").listFiles().orEmpty().filter { it.extension == "jar" }
        val modulePath = (listOf(jar) + dependencies).joinToString(File.pathSeparator) { it.absolutePath }
        val output = probe.file("consumer/classes").apply { mkdirs() }
        run(dir, jdk(25).resolve("bin/javac"), "--release", "17", "--module-path", modulePath,
            "-d", output, consumerModule, consumer)
        for (version in listOf(17, 25)) {
            run(dir, jdk(version).resolve("bin/java"), "--module-path", "$modulePath${File.pathSeparator}$output",
                "--module", "consumer/consumer.Main", if (version == 17) "portable" else "desktop")
        }
        val before = jar.readBytes()
        probe.build(jarTask, "--rerun-tasks", "--configuration-cache", "-PciVersion=1.0.0")
        assertArrayEquals(before, probe.file("build/libs/$coordinate-1.0.0.jar").readBytes())
    }

    private fun jdk(version: Int): File = System.getProperty("kbuild.test.javaInstallations").split(',').map(::File).single {
        val release = Properties().apply { it.resolve("release").inputStream().use(::load) }
        release.getProperty("JAVA_VERSION").trim('"').substringBefore('.').toInt() == version
    }

    private fun run(directory: File, vararg arguments: Any): String {
        val process = ProcessBuilder(arguments.map { it.toString() }).directory(directory).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)
        return output
    }
}
