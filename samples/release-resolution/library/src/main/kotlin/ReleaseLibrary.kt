package org.example

/** A library published with a transitive KBuild platform dependency. */
class ReleaseLibrary {
    /** Resolves a runtime dependency whose version comes from the platform. */
    fun message(): String = kotlinx.coroutines.runBlocking { "hello from release" }
}
