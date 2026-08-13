package app.webora.browser.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The five type sizes `ADR-013` specifies for Direction A: `28 / 22 / 15 / 13 / 12`.
 *
 * Five sizes cover fifteen Material roles. The mapping below is by rank rather than by taste, and
 * `WeboraThemeTest` asserts every role's size is a member of this set — so a sixteenth value cannot
 * arrive quietly, whether by hand or by a Compose upgrade adding a role.
 *
 * The display roles are capped at [DISPLAY] rather than given Material's 45–57 sp. Webora draws
 * nothing that big, and inventing a size to fill a role would be adding to a decided scale.
 */
internal object WeboraTypeScale {
    val DISPLAY: TextUnit = 28.sp
    val TITLE: TextUnit = 22.sp
    val BODY: TextUnit = 15.sp
    val LABEL: TextUnit = 13.sp
    val CAPTION: TextUnit = 12.sp

    val ALL: List<TextUnit> = listOf(DISPLAY, TITLE, BODY, LABEL, CAPTION)
}

/**
 * The Material type scale, built entirely from [WeboraTypeScale].
 *
 * Every one of the fifteen roles is supplied. A role left out would fall back to Material's own
 * baseline, which is the default this ticket exists to remove — and the fallback is invisible at the
 * call site, since `MaterialTheme.typography.titleMedium` reads the same either way.
 *
 * `FontFamily.Default` is Roboto on Android, which is what `ADR-013` names. It is stated rather than
 * omitted so the intent survives a reader who does not know the platform default.
 */
internal fun weboraTypography(): Typography = Typography(
    displayLarge = style(WeboraTypeScale.DISPLAY, FontWeight.SemiBold),
    displayMedium = style(WeboraTypeScale.DISPLAY, FontWeight.SemiBold),
    displaySmall = style(WeboraTypeScale.DISPLAY, FontWeight.SemiBold),
    headlineLarge = style(WeboraTypeScale.DISPLAY, FontWeight.SemiBold),
    headlineMedium = style(WeboraTypeScale.DISPLAY, FontWeight.SemiBold),
    headlineSmall = style(WeboraTypeScale.TITLE, FontWeight.SemiBold),
    titleLarge = style(WeboraTypeScale.TITLE, FontWeight.SemiBold),
    titleMedium = style(WeboraTypeScale.BODY, FontWeight.Medium),
    titleSmall = style(WeboraTypeScale.LABEL, FontWeight.Medium),
    bodyLarge = style(WeboraTypeScale.BODY, FontWeight.Normal),
    bodyMedium = style(WeboraTypeScale.LABEL, FontWeight.Normal),
    bodySmall = style(WeboraTypeScale.CAPTION, FontWeight.Normal),
    labelLarge = style(WeboraTypeScale.LABEL, FontWeight.Medium),
    labelMedium = style(WeboraTypeScale.CAPTION, FontWeight.Medium),
    labelSmall = style(WeboraTypeScale.CAPTION, FontWeight.Medium),
)

/**
 * Line height is derived from the size rather than chosen per role.
 *
 * A per-role line height is a second table to keep in step with the first, and nothing would notice
 * when it fell out of step — the text would simply be a little wrong somewhere.
 */
private fun style(size: TextUnit, weight: FontWeight): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size,
    lineHeight = size * LINE_HEIGHT_RATIO,
    fontWeight = weight,
)

private const val LINE_HEIGHT_RATIO = 1.35f
