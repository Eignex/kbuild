package com.example

import java.lang.foreign.Arena

/** Desktop implementation loaded by Java 25 and later. */
internal object Backend {
    /** Exercises an FFM API unavailable to the portable implementation. */
    fun value(): String = Arena.ofConfined().use { "ffm" }
}
