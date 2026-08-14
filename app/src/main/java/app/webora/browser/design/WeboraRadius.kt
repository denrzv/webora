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
 *
 * **[PILL] is a control value and belongs to no Material role** — see [WEBORA_SHAPES].
 */
internal object WeboraRadius {
    val EXTRA_SMALL: Dp = 4.dp
    val SMALL: Dp = 8.dp
    val MEDIUM: Dp = 12.dp
    val LARGE: Dp = 20.dp

    /**
     * The largest *container* radius: what a dialog, a sheet or a large card is rounded by.
     *
     * A multiple of the stated base like every step below it, and Material's own extra-large value,
     * so a Webora dialog and a system dialog do not disagree about what a dialog corner is. Kept
     * distinct from [LARGE] rather than aliased to it: a scale whose top two roles are the same
     * number invites the next reader to collapse them, and the collapse lands back at the role that
     * has to hold *something* for `AlertDialog` to read.
     */
    val EXTRA_LARGE: Dp = 28.dp

    /**
     * Large enough that any control this rounds becomes a pill at any height it can reach.
     *
     * Applied by naming it — `BrowserChrome`'s address field, identity chip and dock slots. It is
     * deliberately absent from [WEBORA_SHAPES], and `WeboraThemeTest` fails if it returns.
     */
    val PILL: Dp = 999.dp

    val ALL: List<Dp> = listOf(EXTRA_SMALL, SMALL, MEDIUM, LARGE, EXTRA_LARGE, PILL)
}

/**
 * The Material shape scale, built entirely from [WeboraRadius]. Constant, so built once.
 *
 * **Every role here is a container role, so none of them may carry [WeboraRadius.PILL].** `UX-009`
 * is what that rule costs when it is missing, and it takes four facts to see:
 * `DialogTokens.ContainerShape` is `CornerExtraLarge`, so every `AlertDialog` reads `extraLarge`;
 * `CornerBasedShape.createOutline` does not reject an over-large corner but scales adjacent pairs
 * down to fit the shorter side; a 999 dp corner therefore resolves to *half* a 280 dp dialog's
 * width; and `Surface` clips its content to that shape. The result was a stadium-shaped first-use
 * consent dialog with `Allow https://…` clipped to `ow` — `ADR-011`'s enforcement point and
 * `HARDEN-002`'s canonical origin, losing characters in a hosted frame while every semantics
 * assertion stayed green.
 *
 * A control that wants a pill names [WeboraRadius.PILL] itself. That is one more character at the
 * call site and one fewer shared default between a 52 dp field and a 500 dp dialog.
 */
internal val WEBORA_SHAPES: Shapes = Shapes(
    extraSmall = RoundedCornerShape(WeboraRadius.EXTRA_SMALL),
    small = RoundedCornerShape(WeboraRadius.SMALL),
    medium = RoundedCornerShape(WeboraRadius.MEDIUM),
    large = RoundedCornerShape(WeboraRadius.LARGE),
    extraLarge = RoundedCornerShape(WeboraRadius.EXTRA_LARGE),
)
