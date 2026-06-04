package com.example

/** Returns the CLI greeting for [name]. */
public fun greeting(name: String): String = "hello $name"

/** Entry point of the sample CLI. */
public fun main() {
    println(greeting("kbuild"))
}
