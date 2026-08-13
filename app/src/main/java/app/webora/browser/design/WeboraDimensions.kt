package app.webora.browser.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.webora.browser.browser.MINIMUM_TOUCH_TARGET

/**
 * Spacing, on Direction A's stated 4 dp base with its 20 dp gutter.
 *
 * Every step is a multiple of [BASE], asserted rather than asserted-in-prose. `OnboardingScreen`
 * currently hard-codes `Spacer` heights; `UX-004` replaces those with these, and a scale whose steps
 * were arbitrary would be no improvement on the literals it replaces.
 */
internal object WeboraSpacing {
    val BASE: Dp = 4.dp
    val SMALL: Dp = 8.dp
    val MEDIUM: Dp = 12.dp
    val LARGE: Dp = 16.dp

    /** The screen gutter `ADR-013` names. */
    val GUTTER: Dp = 20.dp

    val ALL: List<Dp> = listOf(BASE, SMALL, MEDIUM, LARGE, GUTTER)
}

/**
 * The chrome geometry `ADR-013` specifies for Direction A.
 *
 * `UX-003` consumes these; nothing reads them yet. They live here rather than in that ticket because
 * a measurement recorded in an ADR and re-typed at a call site is a measurement with two sources.
 */
internal object WeboraChrome {
    /** The address pill. */
    val ADDRESS_HEIGHT: Dp = 52.dp

    /** The floating navigation dock. */
    val DOCK_HEIGHT: Dp = 60.dp

    /** The circular slot a dock control or the pill's trailing action occupies. */
    val SLOT_SIZE: Dp = 40.dp

    /** Icon size. `ADR-013`: "8, at 20 dp, stroke 1.9". */
    val ICON_SIZE: Dp = 20.dp

    /**
     * The accessibility minimum, re-exported rather than restated.
     *
     * `A11Y-001` owns this number and `BrowserAccessibilityTest` asserts it against Material's own
     * 40 dp button minimum. A second `48.dp` here would be a second place for it to change, and the
     * geometry layer is exactly where someone would change it to make a design fit.
     */
    val TOUCH_TARGET: Dp = MINIMUM_TOUCH_TARGET
}
