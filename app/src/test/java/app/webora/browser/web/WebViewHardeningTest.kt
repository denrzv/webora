package app.webora.browser.web

import android.webkit.WebSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewHardeningTest {
    @Test
    fun `fixed policy enables script but denies local and mixed content capabilities`() {
        val policy = hardenedWebViewPolicy

        assertTrue(policy.javaScriptEnabled)
        assertFalse(policy.allowFileAccess)
        assertFalse(policy.allowContentAccess)
        assertFalse(policy.allowFileAccessFromFileUrls)
        assertFalse(policy.allowUniversalAccessFromFileUrls)
        assertTrue(policy.mixedContentMode == WebSettings.MIXED_CONTENT_NEVER_ALLOW)
        assertTrue(policy.safeBrowsingEnabled)
    }

    @Test
    fun `only http and https remain WebView-owned navigation`() {
        assertTrue(isWebViewOwnedUrl("https://example.com/page"))
        assertTrue(isWebViewOwnedUrl("HTTP://example.com"))
        assertFalse(isWebViewOwnedUrl("file:///data/local/private.html"))
        assertFalse(isWebViewOwnedUrl("content://app.webora.browser/private"))
        assertFalse(isWebViewOwnedUrl("javascript:alert(1)"))
        assertFalse(isWebViewOwnedUrl("not a URL"))
    }
}
