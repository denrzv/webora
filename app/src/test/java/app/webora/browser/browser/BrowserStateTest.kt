package app.webora.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserStateTest {
    @Test
    fun `home navigation enters regular mode with resolved destination`() {
        val state = BrowserState().navigateFromHome("https://example.com/path")

        assertEquals("https://example.com/path", state.displayedUrl)
        assertEquals("https://example.com/path", state.addressText)
        assertTrue(state.mode is BrowserMode.Regular)
    }
    @Test
    fun `page observation creates regular mode and renderer state`() {
        val state = BrowserState().observe(
            BrowserObservation.Page(
                url = "https://Example.com/catalog",
                isLoading = true,
                canGoBack = true,
                canGoForward = false,
            ),
        )

        assertEquals("https://Example.com/catalog", state.displayedUrl)
        assertEquals(state.displayedUrl, state.addressText)
        assertTrue(state.isLoading)
        assertTrue(state.canGoBack)
        assertFalse(state.canGoForward)
        assertEquals("example.com", (state.mode as BrowserMode.Regular).origin?.host)
    }

    @Test
    fun `malformed callback stays regular without trusted origin`() {
        val state = BrowserState().observe(
            BrowserObservation.Page("not a URL", false, false, false),
        )

        assertNull((state.mode as BrowserMode.Regular).origin)
    }

    @Test
    fun `address edit does not change observed browser mode`() {
        val initial = BrowserState(mode = BrowserMode.Regular(null))
        val state = initial.observe(BrowserObservation.AddressEdited("untrusted text"))

        assertEquals(initial.mode, state.mode)
        assertEquals("untrusted text", state.addressText)
    }
}
