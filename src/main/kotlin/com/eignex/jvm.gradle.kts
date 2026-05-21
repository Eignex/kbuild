import com.eignex.internal.KBUILD_VERSION

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover")
}

apply(plugin = "com.eignex.publish")
apply(plugin = "com.eignex.lint")

repositories { mavenCentral() }

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    "implementation"(platform("com.eignex:kbuild-platform:$KBUILD_VERSION"))
    "testImplementation"(platform("com.eignex:kbuild-platform:$KBUILD_VERSION"))
    "testImplementation"(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Jar>("javadocJar") {
    dependsOn(tasks.named("dokkaGenerate"))
    from(layout.buildDirectory.dir("dokka/html"))
}
