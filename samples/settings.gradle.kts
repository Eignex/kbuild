rootProject.name = "samples"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("..")
}

// Substitute com.eignex:kbuild-platform with the local project instead of Maven Central.
includeBuild("..")

include(
    "jvm-positive",
    "cli-positive",
    "jvm-negative-fqn",
    "jvm-negative-undoc",
    "jvm-negative-sentence",
    "jvm-negative-deprecated",
)
