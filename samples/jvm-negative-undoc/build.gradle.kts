plugins {
    kotlin("jvm") version "2.3.20"
    id("com.eignex.lint")
}

repositories { mavenCentral() }

kotlin { jvmToolchain(21) }
