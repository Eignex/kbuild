plugins {
    kotlin("jvm") version "2.4.21"
    id("com.eignex.lint")
}

repositories { mavenCentral() }

kotlin { jvmToolchain(21) }
