package app.webora.browser.browser

import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSessionTest {
    @Test fun `fresh session has one selected Home tab`() {
        val session = BrowserSession.fresh()

        assertEquals(1, session.tabs.size)
        assertEquals(session.tabs.single(), session.activeTab)
        assertEquals(BrowserMode.Home, session.activeTab.state.mode)
    }

    @Test fun `create appends and selects an independently identified Home tab`() {
        val initial = BrowserSession.fresh().updateActive { it.navigateFromHome("https://one.example") }
        val created = initial.createTab()

        assertEquals(2, created.tabs.size)
        assertNotEquals(created.tabs.first().id, created.tabs.last().id)
        assertEquals(created.tabs.last(), created.activeTab)
        assertEquals(BrowserMode.Home, created.activeTab.state.mode)
        assertEquals("https://one.example", created.tabs.first().state.displayedUrl)
    }

    @Test fun `selection changes no tab state`() {
        val first = BrowserSession.fresh().updateActive { it.navigateFromHome("https://one.example") }
        val second = first.createTab().updateActive { it.navigateFromHome("https://two.example") }

        val selected = second.select(first.tabs.first().id)

        assertEquals(first.tabs.first().id, selected.activeId)
        assertEquals(second.tabs, selected.tabs)
        assertFalse(selected.activeTab.state.canGoBack)
    }

    @Test fun `addressed update cannot mutate another tab`() {
        val session = BrowserSession.fresh().createTab()
        val first = session.tabs.first()
        val second = session.tabs.last()

        val updated = session.update(first.id) { it.navigateFromHome("https://first.example") }

        assertEquals("https://first.example", updated.tab(first.id)?.state?.displayedUrl)
        assertEquals(second, updated.tab(second.id))
        assertEquals(second.id, updated.activeId)
    }

    @Test fun `late background observation updates its owner not the selected tab`() {
        val session = BrowserSession.fresh().createTab()
        val backgroundId = session.tabs.first().id
        val selectedBefore = session.activeTab

        val updated = session.update(backgroundId) {
            it.observe(BrowserObservation.Page("https://background.example", false, true, false))
        }

        assertEquals(selectedBefore, updated.activeTab)
        assertEquals("https://background.example", updated.tab(backgroundId)?.state?.displayedUrl)
        assertTrue(updated.tab(backgroundId)?.state?.canGoBack == true)
    }

    @Test fun `closing active chooses following then preceding neighbour`() {
        val three = BrowserSession.fresh().createTab().createTab()
        val first = three.tabs[0].id
        val middle = three.tabs[1].id
        val last = three.tabs[2].id

        val afterMiddle = three.select(middle).close(middle)
        val afterLast = three.close(last)

        assertEquals(last, afterMiddle.activeId)
        assertEquals(middle, afterLast.activeId)
        assertEquals(first, three.select(first).close(last).activeId)
    }

    @Test fun `closing inactive preserves active and closing final replaces it with fresh Home`() {
        val two = BrowserSession.fresh().createTab()
        val active = two.activeId
        assertEquals(active, two.close(two.tabs.first().id).activeId)

        val only = BrowserSession.fresh().updateActive { it.navigateFromHome("https://gone.example") }
        val replacement = only.close(only.activeId)
        assertEquals(1, replacement.tabs.size)
        assertEquals(BrowserMode.Home, replacement.activeTab.state.mode)
        assertNotEquals(only.activeId, replacement.activeId)
    }

    @Test fun `cap refuses a ninth tab without eviction or selection change`() {
        val capped = generateSequence(BrowserSession.fresh()) { it.createTab() }
            .take(BrowserSession.MAX_TABS)
            .last()
        val refused = capped.createTab()

        assertEquals(BrowserSession.MAX_TABS, capped.tabs.size)
        assertEquals(capped, refused)
        assertFalse(refused.canCreateTab)
    }

    @Test fun `different tab modes and navigation capabilities remain independent`() {
        val origin = checkNotNull(SiteOrigin.parse("https://regular.example"))
        val firstState = BrowserState(
            mode = BrowserMode.Regular(origin),
            displayedUrl = "https://regular.example",
            canGoBack = true,
        )
        val session = BrowserSession.fresh().updateActive { firstState }.createTab()

        assertTrue(session.tabs.first().state.mode is BrowserMode.Regular)
        assertTrue(session.tabs.first().state.canGoBack)
        assertEquals(BrowserMode.Home, session.activeTab.state.mode)
        assertFalse(session.activeTab.state.canGoBack)
    }
}
