package com.eignex.kbuild

import org.gradle.api.provider.Property

abstract class EignexCliExtension {
    /** Fully qualified JVM main class, e.g. "com.example.MainKt". */
    abstract val mainClass: Property<String>

    /** Kotlin/Native entry point function, e.g. "com.example.main". Defaults to a root-package main. */
    abstract val entryPoint: Property<String>
}
