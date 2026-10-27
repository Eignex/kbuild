import com.eignex.kbuild.getOrCreateEignexBuildExtension
import com.eignex.kbuild.configureKbuildJvm
import com.eignex.kbuild.createKbuildPlatformConfiguration

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover")
}

val eignexBuild = project.getOrCreateEignexBuildExtension()

apply(plugin = "com.eignex.publish")
apply(plugin = "com.eignex.lint")

kotlin {
}

// Register first so withJavadocJar exposes this Dokka artifact without adding Java Javadoc.
tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    dependsOn(tasks.named("dokkaGenerate"))
    from(layout.buildDirectory.dir("dokka/html"))
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies { }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

afterEvaluate {
    configureKbuildJvm(eignexBuild)
    if (eignexBuild.useMavenCentral.get()) repositories.mavenCentral()
    kotlin {
        jvmToolchain(eignexBuild.jvmToolchain.get())
    }
    if (eignexBuild.usePlatformDependencies.get()) {
        project.createKbuildPlatformConfiguration(eignexBuild)
    }
    if (eignexBuild.useKotlinTestDependency.get()) {
        dependencies { "testImplementation"(kotlin("test")) }
    }
    if (!eignexBuild.lintEnabled.get()) {
        tasks.matching { it.name.startsWith("detekt") || it.name == "checkKdoc" }.configureEach {
            enabled = false
        }
    }
    if (!eignexBuild.koverEnabled.get()) {
        tasks.matching { it.name.startsWith("kover") }.configureEach { enabled = false }
    }
}
