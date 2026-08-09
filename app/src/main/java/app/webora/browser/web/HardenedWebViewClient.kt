package app.webora.browser.web

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

internal class HardenedWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        request?.url?.toString()?.let { !isWebViewOwnedUrl(it) } ?: true

    @Deprecated("Used by WebView providers on older Android versions")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        url?.let { !isWebViewOwnedUrl(it) } ?: true
}
