package app.webora.browser.siteskin

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The vertical bounds the browser gives the hub drawer's panel.
 *
 * A pair rather than a single height because the panel's actual height is decided by its content
 * between these two — `heightIn` plus the content column's `verticalScroll` wraps a short menu and
 * bounds-and-scrolls a long one, with no branch anywhere on what the site published.
 */
internal data class HubDrawerHeight(val min: Dp, val max: Dp)

/**
 * How tall the hub drawer's panel may be, given the height available to it.
 *
 * **The one input is a platform fact, and its absence of any other input is the security property.**
 * `UX-022` and `UX-025` bound what a site may put in this panel; nothing bounds how much of the
 * browser's own window that panel takes except this rule. The tempting implementation reads
 * `model.siteMenu.size` — a site-controlled number that reads as a layout detail and would produce a
 * result that looks correct for the reference integration — and that is a website deciding how much
 * of Webora's window it occupies. `headerIdentityPlacement` makes the same exclusion in the same
 * package for the same reason, and `HubDrawerHeightTest` asserts it against the *signature*, so it
 * covers every count, id, label and colour anyone could add later: none of them is a [Dp].
 *
 * **[HUB_DRAWER_MAX_FRACTION] is a dismissal guarantee, not a proportion someone liked.** A panel
 * permitted to fill the viewport leaves no scrim to tap, and `UX-026`'s outside-tap dismissal would
 * then hold for a two-row menu and quietly stop holding for the twenty `menu` entries `SPEC.md` §8
 * permits. Reserving a fraction keeps a strip of scrim reachable at every content height. It is a
 * fraction rather than a fixed inset because a constant is either too tall on a small device or
 * wasteful on a large one, and neither keeps the guarantee.
 *
 * **Font scale is deliberately not an input.** Large text makes the content taller, and the response
 * to taller content is already scrolling at the maximum. How much of its own window the browser is
 * willing to yield to a site menu does not change because the user enlarged the text — adding the
 * input would change nothing today and invite the next reader to make it change something.
 *
 * Total: every input returns usable bounds. A non-positive available height yields `(0, 0)` rather
 * than a negative bound, and the minimum is coerced under the maximum — `heightIn` throws on
 * inverted bounds, so an uncoerced minimum would crash the browser on a small enough window, a
 * failure caused by the device rather than by anything anyone did.
 */
internal fun hubDrawerHeight(available: Dp): HubDrawerHeight {
    val max = (available * HUB_DRAWER_MAX_FRACTION).coerceAtLeast(0.dp)
    return HubDrawerHeight(min = HUB_DRAWER_MIN_HEIGHT.coerceAtMost(max), max = max)
}

/**
 * The share of the available height a full panel may take.
 *
 * On the hosted Pixel 6 profile — 1080 × 2400 at 2.75, so ≈873 dp less the status and navigation
 * bars, ≈800 dp of safe height — this reserves ≈120 dp of scrim, comfortably over one 48 dp target.
 */
private const val HUB_DRAWER_MAX_FRACTION = 0.85f

/**
 * The smallest panel that still reads as a menu.
 *
 * The component's own tokens for a header and one group: `GUTTER` 20 × 2, `HUB_BRAND_SLOT_SIZE` 48,
 * `LARGE` 16, a heading ≈20, `SMALL` 8 and one `MINIMUM_TOUCH_TARGET` row — ≈180 dp. It binds only
 * when a site's items have all been projected into the dock or the window is tiny; the reference
 * integration's ≈272 dp of content clears it, which is the intended shape: the bounds are for the
 * degenerate ends and the content decides the middle.
 */
private val HUB_DRAWER_MIN_HEIGHT = 180.dp
