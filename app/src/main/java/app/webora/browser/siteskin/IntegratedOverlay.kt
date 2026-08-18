package app.webora.browser.siteskin

/**
 * Which browser-owned overlay is open over integrated chrome, or `null` for none.
 *
 * **One nullable value rather than two booleans, and the reason is recorded rather than aesthetic.**
 * `UX-022` put the SiteSkin bouquet and the SiteSkin drawer behind a single visibility flag and
 * wrote down what a second one would have cost: *"a drawer with its own flag would need all three
 * [resets] again, and the failure is silent and specific: a tab switch tears down the dock while a
 * drawer nobody reset stays composed over the next origin's page."* `UX-024` adds a third overlay,
 * so that bet would now be taken twice.
 *
 * Holding one value buys two things a pair of booleans cannot:
 *
 * - **Exclusion is structural.** A variable holds one constant, so "only one overlay at a time" is
 *   not a rule two assignments have to remember in both directions — it is what the type says.
 * - **Every reset stays where it already is.** The three that matter — tab switch, active
 *   configuration change, and a page start on the owning tab — become `= null` at their existing
 *   sites, with no new site to forget.
 *
 * A fourth overlay is a constant here and nothing else, which is the direction to be wrong in.
 */
internal enum class IntegratedOverlay {
    /** `UX-015`'s bouquet or `UX-022`'s drawer, whichever the browser selected for this site. */
    SITE_HUB,

    /** `UX-024`'s browser navigation bouquet: Back, Forward, Refresh. */
    BROWSER_NAVIGATION,
}
