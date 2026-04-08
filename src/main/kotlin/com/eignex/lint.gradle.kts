import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}

val eignexDetektConfig =
    layout.buildDirectory.file("tmp/eignex-detekt.yml").get().asFile
if (!eignexDetektConfig.exists()) {
    eignexDetektConfig.parentFile.mkdirs()
    eignexDetektConfig.writeText(
        """
        formatting:
          active: true
          NoWildcardImports:
            active: false
        style:
          WildcardImport:
            active: false
          MagicNumber:
            active: false
          ReturnCount:
            active: false
        complexity:
          active: false
    """.trimIndent()
    )
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(
        files(eignexDetektConfig),
        rootProject.files("detekt.yml").filter { it.exists() }
    )
    source.setFrom(fileTree("src") {
        include("**/*.kt")
        include("**/*.kts")
    })
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    autoCorrect = true
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
}
