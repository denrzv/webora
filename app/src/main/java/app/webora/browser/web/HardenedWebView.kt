package app.webora.browser.web

import android.webkit.WebView
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.webora.browser.browser.ExternalNavigation

/**
 * Hosts an untrusted web page with Webora's fixed renderer security policy.
 */
@Composable
internal fun HardenedWebView(
    initialUrl: String,
    controller: BrowserWebViewController,
    onEvent: (WebViewEvent) -> Unit,
    onExternalNavigation: (ExternalNavigation) -> Unit,
    onDownload: (String) -> Unit,
    onFileChooser: (String, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentObserver = rememberUpdatedState(onEvent)
    var attachedWebView: WebView? = null
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                applyWebViewHardening(this)
                webViewClient = HardenedWebViewClient(
                    onPageChanged = { view, url, isLoading ->
                        currentObserver.value(WebViewEvent.PageChanged(view.toObservation(url, isLoading)))
                    },
                    onMainFrameFailed = { url, kind ->
                        currentObserver.value(WebViewEvent.MainFrameFailed(url, kind))
                    },
                    onExternalNavigation = onExternalNavigation,
                )
                webChromeClient = UploadWebChromeClient(onFileChooser)
                setDownloadListener { url, _, _, _, _ -> url?.let(onDownload) }
                controller.attach(this)
                attachedWebView = this
                loadUrl(initialUrl)
            }
        },
    )
    DisposableEffect(controller) {
        onDispose { attachedWebView?.let(controller::detach) }
    }
}

private class UploadWebChromeClient(
    private val requestFile: (String, (String?) -> Unit) -> Unit,
) : WebChromeClient() {
    private var pending: ValueCallback<Array<Uri>>? = null

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        pending?.onReceiveValue(null)
        pending = filePathCallback
        val mimeType = uploadMimeType(fileChooserParams?.acceptTypes.orEmpty())
        if (mimeType == null) {
            pending?.onReceiveValue(null)
            pending = null
            return true
        }
        requestFile(mimeType) { selected ->
            val result = selectedUploadUri(selected)?.let { arrayOf(Uri.parse(it)) }
            pending?.onReceiveValue(result)
            pending = null
        }
        return true
    }
}

private fun WebView.toObservation(url: String, isLoading: Boolean): WebViewObservation = WebViewObservation(
    url = url,
    isLoading = isLoading,
    canGoBack = canGoBack(),
    canGoForward = canGoForward(),
)
