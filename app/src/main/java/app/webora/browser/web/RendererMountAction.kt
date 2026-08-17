package app.webora.browser.web

/**
 * What a renderer host must do when it mounts a renderer that may already have a page.
 *
 * Three cases, not a `Boolean`, because "the renderer is already there" is not always "nothing to
 * do": a tab that reached Home and navigated back to the *same* URL has `isLoading = true` set by
 * `navigateFromHome` and no navigation will ever arrive to clear it. Returning a bare
 * `needsLoad: Boolean` would fix the page and leave a narrower version of the same permanent
 * spinner.
 */
internal sealed interface RendererMountAction {
    /** Issue this navigation. The renderer has no page, or not this tab's page. */
    data class Load(val url: String) : RendererMountAction

    /** The page is already on screen but the tab still believes it is loading. Report completion. */
    data object Settle : RendererMountAction

    /** The renderer already holds the tab's page and the tab knows it. This is the tab-switch case. */
    data object Ready : RendererMountAction
}

/**
 * Decides a mount from browser-observed values only.
 *
 * `HardenedWebView` used to ask `existing == null` — "is this `WebView` new?" — which was equivalent
 * to "does this renderer need the page?" only while a renderer's life and its host's life coincided.
 * `BROWSE-006` broke that equivalence by retaining renderers across host disposal so live
 * back/forward history survives a tab switch, and nothing re-derived the condition. A tab that
 * returned to Home and navigated again therefore kept painting the previous page under a spinner
 * that no callback could ever clear.
 *
 * @param hosted the URL the renderer is known to be on — `BrowserWebViewController.hostedUrl`,
 *   written from browser requests and from the renderer's own reported URL. Never from page content.
 * @param target the tab's committed `displayedUrl`, produced by `AddressResolver`.
 * @param isLoading the tab's observed loading flag.
 *
 * **This is deliberately not `WebView.getUrl()`.** That reports the framework's view of the current
 * document, which after a failed navigation may be the failed URL, the previously committed URL or
 * `about:blank` — so a rule built on it re-issues the failed navigation every time the user switches
 * back to an error tab, which is the reload `BROWSE-009`'s acceptance criterion 2 forbids. It would
 * also differ from `target` on any redirect or trailing-slash normalization.
 *
 * A blank [target] is [RendererMountAction.Ready]: a tab with no committed URL has nothing to
 * request, and `loadUrl("")` is not a navigation worth issuing.
 */
internal fun rendererMountAction(
    hosted: String?,
    target: String,
    isLoading: Boolean,
): RendererMountAction = when {
    target.isEmpty() -> RendererMountAction.Ready
    hosted != target -> RendererMountAction.Load(target)
    isLoading -> RendererMountAction.Settle
    else -> RendererMountAction.Ready
}
