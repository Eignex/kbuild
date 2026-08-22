import com.eignex.kbuild.getOrCreateEignexBuildExtension

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

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies { }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Jar>("javadocJar") {
    dependsOn(tasks.named("dokkaGenerate"))
    from(layout.buildDirectory.dir("dokka/html"))
}

afterEvaluate {
    if (eignexBuild.useMavenCentral.get()) repositories.mavenCentral()
    kotlin {
        jvmToolchain(eignexBuild.jvmToolchain.get())
    }
    if (eignexBuild.usePlatformDependencies.get()) {
        val platform = project.dependencies.platform(
            "${eignexBuild.platformGroup.get()}:${eignexBuild.platformArtifact.get()}:" +
                eignexBuild.platformVersion.get(),
        )
        dependencies {
            "implementation"(platform)
            "testImplementation"(platform)
        }
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
