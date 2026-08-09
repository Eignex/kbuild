package com.eignex.kbuild

import java.io.File
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTestFramework
import org.jetbrains.kotlin.gradle.targets.js.testing.karma.KotlinKarma
import org.jetbrains.kotlin.gradle.targets.js.testing.mocha.KotlinMocha

/**
 * Raises the per-test timeout on whichever framework a js test task ends up using.
 *
 * A real class rather than a lambda in the convention script: the action is stored on the task, and
 * a lambda declared in a precompiled script plugin captures the script object, which the
 * configuration cache cannot serialize.
 *
 * @property timeout the mocha timeout, in the `120s` form the Kotlin plugin expects.
 * @property karmaConfigDirectory directory of `.js` files karma appends to its generated config.
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
