package com.example

/** Reports which implementation the JVM selected from the multi-release JAR. */
public fun backend(): String = Backend.value()

/** Portable implementation usable on Java 17 and Android API 24. */
internal object Backend {
    /** Identifies the portable backend. */
    @Suppress("FunctionOnlyReturningConstant") // Must match the desktop implementation method.
    fun value(): String = "portable"
}
