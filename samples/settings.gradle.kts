rootProject.name = "samples"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("..")
}

include("jvm-positive", "jvm-negative-fqn")
