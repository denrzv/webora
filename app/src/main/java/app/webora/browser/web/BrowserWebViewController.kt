package app.webora.browser.web

import android.webkit.WebView

internal data class WebViewObservation(
    val url: String,
    val isLoading: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
)

internal class BrowserWebViewController {
    private var webView: WebView? = null

    fun attach(webView: WebView) {
        this.webView = webView
    }

    fun detach(webView: WebView) {
        if (this.webView === webView) this.webView = null
    }

    fun navigate(url: String) {
        webView?.loadUrl(url)
    }

    fun goBack(): Boolean = webView?.takeIf(WebView::canGoBack)?.run {
        goBack()
        true
    } ?: false

    fun goForward() {
        webView?.takeIf(WebView::canGoForward)?.goForward()
    }

    fun reload() {
        webView?.reload()
    }
}
