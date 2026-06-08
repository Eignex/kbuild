@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    id("com.eignex.kmp")
}

eignexPublish {
    description.set("KMP positive sample exercising the Node pin via wasmWasi.")
    githubRepo.set("Eignex/kbuild")
}

kotlin {
    jvm()
    // wasmWasi guards the node pin: its exnref output needs a stable-exnref V8.
    wasmWasi { nodejs() }
}
