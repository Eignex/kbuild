package com.example

/** A trivial sample class used to smoke-test the kbuild lint plugin. */
public class Sample {
    /** Returns a greeting using a deliberately fully-qualified [kotlin.text.uppercase] call. */
    public fun greet(name: String): String = kotlin.text.StringBuilder()
        .append("hello, ")
        .append(name)
        .toString()
}
