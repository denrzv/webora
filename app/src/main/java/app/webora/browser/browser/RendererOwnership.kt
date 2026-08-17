package app.webora.browser.browser

import app.webora.browser.web.WebViewEvent

/**
 * Browser-owned page bookkeeping, one entry per live tab.
 *
 * Immutable because [routeRendererEvent] must be pure: a mutable book passed into a pure function is
 * a pure function with a hidden output, and the whole point of this layer is that the gate can drive
 * it. It replaces two bare `mutableMap`s that lived in `BrowserScreen`'s `remember` — neither was
 * Compose state, and one of them (`completedPages`) had no removal, so its entries outlived the tab.
 *
 * Both values are page-scoped and deliberately unpersisted. `BROWSE-006` requires a restored tab to
 * re-traverse discovery, consent and exact-origin activation, so carrying a generation across a
 * process death would be a way to skip that.
 */
internal data class RendererPageBook(
    private val generations: Map<Long, Long> = emptyMap(),
    private val completed: Map<Long, String> = emptyMap(),
) {
    /** The navigation generation `tabId` is currently on. A tab that has never started a page is 0. */
    fun generation(tabId: Long): Long = generations[tabId] ?: 0L

    /** The canonical URL last recorded as a completed visit for `tabId`, if any. */
    fun completedPage(tabId: Long): String? = completed[tabId]

    /** Advances only `tabId`'s generation and forgets only its completed page. */
    fun startedPage(tabId: Long): RendererPageBook = RendererPageBook(
        generations = generations + (tabId to generation(tabId) + 1),
        completed = completed - tabId,
    )

    fun recordedVisit(tabId: Long, url: String): RendererPageBook =
        copy(completed = completed + (tabId to url))

    /** Drops a closed tab's bookkeeping. Called from the one place that destroys its renderer. */
    fun forget(tabId: Long): RendererPageBook =
        RendererPageBook(generations - tabId, completed - tabId)
}

/**
 * Page-scoped work a renderer event authorizes, always naming the tab that authorized it.
 *
 * Closed on purpose. An effect model with a "run this lambda" case would put arbitrary work back
 * behind an id nobody checks, which is the defect this file exists to remove.
 */
internal sealed interface RendererEffect {
    /** Start SiteSkin discovery for `url` on behalf of `tabId` at `generation`. */
    data class DiscoverManifest(val tabId: Long, val url: String, val generation: Long) : RendererEffect

    /** Record one completed main-frame visit for `tabId`. */
    data class RecordVisit(val tabId: Long, val url: String, val title: String?) : RendererEffect
}

internal data class RendererRouting(
    val session: BrowserSession,
    val book: RendererPageBook,
    val effects: List<RendererEffect>,
)

/**
 * Applies one renderer event to the tab that produced it, whichever tab is selected.
 *
 * `BROWSE-006` wrote the rule — *"UI and WebView callbacks must carry a tab id rather than updating
 * whichever tab happens to be active when they arrive"* — and `BrowserSession.update(id)` already
 * implements it. What was missing is a caller that obeys it: `BrowserScreen` resolved `activeTabId`
 * at delivery time, so a late `onPageFinished` or `onReceivedError` from a background renderer
 * rewrote the selected tab's URL, address text, loading flag, history capability and `loadFailure` —
 * and therefore its `SecurityPresentation`, which is one origin's identity presented over another
 * origin's page.
 *
 * The one thing that matters here is that [WebViewEvent.tabId] is the only id read. Nothing in this
 * function may consult [BrowserSession.activeId]; the negative control for every test below is to
 * make it do so.
 *
 * An event whose owner has already been closed changes nothing and emits nothing. `update` ignores
 * an unknown id on its own, and the effects have to agree with it rather than firing for a tab that
 * no longer exists.
 */
internal fun routeRendererEvent(
    session: BrowserSession,
    book: RendererPageBook,
    event: WebViewEvent,
): RendererRouting {
    val tabId = event.tabId
    if (session.tab(tabId) == null) return RendererRouting(session, book, emptyList())
    val updated = session.update(tabId) { it.observe(event.toBrowserObservation()) }
    return when (event) {
        is WebViewEvent.PageStarted -> {
            val started = book.startedPage(tabId)
            RendererRouting(
                session = updated,
                book = started,
                effects = listOf(
                    RendererEffect.DiscoverManifest(tabId, event.observation.url, started.generation(tabId)),
                ),
            )
        }
        is WebViewEvent.MainFrameCompleted -> completedRouting(updated, book, event)
        is WebViewEvent.PageChanged, is WebViewEvent.MainFrameFailed ->
            RendererRouting(updated, book, emptyList())
    }
}

/**
 * A completed main frame records at most one visit, and the suppression is per tab.
 *
 * The same URL completing twice in one tab is one visit; the same URL completing in two tabs is two,
 * because they are two different browsing contexts. Keying the suppression on the tab is what makes
 * those two cases differ — a single "last completed page" would silently drop the second tab's.
 */
private fun completedRouting(
    session: BrowserSession,
    book: RendererPageBook,
    event: WebViewEvent.MainFrameCompleted,
): RendererRouting {
    val tabId = event.tabId
    val canonical = canonicalBrowsingUrl(event.observation.url)
    if (canonical == null || book.completedPage(tabId) == canonical) {
        return RendererRouting(session, book, emptyList())
    }
    return RendererRouting(
        session = session,
        book = book.recordedVisit(tabId, canonical),
        effects = listOf(RendererEffect.RecordVisit(tabId, canonical, event.title)),
    )
}

internal fun WebViewEvent.toBrowserObservation(): BrowserObservation = when (this) {
    is WebViewEvent.MainFrameFailed -> BrowserObservation.PageFailed(url, kind)
    is WebViewEvent.PageStarted -> BrowserObservation.PageStarted(
        observation.url,
        observation.canGoBack,
        observation.canGoForward,
    )
    is WebViewEvent.PageChanged -> BrowserObservation.Page(
        observation.url,
        observation.isLoading,
        observation.canGoBack,
        observation.canGoForward,
    )
    is WebViewEvent.MainFrameCompleted -> BrowserObservation.Page(
        url = observation.url,
        isLoading = false,
        canGoBack = observation.canGoBack,
        canGoForward = observation.canGoForward,
    )
}
