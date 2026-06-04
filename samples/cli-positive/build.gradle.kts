plugins {
    id("com.eignex.cli")
}

kotlin {
    jvm()
    linuxX64()
}

eignexCli {
    mainClass = "com.example.MainKt"
    entryPoint = "com.example.main"
}
