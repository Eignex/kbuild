plugins { application }
val version = providers.gradleProperty("kbuildVersion").get()
dependencies {
    implementation("org.example:library:$version")
    implementation("org.example:multiplatform:$version")
    // No KBuild platform or repository: library metadata must carry its dependency versions.
}
application { mainClass.set("Main") }
