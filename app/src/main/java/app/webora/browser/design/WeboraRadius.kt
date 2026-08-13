package app.webora.browser.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corner radii, derived from Direction A's own numbers rather than chosen.
 *
 * `ADR-013` names two radii and a base unit: `999 dp` pills, `20 dp` cards, `4 dp` base spacing. The
 * steps between them are multiples of that base. This is recorded because "derived from the stated
 * base" and "picked because it looked right" are indistinguishable in the resulting numbers, and
 * only the first is reviewable — `WeboraDimensionsTest` makes the derivation an assertion.
 */
internal object WeboraRadius {
    val EXTRA_SMALL: Dp = 4.dp
    val SMALL: Dp = 8.dp
    val MEDIUM: Dp = 12.dp
    val LARGE: Dp = 20.dp

    /** Large enough that any control this rounds becomes a pill at any height it can reach. */
    val PILL: Dp = 999.dp

    val ALL: List<Dp> = listOf(EXTRA_SMALL, SMALL, MEDIUM, LARGE, PILL)
}

/** The Material shape scale, built entirely from [WeboraRadius]. Constant, so built once. */
internal val WEBORA_SHAPES: Shapes = Shapes(
    extraSmall = RoundedCornerShape(WeboraRadius.EXTRA_SMALL),
    small = RoundedCornerShape(WeboraRadius.SMALL),
    medium = RoundedCornerShape(WeboraRadius.MEDIUM),
    large = RoundedCornerShape(WeboraRadius.LARGE),
    extraLarge = RoundedCornerShape(WeboraRadius.PILL),
)
