package app.webora.browser.privacy

import app.webora.browser.inspector.ManifestTraceRecord
import app.webora.browser.inspector.ManifestTransportTrace
import app.webora.browser.inspector.ManifestValidationTrace
import app.webora.browser.inspector.SiteSkinTraceRecorder
import app.webora.browser.inspector.TraceCacheState
import app.webora.browser.inspector.TraceTransportOutcome
import app.webora.browser.inspector.TraceValidationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowsingDataCleanerTest {
    @Test fun `clearing browsing data drops the developer trace with the manifest cache`() = runTest {
        // Debug-only and in memory, but still per-origin state derived from browsing.
        val recorder = SiteSkinTraceRecorder()
        recorder.record(
            ManifestTraceRecord(
                origin = "https://shop.example",
                generation = 1,
                transport = ManifestTransportTrace(
                    manifestUrl = "https://shop.example/.well-known/siteskin.json",
                    outcome = TraceTransportOutcome.FETCHED,
                    cacheState = TraceCacheState.MISS,
                ),
                validation = ManifestValidationTrace(TraceValidationResult.ACCEPTED, "1.0"),
            ),
        )
        val cleaner = BrowsingDataCleaner(
            cookies = CookieDataCleaner { true },
            clearWebStorage = {},
            clearWebView = {},
            clearManifestCache = {},
            clearConsent = {},
            clearTrace = recorder::clear,
        )

        assertTrue(cleaner.clear())
        assertNull(recorder.latest("https://shop.example"))
    }

    @Test fun `clear waits for cookies and invokes every browsing data adapter`() = runTest {
        val calls = mutableListOf<String>()
        val cleaner = BrowsingDataCleaner(
            cookies = CookieDataCleaner { calls += "cookies"; true },
            clearWebStorage = { calls += "storage" },
            clearWebView = { calls += "webview" },
            clearManifestCache = { calls += "manifest" },
            clearConsent = { calls += "consent" },
            clearHistory = { calls += "history" },
            clearTrace = { calls += "trace" },
        )

        assertTrue(cleaner.clear())
        assertEquals(listOf("cookies", "storage", "webview", "manifest", "consent", "history", "trace"), calls)
    }

    @Test fun `no cookies to remove is still a complete clear`() = runTest {
        var remainingAdapters = 0
        val cleaner = BrowsingDataCleaner(
            cookies = CookieDataCleaner { false },
            clearWebStorage = { remainingAdapters++ },
            clearWebView = { remainingAdapters++ },
            clearManifestCache = { remainingAdapters++ },
            clearConsent = { remainingAdapters++ },
            clearTrace = { remainingAdapters++ },
        )

        assertTrue(cleaner.clear())
        assertEquals(5, remainingAdapters)
    }

    @Test fun `adapter failure is incomplete and later adapters still run`() = runTest {
        val calls = mutableListOf<String>()
        val cleaner = BrowsingDataCleaner(
            cookies = CookieDataCleaner { true },
            clearWebStorage = { error("unavailable") },
            clearWebView = { calls += "webview" },
            clearManifestCache = { calls += "manifest" },
            clearConsent = { calls += "consent" },
            clearHistory = { calls += "history" },
            clearTrace = { calls += "trace" },
        )

        assertFalse(cleaner.clear())
        assertEquals(listOf("webview", "manifest", "consent", "history", "trace"), calls)
    }
}
