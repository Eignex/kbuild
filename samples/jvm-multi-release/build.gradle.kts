plugins {
    id("com.eignex.kmp")
}

version = "1.0-SNAPSHOT"

eignexBuild {
    usePlatformDependencies.set(false)
    jvmToolchain.set(25)
    jvmTarget.set(17)
    multiReleaseJvmVersion.set(25)
    packagedTestJdks.set(listOf(17, 25))
}

eignexPublish {
    description.set("One JVM artifact with a portable baseline and a desktop FFM implementation.")
    githubRepo.set("Eignex/kbuild")
}

kotlin {
    jvm()
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}

// Makes runtime dependencies available to the independent Android consumer.
val runtimeClasspath = configurations.named("jvmRuntimeClasspath")
tasks.register<Copy>("copyAndroidLibraries") {
    from(runtimeClasspath)
    into(layout.buildDirectory.dir("android-libraries"))
}
