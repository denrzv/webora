package app.webora.browser.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Webora's own theme, and the replacement for the bare `MaterialTheme {}` at `MainActivity`.
 *
 * [darkTheme] is a parameter with the framework read as its default rather than a read inside the
 * function, the same shape `DEVX-003` used for the inspector's availability and `SiteSkinTheme`
 * uses for its projection: a function that reads the platform itself cannot be asked the other
 * question, so a test could not tell a correct implementation from one that ignores the setting.
 *
 * The choice is browser-owned in both themes. A manifest supplies colours to
 * `SiteSkinColorScheme`; it does not decide whether the user's dark-theme preference applies, and
 * it has no path into anything on this side at all.
 */
@Composable
internal fun WeboraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = materialColorScheme(WeboraColors.scheme(darkTheme)),
        typography = weboraTypography(),
        shapes = weboraShapes(),
        content = content,
    )
}

/**
 * Material's forty-eight colour roles, every one of them assigned from one of Webora's fourteen.
 *
 * Two mechanisms keep a Material default out, and neither leans on the other. `ColorScheme`'s
 * primary constructor declares **no default values**, so omitting a role is a compile error rather
 * than a silent fallback — which `lightColorScheme()` would have been, since it defaults every
 * argument to the baseline palette this ticket exists to remove. And `WeboraThemeTest` asserts every
 * colour in the result is a value declared in [WeboraColorScheme], so a role assigned a literal, or
 * one added by a future Compose version, fails rather than shipping.
 *
 * There is no `Color(...)` literal below on purpose. Every value here has already been measured by
 * `WeboraColorSchemeTest`; a literal would be a colour that has not.
 *
 * Three mappings are worth their reasons:
 *
 * - **`outline` takes `muted`, not `divider`.** Material uses `outline` for boundaries that must
 *   reach 3:1 and `outlineVariant` for decorative hairlines. Direction A's hairline is 1.44:1
 *   against ground, correct for a separator and wrong for a control boundary.
 * - **The error family reuses the TLS pair.** Direction A has no red, and inventing one would be an
 *   unmeasured colour. `notSecure` on `container` is the browser's one flag pair and is already
 *   measured at 7.65:1 light / 8.50:1 dark; the container roles are the same pair with the roles
 *   swapped.
 * - **`secondary` and `tertiary` take `primary`.** Direction A has one accent. Giving Material two
 *   more would mean deciding two more colours, which is amending a decision rather than
 *   implementing it.
 */
@Suppress("LongMethod")
internal fun materialColorScheme(scheme: WeboraColorScheme): ColorScheme = ColorScheme(
    primary = scheme.primary,
    onPrimary = scheme.onPrimary,
    primaryContainer = scheme.container,
    onPrimaryContainer = scheme.onContainer,
    inversePrimary = scheme.container,
    secondary = scheme.primary,
    onSecondary = scheme.onPrimary,
    secondaryContainer = scheme.chrome,
    onSecondaryContainer = scheme.onChrome,
    tertiary = scheme.primary,
    onTertiary = scheme.onPrimary,
    tertiaryContainer = scheme.container,
    onTertiaryContainer = scheme.onContainer,
    background = scheme.ground,
    onBackground = scheme.ink,
    surface = scheme.surface,
    onSurface = scheme.ink,
    surfaceVariant = scheme.chrome,
    onSurfaceVariant = scheme.muted,
    surfaceTint = scheme.primary,
    inverseSurface = scheme.ink,
    inverseOnSurface = scheme.ground,
    error = scheme.notSecure,
    onError = scheme.container,
    errorContainer = scheme.container,
    onErrorContainer = scheme.notSecure,
    outline = scheme.muted,
    outlineVariant = scheme.divider,
    scrim = scheme.scrim,
    surfaceBright = scheme.surface,
    surfaceDim = scheme.chrome,
    surfaceContainer = scheme.chrome,
    surfaceContainerHigh = scheme.container,
    surfaceContainerHighest = scheme.container,
    surfaceContainerLow = scheme.surface,
    surfaceContainerLowest = scheme.ground,
    primaryFixed = scheme.container,
    primaryFixedDim = scheme.container,
    onPrimaryFixed = scheme.onContainer,
    onPrimaryFixedVariant = scheme.onContainer,
    secondaryFixed = scheme.chrome,
    secondaryFixedDim = scheme.chrome,
    onSecondaryFixed = scheme.onChrome,
    onSecondaryFixedVariant = scheme.onChrome,
    tertiaryFixed = scheme.container,
    tertiaryFixedDim = scheme.container,
    onTertiaryFixed = scheme.onContainer,
    onTertiaryFixedVariant = scheme.onContainer,
)
