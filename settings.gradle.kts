rootProject.name = "kbuild"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include("kbuild-platform")
project(":kbuild-platform").projectDir = file("platform")
