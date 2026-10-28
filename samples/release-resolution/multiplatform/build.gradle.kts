plugins { id("com.eignex.kmp"); kotlin("plugin.serialization") }
group = "org.example"
kotlin {
    jvm()
    sourceSets.commonMain.dependencies {
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    }
}
eignexPublish {
    description.set("Anonymous multiplatform release resolution probe.")
    githubRepo.set("Eignex/kbuild")
}
