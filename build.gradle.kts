plugins {
    `kotlin-dsl`
}

group = "com.eignex"
version = "1.0.0"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.1.0")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.5")
    implementation("io.github.sgtsilvio.gradle:gradle-maven-central-publishing:0.4.1")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
}

gradlePlugin {
    plugins {
        named("jvm")     { id = "com.eignex.jvm" }
        named("kmp")     { id = "com.eignex.kmp" }
        named("publish") { id = "com.eignex.publish" }
        named("lint")    { id = "com.eignex.lint" }
    }
}
