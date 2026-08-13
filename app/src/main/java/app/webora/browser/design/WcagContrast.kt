package app.webora.browser.design

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The WCAG 2.2 AA thresholds both palettes are measured against.
 *
 * Shared because the browser and the website surfaces owe the user the same guarantee, and a second
 * copy of `4.5` is a second place for one of them to drift low. Tests deliberately restate the
 * numbers instead of importing them: an assertion that shares its threshold with the code under test
 * stops asserting anything the day someone lowers it.
 */
internal object WcagContrast {
    /** §1.4.3 — text and images of text. */
    const val BODY_TEXT: Double = 4.5

    /** §1.4.11 — visual information identifying a component or its state. */
    const val NON_TEXT: Double = 3.0
}

/**
 * The WCAG relative-luminance contrast ratio between two opaque colours.
 *
 * Lives here rather than beside either palette because both need it, and for two different jobs.
 * `SiteSkinTheme` runs it at runtime over colours a website supplied, because those can fail and
 * must be corrected before they reach the screen. `WeboraColorScheme` runs it in a JVM test over
 * values that cannot change after compilation — which is the stronger position, and the reason the
 * browser palette has no runtime guard: a compiled token that misses its target is a build failure
 * to fix, not an input to normalize.
 */
internal fun contrastRatio(first: Color, second: Color): Double {
    val light = max(relativeLuminance(first), relativeLuminance(second))
    val dark = min(relativeLuminance(first), relativeLuminance(second))
    return (light + CONTRAST_OFFSET) / (dark + CONTRAST_OFFSET)
}

private fun relativeLuminance(color: Color): Double {
    val channels = listOf(color.red, color.green, color.blue).map { channel ->
        val value = channel.toDouble()
        if (value <= SRGB_THRESHOLD) value / SRGB_DIVISOR
        else ((value + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_EXPONENT)
    }
    return RED_WEIGHT * channels[0] + GREEN_WEIGHT * channels[1] + BLUE_WEIGHT * channels[2]
}

private const val CONTRAST_OFFSET = 0.05
private const val SRGB_THRESHOLD = 0.04045
private const val SRGB_DIVISOR = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_EXPONENT = 2.4
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722
