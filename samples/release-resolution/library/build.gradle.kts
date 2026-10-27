plugins { id("com.eignex.jvm") }
group = "org.example"
eignexBuild { jvmToolchain.set(25) }
eignexPublish {
    description.set("Anonymous release resolution probe.")
    githubRepo.set("Eignex/kbuild")
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.register("verifyPlatform") {
    val runtime = configurations.testRuntimeClasspath
    doLast {
        val modules = runtime.get().resolvedConfiguration.resolvedArtifacts.map { it.moduleVersion.id.name }.toSet()
        check(modules.containsAll(setOf("kotlinx-coroutines-core-jvm", "kotlinx-serialization-json-jvm", "junit-jupiter", "junit-platform-launcher")))
    }
}
