package com.example

import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks the backend selected by the runtime. */
internal class BackendTest {
    /** Java 17 uses portable code and Java 25 uses the FFM version. */
    @Test
    fun selectsBackend() {
        // Ordinary class-directory tests exercise the portable implementation.
        val packaged = Backend::class.java.getResource("Backend.class")!!.protocol == "jar"
        val expected = if (packaged && Runtime.version().feature() >= 25) "ffm" else "portable"
        assertEquals(expected, backend())
    }
}
