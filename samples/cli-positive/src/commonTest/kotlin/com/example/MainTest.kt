package com.example

import kotlin.test.Test
import kotlin.test.assertEquals

/** Smoke-tests the sample CLI greeting on every declared target. */
class MainTest {
    /** Verifies that [greeting] includes the given name. */
    @Test
    fun greetingIncludesName() {
        assertEquals("hello kbuild", greeting("kbuild"))
    }
}
