package app.webora.browser.browser

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSessionSnapshotTest {
    @Test fun `round trip retains order selection and safe committed pages`() {
        val first = BrowserSession.fresh().updateActive { it.navigateFromHome("https://one.example/a") }
        val session = first.createTab().createTab()
            .updateActive { it.navigateFromHome("http://two.example/b") }
            .select(first.activeId)

        val restored = BrowserSessionSnapshot.restore(BrowserSessionSnapshot.from(session))

        assertEquals(session.tabs.map { it.id }, restored.tabs.map { it.id })
        assertEquals(session.activeId, restored.activeId)
        assertEquals("https://one.example/a", restored.tabs[0].state.displayedUrl)
        assertEquals(BrowserMode.Home, restored.tabs[1].state.mode)
        assertEquals("http://two.example/b", restored.tabs[2].state.displayedUrl)
    }

    @Test fun `integrated mode is never serialized and restores through regular boundary`() {
        val origin = checkNotNull(SiteOrigin.parse("https://shop.example"))
        val integrated = BrowserState(
            mode = BrowserMode.Regular(origin),
            displayedUrl = "https://shop.example/catalog",
            addressText = "attacker.test",
            isLoading = true,
            canGoBack = true,
            loadFailure = BrowserLoadFailure(LoadErrorKind.TLS, "shop.example", null),
        ).activateSiteSkin(origin, configuration())
        val session = BrowserSession.fresh().updateActive { integrated }

        val restored = BrowserSessionSnapshot.restore(BrowserSessionSnapshot.from(session)).activeTab.state

        assertEquals(BrowserMode.Regular(origin), restored.mode)
        assertEquals(restored.displayedUrl, restored.addressText)
        assertFalse(restored.isLoading)
        assertFalse(restored.canGoBack)
        assertEquals(null, restored.loadFailure)
    }

    @Test fun `unsafe duplicate and malformed entries are dropped with deterministic selection`() {
        val snapshot = BrowserSessionSnapshot(
            version = BrowserSessionSnapshot.VERSION,
            activeId = 99,
            nextId = 2,
            entries = listOf(
                BrowserTabSnapshot(1, BrowserTabKind.PAGE, "file:///secret"),
                BrowserTabSnapshot(2, BrowserTabKind.PAGE, "https://safe.example"),
                BrowserTabSnapshot(2, BrowserTabKind.HOME, null),
                BrowserTabSnapshot(-1, BrowserTabKind.HOME, null),
                BrowserTabSnapshot(3, BrowserTabKind.PAGE, "not a url"),
            ),
        )

        val restored = BrowserSessionSnapshot.restore(snapshot)

        assertEquals(listOf(2L), restored.tabs.map { it.id })
        assertEquals(2, restored.activeId)
        assertTrue(restored.activeTab.state.mode is BrowserMode.Regular)
    }

    @Test fun `unsupported version and empty valid input fall back to fresh session`() {
        val unsupported = BrowserSessionSnapshot(99, 4, 5, emptyList())
        val restored = BrowserSessionSnapshot.restore(unsupported)

        assertEquals(1, restored.tabs.size)
        assertEquals(BrowserMode.Home, restored.activeTab.state.mode)
        assertNotEquals(4, restored.activeId)
    }

    @Test fun `restoration is bounded without silently trusting ninth entry`() {
        val entries = (1L..12L).map { BrowserTabSnapshot(it, BrowserTabKind.HOME, null) }
        val restored = BrowserSessionSnapshot.restore(
            BrowserSessionSnapshot(BrowserSessionSnapshot.VERSION, 12, 13, entries),
        )

        assertEquals(BrowserSession.MAX_TABS, restored.tabs.size)
        assertEquals(1, restored.activeId)
    }

    @Test fun `oversized URL is rejected`() {
        val url = "https://example.com/" + "a".repeat(BrowserSessionSnapshot.MAX_URL_LENGTH)
        val restored = BrowserSessionSnapshot.restore(
            BrowserSessionSnapshot(
                BrowserSessionSnapshot.VERSION,
                7,
                8,
                listOf(BrowserTabSnapshot(7, BrowserTabKind.PAGE, url)),
            ),
        )

        assertEquals(BrowserMode.Home, restored.activeTab.state.mode)
    }

    private fun configuration() = SiteSkinValidator.validate(
        """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"}}""".byteInputStream(),
        "https://shop.example",
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
}
