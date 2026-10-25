@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    id("com.eignex.kmp")
}

eignexPublish {
    description.set("KMP positive sample exercising JVM and WASI runtimes.")
    githubRepo.set("Eignex/kbuild")
}

kotlin {
    jvm()
    // Use a standalone WASI runtime rather than Node's experimental WASI adapter.
    wasmWasi {
        // KGP 2.4.21's environment checker still requires Node to be configured for WASI.
        nodejs()
        wasmtime()
    }
}

// Wasmtime executes the tests; Node's WASI adapter still has experimental status.
tasks.named("wasmWasiNodeTest") { enabled = false }
