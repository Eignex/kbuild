package com.example

/** Deliberately uses a fully-qualified call to trigger UnnecessaryFullyQualifiedName. */
public class HasFqn {
    /** Builds a list using an unnecessary FQN. */
    public fun build(): List<String> = kotlin.collections.listOf("hello")
}
