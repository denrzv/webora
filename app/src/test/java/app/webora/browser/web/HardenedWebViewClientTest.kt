package app.webora.browser.web

import android.webkit.WebViewClient
import app.webora.browser.browser.LoadErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardenedWebViewClientTest {
    @Test
    fun `navigation policy keeps web urls and emits only supported external schemes`() {
        assertFalse(shouldOverrideNavigation("https://example.com", true) { error("must not emit") })
        var emitted: String? = null
        assertTrue(shouldOverrideNavigation("mailto:hello@example.com", true) { emitted = it.uri })
        assertEquals("mailto:hello@example.com", emitted)
        emitted = null
        assertTrue(shouldOverrideNavigation("intent://attack", true) { emitted = it.uri })
        assertEquals(null, emitted)
    }

    @Test
    fun `subframes cannot request external navigation`() {
        var emitted = false
        assertFalse(shouldOverrideNavigation("mailto:hello@example.com", false) { emitted = true })
        assertFalse(emitted)
    }

    @Test
    fun `framework errors map to closed browser owned reasons`() {
        assertEquals(LoadErrorKind.CONNECTION, classifyWebViewError(WebViewClient.ERROR_HOST_LOOKUP))
        assertEquals(LoadErrorKind.NETWORK, classifyWebViewError(WebViewClient.ERROR_IO))
        assertEquals(LoadErrorKind.TLS, classifyWebViewError(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE))
        assertEquals(LoadErrorKind.UNKNOWN, classifyWebViewError(Int.MIN_VALUE))
    }
}
