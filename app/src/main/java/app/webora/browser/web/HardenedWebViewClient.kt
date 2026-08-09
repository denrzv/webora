package app.webora.browser.web

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import app.webora.browser.browser.LoadErrorKind

internal class HardenedWebViewClient(
    private val onPageChanged: (WebView, String, Boolean) -> Unit = { _, _, _ -> },
    private val onMainFrameFailed: (String, LoadErrorKind) -> Unit = { _, _ -> },
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

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            onMainFrameFailed(request.url.toString(), classifyWebViewError(error.errorCode))
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }

    private companion object {
        const val COMPLETE_PROGRESS = 100
    }
}

internal fun classifyWebViewError(errorCode: Int): LoadErrorKind = when (errorCode) {
    WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT ->
        LoadErrorKind.CONNECTION
    WebViewClient.ERROR_IO, WebViewClient.ERROR_PROXY_AUTHENTICATION -> LoadErrorKind.NETWORK
    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> LoadErrorKind.TLS
    else -> LoadErrorKind.UNKNOWN
}
