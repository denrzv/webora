package app.webora.browser.siteskin

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where the browser's trust chip sits in the integrated header. */
internal enum class HeaderIdentityPlacement {
    /** Beside the site's title, in the brand row — the layout at ordinary widths and scales. */
    INLINE,

    /** On a row of its own, sharing it with no site content at all. */
    OWN_ROW,
}

/**
 * Whether the brand row can still hold the trust chip beside the site's title.
 *
 * **The arithmetic, measured in `docs/research/UX-023.md` F1.** The expressive header's 20 dp
 * gutters leave 280 dp on a 320 dp host. The brand row's fixed children — the browser's leading
 * control 48, spacer 8, logo 40, spacer 12, spacer 8 — take [HEADER_FIXED_WIDTH], so the title and
 * the chip share what is left. The chip is unweighted and declared after the weighted title column,
 * so it measures first and the title absorbs any shortfall — which is why the failure mode is a
 * title of zero width rather than a chip that overflows.
 *
 * | Host | Scale | Domain | Chip needs | Budget | Result |
 * |---|---|---|---|---|---|
 * | 320 dp | 100% | `example.co.uk` (13) | 122 | 124 | [INLINE] |
 * | 320 dp | 200% | `example.co.uk` | 208 | 124 | [OWN_ROW] |
 * | 360 dp | 100% | `denrzv.github.io` (16) | 142 | 164 | [INLINE] |
 * | 360 dp | 200% | `denrzv.github.io` | 247 | 164 | [OWN_ROW] |
 *
 * **A website controls none of the three inputs.** [availableWidth] and [fontScale] are platform
 * facts; [domainLength] is the length of the registrable domain `UX-021` derives only from the
 * committed `SiteOrigin`. The two site-controlled values in that row — the manifest's title and
 * subtitle — are deliberately absent, and their absence is the security property: a rule phrased as
 * *"wrap when the title is long"* would let a website decide which row the browser's trust mark
 * lands on, which is `HARDEN-002`'s impersonation surface reached through layout instead of text.
 * The rule asks only what the **browser** must fit.
 *
 * **The estimate over-predicts on purpose.** It says ~122 dp for `example.co.uk` at 100% where
 * `UX-021` measured ~117, and ~208 at 200% where that measurement extrapolates to ~198. A wrap that
 * was not needed costs one row of height; a wrap that was needed and did not happen costs
 * `ADR-006`'s *visible* domain. Only one of those is a guarantee.
 *
 * Total by construction: every input returns a placement, and a non-positive width returns
 * [OWN_ROW] — the branch that cannot truncate.
 *
 * `SECURITY_CHIP_MAX_WIDTH` is deliberately not consulted. It is a ceiling on how wide the chip may
 * grow, not a statement about how much room it needs, and `UX-021` recorded at its declaration why
 * tuning it does not solve this.
 */
internal fun headerIdentityPlacement(
    availableWidth: Dp,
    fontScale: Float,
    domainLength: Int,
): HeaderIdentityPlacement {
    if (availableWidth <= 0.dp || fontScale <= 0f) return HeaderIdentityPlacement.OWN_ROW
    val budget = availableWidth - HEADER_FIXED_WIDTH - MINIMUM_TITLE_WIDTH
    val required = CHIP_CHROME_WIDTH + CHIP_CHARACTER_WIDTH * domainLength.coerceAtLeast(0) * fontScale
    return if (required <= budget) HeaderIdentityPlacement.INLINE else HeaderIdentityPlacement.OWN_ROW
}

/**
 * The browser's leading control 48 + 8, logo 40 + 12, and the 8 dp before the chip.
 *
 * **The first term is a footprint, not a command, and `UX-024` is why that distinction matters.**
 * `UX-023` measured it against `BrowserBack`; the slot now holds a navigation hub that opens Back,
 * Forward *and* Refresh, and the number did not move — same 48 dp `BrowserControlTile`, same
 * position. That is what let `BROWSE-011`'s separate control row be deleted without re-deriving any
 * of the table above, and `HeaderIdentityPlacementTest` passing unedited across that ticket is the
 * evidence rather than the intention.
 *
 * So: a change to the leading slot that needs this constant edited has moved the footprint, and the
 * wrap threshold has to be re-measured with it.
 */
private val HEADER_FIXED_WIDTH = 116.dp

/** What the site's name currently gets at 100% on a 320 dp host, and the floor worth keeping. */
private val MINIMUM_TITLE_WIDTH = 40.dp

/** The chip's non-text width: a 16 dp shield, a 4 dp gap and 16 dp of horizontal padding. */
private val CHIP_CHROME_WIDTH = 36.dp

/** Per character of the 12 sp Medium domain at 100% scale. Generous, per the KDoc above. */
private val CHIP_CHARACTER_WIDTH = 6.6.dp
