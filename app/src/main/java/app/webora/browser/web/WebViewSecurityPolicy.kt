package app.webora.browser.web

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.net.URI

internal data class WebViewSecurityPolicy(
    val javaScriptEnabled: Boolean = true,
    val allowFileAccess: Boolean = false,
    val allowContentAccess: Boolean = false,
    val allowFileAccessFromFileUrls: Boolean = false,
    val allowUniversalAccessFromFileUrls: Boolean = false,
    val mixedContentMode: Int = WebSettings.MIXED_CONTENT_NEVER_ALLOW,
    val safeBrowsingEnabled: Boolean = true,
)

internal val hardenedWebViewPolicy = WebViewSecurityPolicy()

@Suppress("DEPRECATION")
internal fun applyWebViewHardening(
    webView: WebView,
    policy: WebViewSecurityPolicy = hardenedWebViewPolicy,
) {
    webView.settings.apply {
        javaScriptEnabled = policy.javaScriptEnabled
        allowFileAccess = policy.allowFileAccess
        allowContentAccess = policy.allowContentAccess
        allowFileAccessFromFileURLs = policy.allowFileAccessFromFileUrls
        allowUniversalAccessFromFileURLs = policy.allowUniversalAccessFromFileUrls
        mixedContentMode = policy.mixedContentMode
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
        WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, policy.safeBrowsingEnabled)
    }
}

internal fun isWebViewOwnedUrl(url: String): Boolean =
    runCatching { URI(url).scheme?.lowercase() }
        .getOrNull() in setOf("http", "https")
