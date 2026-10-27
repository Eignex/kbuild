package org.example

/** A library whose published dependencies have resolved versions. */
class ReleaseLibrary {
    /** Resolves a runtime dependency whose version comes from the platform. */
    fun message(): String = kotlinx.coroutines.runBlocking { "hello from release" }
}
