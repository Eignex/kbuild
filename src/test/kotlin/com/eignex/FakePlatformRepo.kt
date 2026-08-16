package com.eignex

import com.eignex.internal.KBUILD_VERSION
import java.io.File

/**
 * The convention plugins pin their dependencies to `com.eignex:kbuild-platform:<kbuild version>`,
 * which no test run has published anywhere. This writes a throwaway Maven repository holding an
 * empty BOM under that coordinate, so probe projects resolve (and therefore configure) without
 * the real platform having to be built and installed first.
 *
 * Returns a `repositories { maven { ... } }` snippet to paste into the probe's build script.
 */
internal fun writeFakePlatformRepo(dir: File): String {
    val repo = dir.resolve("fake-platform-repo")
    val pom = repo.resolve("com/eignex/kbuild-platform/$KBUILD_VERSION/kbuild-platform-$KBUILD_VERSION.pom")
    pom.parentFile.mkdirs()
    pom.writeText(
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.eignex</groupId>
          <artifactId>kbuild-platform</artifactId>
          <version>$KBUILD_VERSION</version>
          <packaging>pom</packaging>
        </project>
        """.trimIndent() + "\n"
    )
    return "repositories { maven { url = uri(\"${repo.toURI()}\") } }"
}
