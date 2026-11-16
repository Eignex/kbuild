package com.eignex

import com.eignex.internal.KBUILD_VERSION
import groovy.json.JsonSlurper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The build platform supplies versions without becoming part of a library's public dependencies. */
class PlatformPublicationTest {
    @Test
    fun `shared KMP metadata uses the build platform over an older transitive platform`(@TempDir dir: File) {
        val repository = writeFakePlatformRepo(dir)
        val repo = dir.resolve("fake-platform-repo")
        val template = repo.walkTopDown().single { it.extension == "pom" }.readText()
        for (version in listOf("1.0.0", "2.0.0")) {
            repo.resolve("com/eignex/kbuild-platform/$version/kbuild-platform-$version.pom").apply {
                parentFile.mkdirs()
                writeText(template.replace(KBUILD_VERSION, version))
            }
        }
        repo.resolve("com/example/legacy/1.0.0/legacy-1.0.0.pom").apply {
            parentFile.mkdirs()
            writeText("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <!-- do_not_remove: published-with-gradle-metadata -->
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId><artifactId>legacy</artifactId><version>1.0.0</version>
                  <packaging>pom</packaging>
                </project>
            """.trimIndent())
        }
        repo.resolve("com/example/legacy/1.0.0/legacy-1.0.0.module").writeText("""
            {
              "formatVersion": "1.1",
              "component": { "group": "com.example", "module": "legacy", "version": "1.0.0" },
              "variants": [{
                "name": "apiElements",
                "attributes": { "org.gradle.category": "library" },
                "dependencies": [{
                  "group": "com.eignex", "module": "kbuild-platform", "version": { "requires": "1.0.0" },
                  "attributes": { "org.gradle.category": "platform" }
                }]
              }]
            }
        """.trimIndent())
        val probe = kmpProbe(dir, """
            $repository
            eignexBuild { platformVersion.set("2.0.0") }
            kotlin { sourceSets.commonMain.dependencies { implementation("com.example:legacy:1.0.0") } }
            tasks.register("verifyMetadataPlatform") {
                val metadata = configurations.named("allSourceSetsCompileDependenciesMetadata")
                val common = configurations.named("commonMainResolvableDependenciesMetadata")
                val jvm = configurations.named("jvmMainCompileClasspath")
                doLast {
                    for (configuration in listOf(metadata.get(), common.get(), jvm.get())) {
                        val result = configuration.incoming.resolutionResult
                        val unresolved = result.allDependencies.filterIsInstance<
                            org.gradle.api.artifacts.result.UnresolvedDependencyResult>()
                        check(unresolved.isEmpty()) { unresolved.map { it.failure.message } }
                        val platform = result.allComponents.singleOrNull {
                            it.moduleVersion?.name == "kbuild-platform"
                        }
                        check(platform != null) { "${'$'}{configuration.name}: missing platform" }
                        check(platform.moduleVersion?.version == "2.0.0") {
                            "${'$'}{configuration.name}: ${'$'}{platform.moduleVersion}"
                        }
                    }
                }
            }
        """)

        probe.build("verifyMetadataPlatform", "--no-configuration-cache")
    }

    @ParameterizedTest
    @ValueSource(strings = ["jvm", "kmp"])
    fun `publications carry resolved versions without the build platform`(plugin: String, @TempDir dir: File) {
        val repository = writeFakePlatformRepo(dir)
        dir.resolve("fake-platform-repo").walkTopDown().single { it.extension == "pom" }.apply {
            writeText(readText().replace("</project>", """
                <dependencyManagement><dependencies>
                  <dependency>
                    <groupId>org.jetbrains.kotlinx</groupId><artifactId>kotlinx-coroutines-bom</artifactId>
                    <version>1.11.0</version><type>pom</type><scope>import</scope>
                  </dependency>
                  <dependency>
                    <groupId>org.jetbrains.kotlinx</groupId><artifactId>kotlinx-serialization-bom</artifactId>
                    <version>1.12.0</version><type>pom</type><scope>import</scope>
                  </dependency>
                </dependencies></dependencyManagement></project>
            """.trimIndent()))
        }
        val dependencies = """
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
        """
        val probe = probe(dir, """
            plugins {
                ${if (plugin == "jvm") "`java-library`" else ""}
                id("com.eignex.$plugin")
            }
            $repository
            eignexPublish {
                description.set("Resolved publication versions.")
                githubRepo.set("Eignex/probe")
            }
            ${if (plugin == "kmp") "kotlin { jvm(); js { nodejs() }; sourceSets.commonMain.dependencies { $dependencies } }" else "dependencies { $dependencies }"}
        """)
        probe.write("src/${if (plugin == "kmp") "commonMain" else "main"}/kotlin/Library.kt", "internal class Library\n")
        probe.build("publishAllPublicationsToLocalStagingRepository", "-PciVersion=1.0.0", "--configuration-cache")

        val files = probe.file("build/staging-repo").walkTopDown().filter { it.extension in setOf("pom", "module") }.toList()
        assertTrue(files.isNotEmpty())
        for (file in files) {
            assertFalse("kbuild-platform" in file.readText(), file.path)
            if (file.extension == "pom") {
                val dependencies = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                    .getElementsByTagName("dependency")
                for (index in 0 until dependencies.length) {
                    val dependency = dependencies.item(index) as org.w3c.dom.Element
                    assertTrue(dependency.getElementsByTagName("version").item(0)?.textContent?.isNotBlank() == true, file.path)
                }
            } else {
                val module = JsonSlurper().parse(file) as Map<*, *>
                val dependencies = (module["variants"] as List<*>).flatMap { variant ->
                    val values = variant as Map<*, *>
                    (values["dependencies"] as? List<*>).orEmpty()
                }.map { it as Map<*, *> }
                for (dependency in dependencies) {
                    val version = (dependency["version"] as? Map<*, *>)?.get("requires") as? String
                    assertTrue(!version.isNullOrBlank(), "$file: $dependency")
                }
                if (dependencies.isNotEmpty()) {
                    assertTrue(dependencies.any { it["module"].toString().startsWith("kotlinx-coroutines-core") &&
                        (it["version"] as Map<*, *>)["requires"] == "1.11.0" }, file.path)
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `KMP kotlin test opt in is independent of the platform`(kotlinTest: Boolean, @TempDir dir: File) {
        val output = kmpProbe(dir, """
            eignexBuild {
                usePlatformDependencies.set(false)
                useKotlinTestDependency.set($kotlinTest)
            }
        """).build("dependencies", "--configuration", "jvmTestCompileClasspath").output
        assertFalse("kbuild-platform" in output)
        assertTrue(("org.jetbrains.kotlin:kotlin-test" in output) == kotlinTest, output)
    }
}
