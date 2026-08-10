package app.webora.browser.privacy

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowsingDataCleanerTest {
    @Test fun `clear waits for cookies and invokes every browsing data adapter`() = runTest {
        val calls = mutableListOf<String>()
        val cleaner = BrowsingDataCleaner(
            cookies = CookieDataCleaner { calls += "cookies"; true },
            clearWebStorage = { calls += "storage" },
            clearWebView = { calls += "webview" },
            clearManifestCache = { calls += "manifest" },
            clearConsent = { calls += "consent" },
        )

        assertTrue(cleaner.clear())
        assertEquals(listOf("cookies", "storage", "webview", "manifest", "consent"), calls)
    }

    @Test fun `no cookies to remove is still a complete clear`() = runTest {
        var remainingAdapters = 0
        val cleaner = BrowsingDataCleaner(
            cookies = CookieDataCleaner { false },
            clearWebStorage = { remainingAdapters++ },
            clearWebView = { remainingAdapters++ },
            clearManifestCache = { remainingAdapters++ },
            clearConsent = { remainingAdapters++ },
        )

        assertTrue(cleaner.clear())
        assertEquals(4, remainingAdapters)
    }

    @Test fun `adapter failure is incomplete and later adapters still run`() = runTest {
        val calls = mutableListOf<String>()
        val cleaner = BrowsingDataCleaner(
            cookies = CookieDataCleaner { true },
            clearWebStorage = { error("unavailable") },
            clearWebView = { calls += "webview" },
            clearManifestCache = { calls += "manifest" },
            clearConsent = { calls += "consent" },
        )

        assertFalse(cleaner.clear())
        assertEquals(listOf("webview", "manifest", "consent"), calls)
    }
}
