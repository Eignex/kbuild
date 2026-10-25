package com.example.internal.desktop

import java.lang.foreign.Arena

/** Versioned-only implementation concealed by the library's module descriptor. */
internal object DesktopBackend {
    /** Exercises an FFM API unavailable to the portable implementation. */
    fun value(): String = Arena.ofConfined().use { "ffm" }
}
