package org.example

/** Exercises metadata and JVM variants through a published downstream library. */
class MultiplatformLibrary {
    /** Identifies the selected library. */
    fun message(): String = kotlinx.coroutines.runBlocking { "hello from multiplatform" }
}

/** Requires the serialization compiler plugin supplied by KBuild. */
@kotlinx.serialization.Serializable
data class SerializedMessage(val text: String)
