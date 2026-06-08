plugins {
    id("com.eignex.kmp")
}

eignexPublish {
    description.set("KMP positive sample exercising the Node pin via wasmWasi.")
    githubRepo.set("Eignex/kbuild")
}

kotlin {
    jvm()
    // wasmWasi exercises the Node pin: its Kotlin 2.3 output embeds exnref types that only
    // instantiate on a Node whose V8 has stable exception handling.
    wasmWasi { nodejs() }
}
