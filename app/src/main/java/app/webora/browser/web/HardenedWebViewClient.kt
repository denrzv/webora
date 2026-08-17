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

    /**
     * The main-frame URL this client last observed starting.
     *
     * Browser-observed, never page-supplied: it is the argument the framework handed to
     * [onPageStarted]. It exists so [onReceivedSslError] — which does *not* identify the frame —
     * can be asked whether the resource it is refusing is the page itself.
     */
    private var mainFrameUrl: String? = null

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        request?.url?.toString()?.let {
            shouldOverrideNavigation(it, request.isForMainFrame, onExternalNavigation)
        } ?: true

    @Deprecated("Used by WebView providers on older Android versions")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        url?.let { shouldOverrideNavigation(it, true, onExternalNavigation) } ?: true

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        failedMainFrameUrl = null
        mainFrameUrl = url
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

    /**
     * Refuses the connection, and settles the page when the refused resource *was* the page.
     *
     * `handler.cancel()` stays unconditional and first. `handler.proceed()` appears nowhere in this
     * tree and never may — a source assertion pins it.
     *
     * `BROWSE-004` cancelled here and published nothing, because this callback does not identify
     * the main frame and a subframe must never replace a good page. That reasoning is intact; what
     * changes is that the browser now asks its *own* observation whether this is the main frame,
     * instead of having no way to ask. When the answer is not a clear yes, nothing is published —
     * a spinner is a worse outcome than an error page, and a wrong error page is worse than both.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        val failedUrl = mainFrameTlsFailure(error.url, mainFrameUrl, failedMainFrameUrl) ?: return
        failedMainFrameUrl = failedUrl
        onMainFrameFailed(failedUrl, LoadErrorKind.TLS)
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

/**
 * Whether a cancelled TLS handshake settles the page, and for which URL.
 *
 * Three conditions, all of which must hold:
 *
 * - the refused resource has a URL at all;
 * - it is the URL the browser observed the main frame starting, so a subframe or subresource on a
 *   different host cannot replace the page — the refusal `BROWSE-004` is built on;
 * - this navigation has not already published a failure, so the framework's own
 *   `ERROR_FAILED_SSL_HANDSHAKE` arriving after (or before) this does not produce a second, possibly
 *   different, failure for one navigation. Idempotence is in the signature rather than in a delivery
 *   order, because the delivery order is not something this code gets to decide.
 *
 * A redirect chain that fails after the observed start reports a URL that is not [mainFrameUrl], so
 * it publishes nothing and can still leave a spinner. That is the fail-closed direction and strictly
 * better than publishing nothing at all, which is what happened before. Narrowing it needs a
 * browser-observed main-frame URL that survives redirects, which is a `BROWSE-004` change.
 */
internal fun mainFrameTlsFailure(
    errorUrl: String?,
    mainFrameUrl: String?,
    alreadyFailed: String?,
): String? {
    val url = errorUrl?.takeIf { it.isNotBlank() } ?: return null
    if (url != mainFrameUrl) return null
    if (url == alreadyFailed) return null
    return url
}

internal fun classifyWebViewError(errorCode: Int): LoadErrorKind = when (errorCode) {
    WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT ->
        LoadErrorKind.CONNECTION
    WebViewClient.ERROR_IO, WebViewClient.ERROR_PROXY_AUTHENTICATION -> LoadErrorKind.NETWORK
    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> LoadErrorKind.TLS
    else -> LoadErrorKind.UNKNOWN
}
