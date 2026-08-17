package app.webora.browser.web

/**
 * What a renderer host must do when it mounts a renderer that may already have a page.
 *
 * A tab that reached Home and navigated back to the *same* URL is the case that keeps this from
 * being "does `hosted` differ from `target`": nothing differs, and `navigateFromHome` has already set
 * `isLoading = true` with no navigation coming to clear it. That tab is waiting for a page, so it
 * gets one.
 *
 * The first version of this reported a **synthetic completion** for the page already on screen
 * instead. Two things were wrong with it and either was enough. It asserted an observation the
 * browser never made — every other `WebViewEvent` originates in a framework callback, and this one
 * would have been the host inferring that a page finished; a tab switched away from *mid-load* has
 * exactly this shape and would have been told its still-loading page was complete. And it wrote
 * Compose state from inside `AndroidView`'s `factory`, which runs during composition. Issuing a real
 * load costs one request in a case the user just asked to navigate, and buys genuine callbacks.
 */
internal sealed interface RendererMountAction {
    /** Issue this navigation. The renderer has no page, not this tab's page, or the tab is waiting. */
    data class Load(val url: String) : RendererMountAction

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
    hosted != target || isLoading -> RendererMountAction.Load(target)
    else -> RendererMountAction.Ready
}
