package com.eignex.kbuild

import com.sun.source.util.JavacTask
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.process.ExecOperations
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ModuleVisitor
import org.objectweb.asm.Opcodes
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import javax.inject.Inject
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

/** Compiles a library-owned descriptor and records packages from every JVM implementation. */
@CacheableTask
abstract class CompileJvmModuleDescriptor @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    /** The library's module declaration. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val descriptor: RegularFileProperty

    /** Class directories used both for module patching and the complete package inventory. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection

    /** Dependencies required by the module declaration. */
    @get:Classpath
    abstract val modulePath: ConfigurableFileCollection

    /** Bytecode release of the descriptor. */
    @get:Input
    abstract val release: Property<Int>

    /** Compiler from the configured build toolchain. */
    @get:Nested
    abstract val javaCompiler: Property<JavaCompiler>

    /** Directory containing the completed `module-info.class`. */
    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    /** Compiles the declaration and adds the deterministic ModulePackages attribute. */
    @TaskAction
    fun compileDescriptor() {
        val source = descriptor.get().asFile
        val parser = checkNotNull(ToolProvider.getSystemJavaCompiler()) { "Module descriptors require Gradle to run on a JDK" }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val moduleName = parser.getStandardFileManager(diagnostics, null, null).use { manager ->
            val task = parser.getTask(
                StringWriter(), manager, diagnostics, listOf("-proc:none"), null,
                manager.getJavaFileObjects(source),
            ) as JavacTask
            task.parse().single().module?.name?.toString()
                ?: throw GradleException("Expected a module declaration in $source: ${diagnostics.diagnostics}")
        }
        val raw = temporaryDir.resolve("compiled").apply { deleteRecursively(); mkdirs() }
        val output = ByteArrayOutputStream()
        val arguments = mutableListOf(
            "--release", release.get().toString(), "-proc:none", "-d", raw.absolutePath,
            "--patch-module", "$moduleName=${classes.asPath}",
        )
        if (!modulePath.isEmpty) arguments += listOf("--module-path", modulePath.asPath)
        arguments += source.absolutePath
        val result = exec.exec {
            executable = javaCompiler.get().executablePath.asFile.absolutePath
            args(arguments)
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) throw GradleException("Module descriptor compilation failed:\n$output")
        if (output.size() > 0) logger.lifecycle(output.toString())

        val packages = sortedSetOf<String>()
        classes.files.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "class" && it.name != "module-info.class" }.forEach {
                val path = it.relativeTo(root).invariantSeparatorsPath
                require('/' in path) { "Named modules cannot contain classes in the default package: $path" }
                packages += path.substringBeforeLast('/')
            }
        }
        val reader = ClassReader(raw.resolve("module-info.class").readBytes())
        val writer = ClassWriter(reader, 0)
        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitModule(name: String, access: Int, version: String?): ModuleVisitor =
                object : ModuleVisitor(Opcodes.ASM9, super.visitModule(name, access, version)) {
                    override fun visitPackage(packaze: String) {
                        packages += packaze
                    }

                    override fun visitEnd() {
                        packages.forEach { super.visitPackage(it) }
                        super.visitEnd()
                    }
                }
        }, 0)
        destinationDirectory.file("module-info.class").get().asFile.apply {
            parentFile.mkdirs()
            writeBytes(writer.toByteArray())
        }
    }
}
