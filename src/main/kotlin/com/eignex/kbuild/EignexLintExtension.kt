package com.eignex.kbuild

import org.gradle.api.provider.Property

/** Configuration for the `com.eignex.lint` convention plugin. */
abstract class EignexLintExtension {
    /** Whether lint tasks are enabled. Defaults to `true`. */
    abstract val enabled: Property<Boolean>

    /** Whether to include the built-in Eignex Detekt rules. Defaults to `true`. */
    abstract val useEignexConfig: Property<Boolean>

    /** Whether Detekt should autocorrect. Defaults to `true`. */
    abstract val autoCorrect: Property<Boolean>

    /** Whether HTML reports are generated. Defaults to `true`. */
    abstract val htmlReport: Property<Boolean>

    /** Whether SARIF reports are generated. Defaults to `false`. */
    abstract val sarifReport: Property<Boolean>
}
