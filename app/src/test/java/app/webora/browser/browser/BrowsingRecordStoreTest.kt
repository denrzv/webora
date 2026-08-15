package app.webora.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowsingRecordStoreTest {
    @Test fun `visits canonicalize full URL and sanitize untrusted title`() {
        val store = store(clock = { 42L })

        store.recordVisit("HTTPS://Example.COM:443/a/../shop?q=1#offer", "  Your\nBank\u202E  ")

        val visit = store.history().single()
        assertEquals("https://example.com/shop?q=1", visit.url)
        assertEquals("https://example.com", visit.origin)
        assertEquals("Your Bank", visit.title)
        assertEquals(42L, visit.visitedAtMillis)
    }

    @Test fun `unsafe or credentialed URLs never become records`() {
        val store = store()

        listOf(
            "javascript:alert(1)", "file:///tmp/a", "https://user@example.com/a",
            "//example.com/a", "not a url",
        ).forEach { store.recordVisit(it, "Page") }

        assertTrue(store.history().isEmpty())
    }

    @Test fun `recents deduplicate without erasing visit history`() {
        var now = 0L
        val store = store(clock = { ++now })
        store.recordVisit("https://one.example/a", "First")
        store.recordVisit("https://two.example/", "Second")
        store.recordVisit("https://one.example/a#again", "Newest")

        assertEquals(3, store.history().size)
        assertEquals(listOf("Newest", "Second"), store.recentSites().map(BrowsingRecord::title))
    }

    @Test fun `history and favourites enforce independent bounds`() {
        var now = 0L
        val store = store(clock = { ++now })
        repeat(220) { store.recordVisit("https://example.com/$it", "Page $it") }
        repeat(120) { store.addFavourite("https://saved.example/$it", "Saved $it") }

        assertEquals(BrowsingRecordStore.MAX_HISTORY, store.history().size)
        assertEquals(BrowsingRecordStore.MAX_FAVOURITES, store.favourites().size)
        assertEquals(BrowsingRecordStore.MAX_RECENTS, store.recentSites().size)
    }

    @Test fun `favourites use canonical URL identity and survive recreation`() {
        val preferences = MemoryBrowsingRecordPreferences()
        val first = BrowsingRecordStore(preferences, clock = { 9L })
        assertTrue(first.addFavourite("https://EXAMPLE.com:443/path#one", "Original"))
        assertTrue(first.addFavourite("https://example.com/path#two", "Updated"))

        val restored = BrowsingRecordStore(preferences)
        assertEquals(1, restored.favourites().size)
        assertEquals("https://example.com/path", restored.favourites().single().url)
        assertEquals("Updated", restored.favourites().single().title)
        assertTrue(restored.isFavourite("https://example.com/path"))
        assertTrue(restored.removeFavourite("https://example.com:443/path#x"))
        assertFalse(restored.isFavourite("https://example.com/path"))
    }

    @Test fun `corrupt persisted entries are dropped independently`() {
        val preferences = MemoryBrowsingRecordPreferences(
            history = "1\nnot-base64\n${BrowsingRecordCodec.encode(record("https://safe.example"))}",
            favourites = "99\n${BrowsingRecordCodec.encode(record("https://ignored.example"))}",
        )

        val store = BrowsingRecordStore(preferences)

        assertEquals(listOf("https://safe.example/"), store.history().map(BrowsingRecord::url))
        assertTrue(store.favourites().isEmpty())
    }

    @Test fun `blank title falls back to browser observed host and long title is bounded`() {
        val store = store()
        store.recordVisit("https://shop.example/path", "\u0000\u202E")
        store.addFavourite("https://shop.example/other", "x".repeat(500))

        assertEquals("shop.example", store.history().single().title)
        assertEquals(BrowsingRecordStore.MAX_TITLE_LENGTH, store.favourites().single().title.length)
        assertNull(canonicalBrowsingUrl("https://example.com/${"x".repeat(5000)}"))
    }

    @Test fun `clearing history deliberately retains favourites`() {
        val store = store()
        store.recordVisit("https://visited.example", "Visited")
        store.addFavourite("https://saved.example", "Saved")

        store.clearHistory()

        assertTrue(store.history().isEmpty())
        assertEquals("https://saved.example/", store.favourites().single().url)
    }

    private fun store(clock: () -> Long = { 1L }) =
        BrowsingRecordStore(MemoryBrowsingRecordPreferences(), clock)

    private fun record(url: String) = BrowsingRecord(url, "https://safe.example", "Safe", 1L, 1L)
}

private class MemoryBrowsingRecordPreferences(
    private var history: String? = null,
    private var favourites: String? = null,
) : BrowsingRecordPreferences {
    override fun history(): String? = history
    override fun favourites(): String? = favourites
    override fun saveHistory(value: String) { history = value }
    override fun saveFavourites(value: String) { favourites = value }
}
