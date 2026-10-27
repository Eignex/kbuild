plugins { application }
val version = providers.gradleProperty("kbuildVersion").get()
val multiplatformModule = if (providers.gradleProperty("pomOnly").getOrElse("false").toBoolean()) "multiplatform-jvm" else "multiplatform"
dependencies {
    implementation("org.example:library:$version")
    implementation("org.example:$multiplatformModule:$version")
    // No KBuild platform or repository: library metadata must carry its dependency versions.
}
application { mainClass.set("Main") }
