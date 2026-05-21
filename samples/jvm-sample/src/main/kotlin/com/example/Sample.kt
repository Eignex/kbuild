package com.example

/** A trivial sample class used to smoke-test the kbuild lint plugin. */
public class Sample {
    /** Returns a two-element list containing a greeting and [name]. */
    public fun greet(name: String): List<String> = listOf("hello", name)
}
