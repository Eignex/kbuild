package com.eignex.kbuild

import java.io.File
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTestFramework
import org.jetbrains.kotlin.gradle.targets.js.testing.karma.KotlinKarma
import org.jetbrains.kotlin.gradle.targets.js.testing.mocha.KotlinMocha

/**
 * Applies the JS test timeout to whichever framework a test task uses: Mocha takes it directly,
 * Karma only through a config directory. The timeout is the `120s` form the Kotlin plugin expects.
 *
 * A class, not a lambda: the action is stored on the task, and a lambda in a precompiled script
 * plugin captures the script object, which the configuration cache cannot serialize.
 */
internal class JsTestFrameworkTimeout(
    private val timeout: String,
    private val karmaConfigDirectory: File,
) : Action<KotlinJsTestFramework> {
    override fun execute(framework: KotlinJsTestFramework) {
        when (framework) {
            is KotlinMocha -> framework.timeout = timeout
            is KotlinKarma -> framework.useConfigDirectory(karmaConfigDirectory)
            else -> Unit
        }
    }
}
