package app.webora.browser.web

import android.webkit.WebView
import app.webora.browser.browser.LoadErrorKind

internal data class WebViewObservation(
    val url: String,
    val isLoading: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
)

/**
 * One observation from one renderer, naming the tab that owns it.
 *
 * [tabId] is fixed when the renderer is built and never re-read, which is what makes a late callback
 * from a background tab addressable. Reading the selected tab at delivery time instead is the defect
 * `BROWSE-009` removes.
 */
internal sealed interface WebViewEvent {
    val tabId: Long

    data class PageStarted(override val tabId: Long, val observation: WebViewObservation) : WebViewEvent

    data class PageChanged(override val tabId: Long, val observation: WebViewObservation) : WebViewEvent

    data class MainFrameCompleted(
        override val tabId: Long,
        val observation: WebViewObservation,
        val title: String?,
    ) : WebViewEvent

    data class MainFrameFailed(
        override val tabId: Long,
        val url: String,
        val kind: LoadErrorKind,
    ) : WebViewEvent
}

/**
 * The one renderer owned by one tab.
 *
 * [tabId] lives here rather than only in the map key that reaches this object, so every event the
 * renderer emits can name its owner without the call site remembering to.
 */
internal class BrowserWebViewController(val tabId: Long) {
    private var webView: WebView? = null

    fun attach(webView: WebView) {
        this.webView = webView
    }

    fun detach(webView: WebView) {
        // Keep the renderer associated with its tab while Compose shows another tab. The owning
        // session destroys it when that tab closes.
        if (this.webView !== webView) return
    }

    fun attached(): WebView? = webView

    fun destroy() {
        webView?.destroy()
        webView = null
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

    fun clearBrowsingData() {
        webView?.clearCache(true)
        webView?.clearFormData()
        webView?.clearHistory()
    }
}
