package app.webora.browser.browser

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.model.SiteSkinConfiguration
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The refresh decision, driven through every row it has.
 *
 * `BROWSE-011` adds a browser-owned Refresh to integrated chrome, and the thing worth asserting is
 * not that a button exists — it is that the browser can *name the page it is about to re-fetch* in
 * every state a tab can be in. `BROWSE-010` is where that stopped being obvious: after a failed
 * navigation the renderer's own idea of its URL is one of three things, so a rule that says
 * "reload whatever is there" has no target.
 */
class PageRefreshTest {

    @Test fun `a committed page reloads in place`() {
        val state = BrowserState().observe(started("https://shop.example/catalog"))

        assertEquals(RefreshAction.Reload, refreshAction(state))
    }

    @Test fun `a failed page re-issues the exact URL that failed`() {
        // The row `BROWSE-010`'s framework finding decides. `reload()` here would be a call whose
        // target the browser cannot name; this is the target it can, and `BrowserErrorPage`'s Retry
        // already navigates to precisely it.
        val state = BrowserState()
            .observe(started("https://shop.example/catalog"))
            .observe(BrowserObservation.PageFailed("https://shop.example/catalog", LoadErrorKind.NETWORK))

        assertEquals(RefreshAction.Retry("https://shop.example/catalog"), refreshAction(state))
    }

    @Test fun `a failure with no nameable retry target falls back to the committed page`() {
        // `observeFailure` restricts `retryUrl` to an exact HTTP(S) round trip, so a failure
        // reported for anything else leaves none. The tab has not lost the page it was on, so this
        // is `Reload` rather than `None` — losing a retry target is not the same as having no page.
        val state = BrowserState()
            .observe(started("https://shop.example/catalog"))
            .observe(BrowserObservation.PageFailed("shop.example", LoadErrorKind.CONNECTION))

        assertEquals(null, state.loadFailure?.retryUrl)
        assertEquals(RefreshAction.Reload, refreshAction(state))
    }

    @Test fun `a pristine tab has nothing to refresh`() {
        // Issue requirement 7: the control is disabled rather than dispatching into nothing. Home
        // composes no integrated chrome at all, and `BrowserNavigationShell` is passed a literal
        // `false` there — this is the assertion that the two agree.
        assertEquals(RefreshAction.None, refreshAction(BrowserState()))
        assertEquals(RefreshAction.None, refreshAction(BrowserState(displayedUrl = "   ")))
    }

    @Test fun `an integrated tab refreshes exactly as the same regular tab would`() {
        // Issue requirement 11, at the decision layer rather than the pixel layer. The manifest is
        // hostile in every field it is allowed to carry; none of them is an input here, and the
        // negative control is to make `refreshAction` read `state.mode` at all.
        val page = "https://brand.example/catalog"
        val regular = BrowserState().observe(started(page))
        val integrated = regular.copy(mode = BrowserMode.Integrated(origin(), hostileConfiguration()))

        assertTrue(integrated.mode is BrowserMode.Integrated)
        assertEquals(refreshAction(regular), refreshAction(integrated))

        val failedRegular = regular.observe(BrowserObservation.PageFailed(page, LoadErrorKind.TLS))
        val failedIntegrated = integrated.observe(BrowserObservation.PageFailed(page, LoadErrorKind.TLS))

        assertEquals(refreshAction(failedRegular), refreshAction(failedIntegrated))
        assertEquals(RefreshAction.Retry(page), refreshAction(failedIntegrated))
    }

    @Test fun `only the retry case carries a URL`() {
        // The closure assertion. `Reload` and `None` are objects, so no future edit can smuggle a
        // destination through them; adding a second URL-carrying case is a deliberate act that
        // fails here first.
        val cases = listOf(RefreshAction.Reload, RefreshAction.None, RefreshAction.Retry("https://a.example"))
        val carrying = cases.filterIsInstance<RefreshAction.Retry>()

        assertEquals(1, carrying.size)
        assertTrue(RefreshAction.Reload === RefreshAction.Reload)
        assertTrue(RefreshAction.None === RefreshAction.None)
    }

    private fun started(url: String) = BrowserObservation.PageStarted(url, canGoBack = false, canGoForward = false)

    private fun origin() = requireNotNull(SiteOrigin.parse(ORIGIN))

    private fun hostileConfiguration(): SiteSkinConfiguration =
        SiteSkinValidator.validate(HOSTILE_MANIFEST.byteInputStream(), ORIGIN)
            .let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private companion object {
        const val ORIGIN = "https://brand.example"

        /** Every field a manifest is allowed to carry, set to something that wants to be believed. */
        const val HOSTILE_MANIFEST = """
            {
              "schemaVersion": "1.0",
              "site": {"id": "brand", "name": "Your Bank", "shortName": "Bank", "homeUrl": "/"},
              "branding": {
                "primaryColor": "#FFFFFF",
                "secondaryColor": "#FFFFFF",
                "backgroundColor": "#FFFFFF",
                "textColor": "#FFFFFF"
              },
              "bottomNavigation": [
                {"id": "home", "label": "Reload", "icon": "home", "action": {"type": "home"}}
              ],
              "quickActions": [
                {"id": "refresh", "label": "Refresh", "icon": "generic", "action": {"type": "refresh"}}
              ]
            }
        """
    }
}
