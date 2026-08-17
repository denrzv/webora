package app.webora.browser.browser

import app.webora.browser.web.WebViewEvent
import app.webora.browser.web.WebViewObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renderer events belong to the tab that produced them, not to the tab that is selected.
 *
 * Every case here asserts **both** halves: the owner changed, and the non-owner is identical to its
 * value before the event. Asserting only the owner passes under the broken implementation whenever
 * the owner happens to be the selected tab, which is most of the time — the research note records
 * that trap, and it is the reason each assertion carries its `unchanged` counterpart.
 *
 * The negative control for this file is a one-line edit in `routeRendererEvent`: replace
 * `event.tabId` with `session.activeId`.
 */
class RendererOwnershipTest {

    @Test
    fun `a background page change updates its owner and leaves the selected tab identical`() {
        val session = twoTabs()
        val background = session.tabs.first().id
        val selectedBefore = session.activeTab

        val routing = routeRendererEvent(
            session,
            RendererPageBook(),
            WebViewEvent.PageChanged(background, observation("https://background.example/", isLoading = false)),
        )

        assertEquals("https://background.example/", routing.session.tab(background)?.state?.displayedUrl)
        assertEquals(selectedBefore, routing.session.activeTab)
        assertEquals(session.activeId, routing.session.activeId)
    }

    @Test
    fun `an event from the selected tab still updates the selected tab`() {
        // The one case that must keep passing when the negative control is applied. Without it, a
        // control that fails every routing assertion cannot be told apart from a control that broke
        // the file — this is what says the control changes the addressing and nothing else.
        val session = twoTabs()
        val selected = session.activeId

        val routing = routeRendererEvent(
            session,
            RendererPageBook(),
            WebViewEvent.PageChanged(selected, observation("https://selected.example/", isLoading = false)),
        )

        assertEquals("https://selected.example/", routing.session.activeTab.state.displayedUrl)
    }

    @Test
    fun `a background failure sets only its own tab's failure and loading state`() {
        val session = twoTabs().update(twoTabs().tabs.first().id) {
            it.navigateFromHome("https://background.example/")
        }
        val background = session.tabs.first().id
        val selectedBefore = session.activeTab

        val routing = routeRendererEvent(
            session,
            RendererPageBook(),
            WebViewEvent.MainFrameFailed(background, "https://background.example/", LoadErrorKind.CONNECTION),
        )

        val failed = checkNotNull(routing.session.tab(background)).state
        assertEquals(LoadErrorKind.CONNECTION, failed.loadFailure?.kind)
        assertEquals(false, failed.isLoading)
        assertEquals(selectedBefore, routing.session.activeTab)
        assertNull(routing.session.activeTab.state.loadFailure)
    }

    @Test
    fun `an event for a closed tab changes nothing and authorizes nothing`() {
        val session = twoTabs()
        val closed = session.tabs.first().id
        val remaining = session.close(closed)
        val book = RendererPageBook().startedPage(closed)

        val routing = routeRendererEvent(
            remaining,
            book,
            WebViewEvent.PageStarted(closed, observation("https://gone.example/", isLoading = true)),
        )

        assertEquals(remaining, routing.session)
        assertEquals(book, routing.book)
        assertTrue(routing.effects.isEmpty())
    }

    @Test
    fun `a page start advances only its own generation and names itself in the discovery effect`() {
        val session = twoTabs()
        val background = session.tabs.first().id
        val selected = session.activeId
        val book = RendererPageBook().startedPage(selected).startedPage(selected)

        val routing = routeRendererEvent(
            session,
            book,
            WebViewEvent.PageStarted(background, observation("https://background.example/", isLoading = true)),
        )

        assertEquals(1L, routing.book.generation(background))
        assertEquals(2L, routing.book.generation(selected))
        assertEquals(
            listOf(RendererEffect.DiscoverManifest(background, "https://background.example/", 1L)),
            routing.effects,
        )
    }

    @Test
    fun `a completed page records once per tab, and two tabs on one URL record separately`() {
        val session = twoTabs()
        val first = session.tabs.first().id
        val second = session.tabs.last().id
        val url = "https://shared.example/page"

        val afterFirst = routeRendererEvent(session, RendererPageBook(), completed(first, url))
        val repeated = routeRendererEvent(afterFirst.session, afterFirst.book, completed(first, url))
        val afterSecond = routeRendererEvent(repeated.session, repeated.book, completed(second, url))

        assertEquals(listOf(RendererEffect.RecordVisit(first, url, TITLE)), afterFirst.effects)
        assertTrue("a repeat of the same page in the same tab is one visit", repeated.effects.isEmpty())
        assertEquals(listOf(RendererEffect.RecordVisit(second, url, TITLE)), afterSecond.effects)
    }

    @Test
    fun `an interleaved sequence leaves each tab holding exactly its own state`() {
        // A starts loading, the user selects B, A then fails and B then completes. Every one of
        // these four events is delivered while the *other* tab is the one on screen for at least
        // one of them, which is the reported reproduction.
        val start = twoTabs()
        val a = start.tabs.first().id
        val b = start.tabs.last().id

        val started = routeRendererEvent(
            start.select(a),
            RendererPageBook(),
            WebViewEvent.PageStarted(a, observation("https://a.example/", isLoading = true)),
        )
        val switched = started.session.select(b)
        val failed = routeRendererEvent(
            switched,
            started.book,
            WebViewEvent.MainFrameFailed(a, "https://a.example/", LoadErrorKind.CONNECTION),
        )
        val finished = routeRendererEvent(failed.session, failed.book, completed(b, "https://b.example/"))

        val tabA = checkNotNull(finished.session.tab(a)).state
        val tabB = checkNotNull(finished.session.tab(b)).state
        assertEquals("https://a.example/", tabA.displayedUrl)
        assertNotNull(tabA.loadFailure)
        assertEquals("https://b.example/", tabB.displayedUrl)
        assertNull("B never failed, so B must carry no failure", tabB.loadFailure)
        assertEquals(b, finished.session.activeId)
        assertEquals(1L, finished.book.generation(a))
        assertEquals(0L, finished.book.generation(b))
    }

    @Test
    fun `forgetting a closed tab drops only that tab's bookkeeping`() {
        val book = RendererPageBook()
            .startedPage(1)
            .recordedVisit(1, "https://one.example/")
            .startedPage(2)
            .recordedVisit(2, "https://two.example/")

        val forgotten = book.forget(1)

        assertEquals(0L, forgotten.generation(1))
        assertNull(forgotten.completedPage(1))
        assertEquals(1L, forgotten.generation(2))
        assertEquals("https://two.example/", forgotten.completedPage(2))
    }

    private fun twoTabs(): BrowserSession = BrowserSession.fresh().createTab()

    private fun completed(tabId: Long, url: String) =
        WebViewEvent.MainFrameCompleted(tabId, observation(url, isLoading = false), TITLE)

    private fun observation(url: String, isLoading: Boolean) = WebViewObservation(
        url = url,
        isLoading = isLoading,
        canGoBack = false,
        canGoForward = false,
    )

    private companion object {
        const val TITLE = "Observed title"
    }
}
