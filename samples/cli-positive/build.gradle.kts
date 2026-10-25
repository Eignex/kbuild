import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    id("com.eignex.cli")
}

kotlin {
    jvm()
    when (HostManager.host) {
        KonanTarget.LINUX_X64 -> linuxX64()
        KonanTarget.MACOS_ARM64 -> macosArm64()
        else -> Unit
    }
}

eignexCli {
    mainClass = "com.example.MainKt"
    entryPoint = "com.example.main"
}
