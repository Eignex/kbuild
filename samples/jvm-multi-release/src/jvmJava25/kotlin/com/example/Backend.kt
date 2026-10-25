package com.example

import com.example.internal.desktop.DesktopBackend

/** Desktop implementation loaded by Java 25 and later. */
internal object Backend {
    /** Exercises an FFM API unavailable to the portable implementation. */
    fun value(): String = DesktopBackend.value()
}
