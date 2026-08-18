package app.webora.browser.browser

/**
 * What refreshing the current page means, as browser-owned data.
 *
 * Closed on purpose, and with no general "navigate to this URL" case: the only URL that may leave
 * here is one the browser already bound to the tab's own failed navigation. A generic case would
 * put arbitrary navigation behind a control a website's page is sitting underneath, which is the
 * shape [RendererEffect] refuses for the same reason.
 */
internal sealed interface RefreshAction {

    /** Re-fetch the page the renderer is already on, appending no history entry. */
    data object Reload : RefreshAction

    /** Re-issue the exact URL whose navigation failed. */
    data class Retry(val url: String) : RefreshAction

    /** There is no reloadable page. The control is disabled and nothing is dispatchable. */
    data object None : RefreshAction
}

/**
 * The one owner of what Refresh does, for the integrated header and the regular dock alike.
 *
 * **Why one function rather than `controller::reload` at two call sites.** `UX-021` records what the
 * alternative costs: regular chrome and the integrated trust chip each carried a verbatim copy of
 * one transport `when`, and "a re-pointed branch in one file drifted from the other with nothing
 * failing". A reload rule copied into two docks is that shape exactly. It is also the only form the
 * JVM gate can drive — `BROWSE-009` and `BROWSE-010` both found the decision worth testing trapped
 * inside a `@Composable`, and both had to lift it out before anything could assert on it.
 *
 * **Why a failed page is [RefreshAction.Retry] and not [RefreshAction.Reload].** `BROWSE-010`
 * established that after a failed navigation `WebView.getUrl()` "may be the failed URL, the
 * previously committed URL or `about:blank`" — so `WebView.reload()` there is a call whose target
 * the browser cannot name, and naming its target is the entire job of this function.
 * `BrowserErrorPage` already navigates to [BrowserLoadFailure.retryUrl] for the same tab and the
 * same URL; this reuses that answer rather than inventing a second one. `observeFailure` restricts
 * that URL to the exact observed HTTP(S) round trip, so it cannot be a scheme the browser would
 * refuse to resolve.
 *
 * **The inputs are two browser-observed values and nothing else.** No [BrowserMode], and therefore
 * no `SiteSkinConfiguration`: whether a page can be refreshed is not a thing a manifest gets an
 * opinion about, and reading the mode here is what would give it one. The parity case in
 * `PageRefreshTest` is the assertion, and making this function read `mode` is its negative control.
 */
internal fun refreshAction(state: BrowserState): RefreshAction {
    // Checked before the committed page, because a tab showing an error page still has one and the
    // failure is the more specific fact about it. A failure whose URL did not survive
    // `observeFailure`'s exact-round-trip restriction has no nameable retry target and falls
    // through to the committed page rather than to `None` — the tab has not lost what it was on.
    state.loadFailure?.retryUrl?.let { return RefreshAction.Retry(it) }
    return if (state.displayedUrl.isBlank()) RefreshAction.None else RefreshAction.Reload
}
