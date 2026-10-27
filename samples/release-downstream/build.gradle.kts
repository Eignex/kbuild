plugins { application }
val version = providers.gradleProperty("kbuildVersion").get()
dependencies {
    implementation("org.example:library:$version")
    implementation("org.example:multiplatform:$version")
    // No explicit platform here: its constraints must arrive through the published libraries.
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-serialization-json")
    runtimeOnly("org.junit.jupiter:junit-jupiter")
}
application { mainClass.set("Main") }
