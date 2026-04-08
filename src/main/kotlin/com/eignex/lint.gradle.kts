import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}

detekt {
    buildUponDefaultConfig = true
    config.from(rootProject.files("detekt.yml").filter { it.exists() })
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
