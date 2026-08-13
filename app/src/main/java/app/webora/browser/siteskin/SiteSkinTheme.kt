package app.webora.browser.siteskin

import androidx.compose.ui.graphics.Color
import app.webora.browser.design.WcagContrast
import app.webora.browser.design.contrastRatio
import dev.siteskin.core.model.SiteSkinConfiguration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The closed light and dark colour projections available to SiteSkin-owned surfaces. */
internal data class SiteSkinTheme(
    val light: SiteSkinColorScheme,
    val dark: SiteSkinColorScheme,
) {
    companion object {
        fun from(configuration: SiteSkinConfiguration): SiteSkinTheme {
            val branding = configuration.branding
            val primary = parseColor(branding?.primaryColor ?: DEFAULT_PRIMARY)
            val secondary = parseColor(branding?.secondaryColor ?: DEFAULT_SECONDARY)
            val background = parseColor(branding?.backgroundColor ?: DEFAULT_BACKGROUND)
            val lightContent = parseColor(branding?.textColor ?: DEFAULT_TEXT)
            val darkContent = parseColor(DARK_TEXT)
            return SiteSkinTheme(
                scheme(primary, secondary, background, lightContent),
                scheme(primary, secondary, mixOverBlack(background), darkContent),
            )
        }
    }
}

/**
 * The projection matching the user's current system theme.
 *
 * Both projections were already computed and guarded; only the light one was ever consumed, so a
 * user who selected a dark system theme — often for accessibility reasons — was served the light
 * one regardless. The choice is browser-owned and comes from the platform: a manifest supplies
 * colours, it does not get to decide whether the user's preference applies to them.
 */
internal fun SiteSkinTheme.scheme(darkTheme: Boolean): SiteSkinColorScheme = if (darkTheme) dark else light

/** Website-influenceable colour roles; browser security presentation is intentionally absent. */
internal data class SiteSkinColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val background: Color,
    val onBackground: Color,
)

private fun scheme(
    primary: Color,
    secondary: Color,
    background: Color,
    content: Color,
): SiteSkinColorScheme = SiteSkinColorScheme(
    primary = guardContainer(primary, content, UI_CONTRAST),
    onPrimary = content,
    secondary = guardContainer(secondary, content, UI_CONTRAST),
    onSecondary = content,
    background = guardContainer(background, content, BODY_CONTRAST),
    onBackground = content,
)

private fun parseColor(value: String): Color = Color(
    color = value.removePrefix("#").toLong(HEX_RADIX) or OPAQUE_ALPHA,
)

private fun mixOverBlack(color: Color): Color = Color(
    red = color.red * DARK_SURFACE_FRACTION,
    green = color.green * DARK_SURFACE_FRACTION,
    blue = color.blue * DARK_SURFACE_FRACTION,
)

private fun guardContainer(container: Color, foreground: Color, target: Double): Color {
    if (contrastRatio(container, foreground) >= target) return container
    val towardWhite = contrastRatio(Color.White, foreground) > contrastRatio(Color.Black, foreground)
    var channels = container.channels()
    repeat(MAX_CHANNEL) {
        channels = channels.map { channel ->
            if (towardWhite) min(MAX_CHANNEL, channel + 1) else max(MIN_CHANNEL, channel - 1)
        }
        val candidate = channels.toColor()
        if (contrastRatio(candidate, foreground) >= target) return candidate
    }
    return channels.toColor()
}

private fun Color.channels(): List<Int> = listOf(red, green, blue).map { (it * MAX_CHANNEL).roundToInt() }
private fun List<Int>.toColor(): Color = Color(first(), get(1), get(2))

private const val DEFAULT_PRIMARY = "#3F51B5"
private const val DEFAULT_SECONDARY = "#5C6BC0"
private const val DEFAULT_BACKGROUND = "#FFFFFF"
private const val DEFAULT_TEXT = "#1B1B1F"
private const val DARK_TEXT = "#FFFFFF"
private const val DARK_SURFACE_FRACTION = 0.2f
private const val BODY_CONTRAST = WcagContrast.BODY_TEXT
private const val UI_CONTRAST = WcagContrast.NON_TEXT
private const val MIN_CHANNEL = 0
private const val MAX_CHANNEL = 255
private const val HEX_RADIX = 16
private const val OPAQUE_ALPHA = 0xFF000000L
