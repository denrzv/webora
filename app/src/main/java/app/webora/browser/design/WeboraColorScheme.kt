package app.webora.browser.design

import androidx.compose.ui.graphics.Color

/**
 * The closed set of colour roles Webora's own surfaces may use.
 *
 * The mirror image of `SiteSkinColorScheme`, and deliberately so. That type takes a website's
 * trusted configuration and guards every pair it forms at runtime, because a website's colours can
 * fail. This one **takes nothing**: there is no `from(configuration)`, no factory with an argument,
 * and no path by which a manifest value could become a browser token. `ADR-013` records that rule —
 * Webora's palette is compiled into the app — and `BrowserTokenIsolationTest` is what keeps it true
 * now that a browser palette exists at all.
 *
 * The absence of a runtime guard is the stronger position, not a missing feature. A compiled token
 * that misses its contrast target is a build failure to fix; correcting it at runtime would let it
 * ship, silently repaired, with nothing red anywhere. `WeboraColorSchemeTest` measures every pair
 * these roles can form, and asserts by reflection that no role escapes measurement.
 *
 * Values are Direction A, "Soft instrument", from `ADR-013` and the token block at
 * `docs/design/directions/index.html:437-455`.
 */
@Suppress("LongParameterList")
internal data class WeboraColorScheme(
    /** The app background. */
    val ground: Color,
    /** Cards and sheets that sit above [ground]. */
    val surface: Color,
    /** The address pill and the navigation dock. */
    val chrome: Color,
    /** Tonal containers, including the browser-owned identity chip. */
    val container: Color,
    /** The single accent. Direction A has one; inventing a second would be amending the decision. */
    val primary: Color,
    /** Body text. */
    val ink: Color,
    /** Secondary text, and the boundary colour for controls that need 3:1. */
    val muted: Color,
    /** Text and icons on [chrome]. */
    val onChrome: Color,
    /** Text and icons on [container]. */
    val onContainer: Color,
    /** Text and icons on [primary]. */
    val onPrimary: Color,
    /** Transport security, secure, on [container]. */
    val secure: Color,
    /** Transport security, not secure, on [container]. */
    val notSecure: Color,
    /**
     * Hairline separators.
     *
     * Named `divider` rather than `outline`, which is what the direction's CSS calls it. Material
     * uses `outline` for boundaries that must reach 3:1 and `outlineVariant` for decorative
     * hairlines; Direction A uses tonal containers *instead of* outlines, so this value is a
     * hairline at 1.44:1 against ground. Calling it `outline` would invite exactly the mapping that
     * puts it where 3:1 is required — [muted] does that job.
     */
    val divider: Color,
    /** The scrim behind a modal. The direction's own darkest value, in both projections. */
    val scrim: Color,
)

/** The two compiled projections. Which one applies is the user's choice, never a website's. */
internal object WeboraColors {
    val LIGHT = WeboraColorScheme(
        ground = Color(0xFFF7F6F3),
        surface = Color(0xFFFFFFFF),
        chrome = Color(0xFFD3EBEA),
        container = Color(0xFFB4E5E3),
        primary = Color(0xFF00696E),
        ink = Color(0xFF191C1C),
        muted = Color(0xFF45504F),
        onChrome = Color(0xFF00201F),
        onContainer = Color(0xFF00201F),
        onPrimary = Color(0xFFFFFFFF),
        secure = Color(0xFF0F5132),
        notSecure = Color(0xFF7A1B14),
        divider = Color(0xFFC6D2D1),
        scrim = Color(0xFF0E1414),
    )

    val DARK = WeboraColorScheme(
        ground = Color(0xFF0E1414),
        surface = Color(0xFF171F1F),
        chrome = Color(0xFF1F2C2C),
        container = Color(0xFF1F2C2C),
        primary = Color(0xFF5CDBD8),
        ink = Color(0xFFDEE4E3),
        muted = Color(0xFFB2BEBD),
        onChrome = Color(0xFFB4E5E3),
        onContainer = Color(0xFFB4E5E3),
        onPrimary = Color(0xFF003736),
        secure = Color(0xFF6DD58C),
        notSecure = Color(0xFFFFB4AB),
        divider = Color(0xFF2C3838),
        scrim = Color(0xFF0E1414),
    )

    /**
     * The projection matching the user's current system theme.
     *
     * A `Boolean` rather than a read of the framework, for the reason `SiteSkinTheme.scheme` gives:
     * the choice is browser-owned and comes from the platform, and a function that reads the
     * platform itself cannot be asked the other question in a test.
     */
    fun scheme(darkTheme: Boolean): WeboraColorScheme = if (darkTheme) DARK else LIGHT
}
