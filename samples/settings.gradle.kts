rootProject.name = "samples"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("..")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Substitute com.eignex:kbuild-platform with the local project instead of Maven Central.
includeBuild("..")

include(
    "jvm-positive",
    "jvm-multi-release",
    "kmp-positive",
    "cli-positive",
    "jvm-negative-fqn",
    "jvm-negative-undoc",
    "jvm-negative-sentence",
    "jvm-negative-deprecated",
)
