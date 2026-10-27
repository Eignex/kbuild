plugins { `kotlin-dsl` }
repositories {
    gradlePluginPortal()
    mavenCentral()
}
kotlin.compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
java.targetCompatibility = JavaVersion.VERSION_21

gradlePlugin {
    plugins {
        create("githubPublish") {
            id = "com.eignex.publish.github"
            implementationClass = "com.eignex.kbuild.GitHubPublishPlugin"
        }
    }
}
