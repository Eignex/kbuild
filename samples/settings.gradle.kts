rootProject.name = "samples"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("..")
}

include(
    "jvm-positive",
    "jvm-negative-fqn",
    "jvm-negative-undoc",
    "jvm-negative-sentence",
    "jvm-negative-deprecated",
)
