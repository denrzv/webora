package app.webora.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserFailureStateTest {
    @Test
    fun `main frame failure retains only safe retry destination and reason`() {
        val state = BrowserState().observe(
            BrowserObservation.PageFailed("https://example.com/private?q=secret", LoadErrorKind.NETWORK),
        )

        assertEquals("https://example.com/private?q=secret", state.loadFailure?.retryUrl)
        assertEquals("example.com", state.loadFailure?.registrableDomain)
        assertEquals(LoadErrorKind.NETWORK, state.loadFailure?.kind)
    }

    @Test
    fun `unsafe failure destination cannot become retry capability`() {
        val state = BrowserState().observe(
            BrowserObservation.PageFailed("file:///sdcard/secret", LoadErrorKind.UNKNOWN),
        )

        assertNull(state.loadFailure?.retryUrl)
        assertNull(state.loadFailure?.registrableDomain)
    }

    @Test
    fun `new main frame start clears stale failure`() {
        val failed = BrowserState().observe(
            BrowserObservation.PageFailed("https://example.com", LoadErrorKind.CONNECTION),
        )

        val loading = failed.observe(
            BrowserObservation.PageStarted("https://example.com", false, false),
        )

        assertNull(loading.loadFailure)
    }

    @Test
    fun `later loading history callback cannot erase main frame failure`() {
        val failed = BrowserState().observe(
            BrowserObservation.PageFailed("https://example.com", LoadErrorKind.CONNECTION),
        )

        val updated = failed.observe(
            BrowserObservation.Page("https://example.com", true, false, false),
        )

        assertNotNull(updated.loadFailure)
    }
}
