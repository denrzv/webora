package app.webora.browser.web

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

internal class HardenedWebViewClient(
    private val onPageChanged: (WebView, String, Boolean) -> Unit = { _, _, _ -> },
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        request?.url?.toString()?.let { !isWebViewOwnedUrl(it) } ?: true

    @Deprecated("Used by WebView providers on older Android versions")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        url?.let { !isWebViewOwnedUrl(it) } ?: true

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        onPageChanged(view, url.orEmpty(), true)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        onPageChanged(view, url.orEmpty(), false)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        onPageChanged(view, url.orEmpty(), view.progress < COMPLETE_PROGRESS)
    }

    private companion object {
        const val COMPLETE_PROGRESS = 100
    }
}
