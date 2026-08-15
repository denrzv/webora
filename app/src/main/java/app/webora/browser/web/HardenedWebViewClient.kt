package app.webora.browser.web

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import app.webora.browser.browser.LoadErrorKind
import app.webora.browser.browser.ExternalNavigation
import app.webora.browser.browser.externalNavigation

internal class HardenedWebViewClient(
    private val onPageStarted: (WebView, String) -> Unit = { _, _ -> },
    private val onPageChanged: (WebView, String, Boolean) -> Unit = { _, _, _ -> },
    private val onMainFrameCompleted: (WebView, String, String?) -> Unit = { _, _, _ -> },
    private val onMainFrameFailed: (String, LoadErrorKind) -> Unit = { _, _ -> },
    private val onExternalNavigation: (ExternalNavigation) -> Unit = {},
) : WebViewClient() {
    private var failedMainFrameUrl: String? = null

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        request?.url?.toString()?.let {
            shouldOverrideNavigation(it, request.isForMainFrame, onExternalNavigation)
        } ?: true

    @Deprecated("Used by WebView providers on older Android versions")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        url?.let { shouldOverrideNavigation(it, true, onExternalNavigation) } ?: true

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        failedMainFrameUrl = null
        onPageStarted(view, url.orEmpty())
    }

    override fun onPageFinished(view: WebView, url: String?) {
        onPageChanged(view, url.orEmpty(), false)
        val completedUrl = url.orEmpty()
        if (completedUrl != failedMainFrameUrl) onMainFrameCompleted(view, completedUrl, view.title)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        onPageChanged(view, url.orEmpty(), view.progress < COMPLETE_PROGRESS)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            val failedUrl = request.url.toString()
            failedMainFrameUrl = failedUrl
            onMainFrameFailed(failedUrl, classifyWebViewError(error.errorCode))
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }

    private companion object {
        const val COMPLETE_PROGRESS = 100
    }
}

internal fun shouldOverrideNavigation(
    url: String,
    isMainFrame: Boolean,
    onExternalNavigation: (ExternalNavigation) -> Unit,
): Boolean {
    if (!isMainFrame) return false
    if (isWebViewOwnedUrl(url)) return false
    externalNavigation(url)?.let(onExternalNavigation)
    return true
}

internal fun classifyWebViewError(errorCode: Int): LoadErrorKind = when (errorCode) {
    WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT ->
        LoadErrorKind.CONNECTION
    WebViewClient.ERROR_IO, WebViewClient.ERROR_PROXY_AUTHENTICATION -> LoadErrorKind.NETWORK
    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> LoadErrorKind.TLS
    else -> LoadErrorKind.UNKNOWN
}
