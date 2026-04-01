plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover")
}

apply(plugin = "com.eignex.publish")
apply(plugin = "com.eignex.lint")

repositories { mavenCentral() }

kotlin {
    jvmToolchain(21)

    // Declare targets in your build.gradle.kts:
    //   jvm()
    //   js { browser(); nodejs() }
    //   linuxX64(); macosX64(); macosArm64(); mingwX64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
