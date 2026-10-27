rootProject.name = "kbuild"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include("kbuild-platform")
project(":kbuild-platform").projectDir = file("platform")
