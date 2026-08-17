package app.webora.browser.web

import android.view.ViewGroup
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

    /**
     * The URL this renderer is known to be on, or `null` before it has been given one.
     *
     * The retained renderer outlives its Compose host — `BROWSE-006` requires that — so `attached()`
     * being non-null stopped answering "does this renderer already have its tab's page?" the moment
     * renderers began outliving hosts. This is the value that answers it, and it lives here for the
     * same reason [tabId] does: a fact about the renderer belongs to the renderer's owner, not to
     * whichever call site last remembered to track it.
     *
     * Written from two browser-owned sources and nothing else — what the browser asked for
     * ([navigate]) and what the renderer reported ([observed]). Page content and manifest fields
     * have no path in, which matters because this value decides whether the browser re-issues a
     * navigation and to where.
     */
    var hostedUrl: String? = null
        private set

    fun attach(webView: WebView) {
        this.webView = webView
    }

    /**
     * Records the URL the renderer reported for its own main frame.
     *
     * In-page navigation never passes through [navigate], so a record of *requests* alone drifts
     * from what the renderer shows and would re-issue a load every time the user returned to a tab
     * they had browsed within. A failed navigation deliberately does not reach here: the browser's
     * last request stands, which is what keeps an error tab from reloading on every switch.
     */
    fun observed(url: String) {
        hostedUrl = url
    }

    /**
     * Removes the retained renderer from its Compose host without destroying it.
     *
     * The renderer stays associated with its tab while another tab is shown — `BROWSE-006` requires
     * live back/forward history to survive a switch, so selection may never destroy. It must still
     * leave the view hierarchy: a `View` that already has a parent throws
     * `IllegalStateException: The specified child already has a parent` when its tab is selected
     * again and a new host tries to adopt it. Compose removes the host, not the child inside it.
     *
     * This is the one owner of that removal. It replaced a `detach(webView)` that compared the view
     * and then returned either way — a no-op shaped like a contract, called with a `var` that was
     * reset to `null` on every recomposition, so nothing observable happened on either side.
     */
    fun detachFromParent() {
        val view = webView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
    }

    fun attached(): WebView? = webView

    fun destroy() {
        // The framework requires a WebView to leave the view hierarchy before it is destroyed.
        detachFromParent()
        webView?.destroy()
        webView = null
        hostedUrl = null
    }

    fun navigate(url: String) {
        hostedUrl = url
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
