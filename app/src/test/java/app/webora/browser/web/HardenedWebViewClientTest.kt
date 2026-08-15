package app.webora.browser.web

import android.webkit.WebViewClient
import android.webkit.WebView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import app.webora.browser.browser.LoadErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardenedWebViewClientTest {
    @Test fun `successful main frame completion publishes browser observed URL and title`() {
        val view = mockk<WebView>()
        every { view.title } returns "Observed title"
        var completed: Pair<String, String?>? = null
        val client = HardenedWebViewClient(
            onMainFrameCompleted = { _, url, title -> completed = url to title },
        )

        client.onPageFinished(view, "https://example.com/page")

        assertEquals("https://example.com/page" to "Observed title", completed)
    }

    @Test fun `failed main frame finish is not completion and next navigation can complete`() {
        val view = mockk<WebView>()
        val request = mockk<WebResourceRequest>()
        val error = mockk<WebResourceError>()
        val uri = mockk<Uri>()
        every { view.title } returns "Page"
        every { request.isForMainFrame } returns true
        every { request.url } returns uri
        every { uri.toString() } returns "https://failed.example/"
        every { error.errorCode } returns WebViewClient.ERROR_CONNECT
        val completions = mutableListOf<String>()
        val client = HardenedWebViewClient(
            onMainFrameCompleted = { _, url, _ -> completions += url },
        )

        client.onPageStarted(view, "https://failed.example/", null)
        client.onReceivedError(view, request, error)
        client.onPageFinished(view, "https://failed.example/")
        client.onPageStarted(view, "https://safe.example/", null)
        client.onPageFinished(view, "https://safe.example/")

        assertEquals(listOf("https://safe.example/"), completions)
    }

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
