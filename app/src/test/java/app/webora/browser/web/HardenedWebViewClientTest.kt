package app.webora.browser.web

import android.webkit.WebViewClient
import app.webora.browser.browser.LoadErrorKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HardenedWebViewClientTest {
    @Test
    fun `framework errors map to closed browser owned reasons`() {
        assertEquals(LoadErrorKind.CONNECTION, classifyWebViewError(WebViewClient.ERROR_HOST_LOOKUP))
        assertEquals(LoadErrorKind.NETWORK, classifyWebViewError(WebViewClient.ERROR_IO))
        assertEquals(LoadErrorKind.TLS, classifyWebViewError(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE))
        assertEquals(LoadErrorKind.UNKNOWN, classifyWebViewError(Int.MIN_VALUE))
    }
}
