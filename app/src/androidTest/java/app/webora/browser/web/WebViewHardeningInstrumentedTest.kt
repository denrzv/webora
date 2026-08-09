package app.webora.browser.web

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewHardeningInstrumentedTest {
    @Suppress("DEPRECATION")
    @Test
    fun realWebSettingsReflectBrowserOwnedPolicy() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = WebView(context)
            applyWebViewHardening(webView)

            with(webView.settings) {
                assertTrue(javaScriptEnabled)
                assertFalse(allowFileAccess)
                assertFalse(allowContentAccess)
                assertFalse(allowFileAccessFromFileURLs)
                assertFalse(allowUniversalAccessFromFileURLs)
                assertTrue(mixedContentMode == WebSettings.MIXED_CONTENT_NEVER_ALLOW)
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                assertTrue(WebSettingsCompat.getSafeBrowsingEnabled(webView.settings))
            }
            webView.destroy()
        }
    }
}
