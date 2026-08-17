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
 *
 * One host serves one tab. The caller keys this composable by the owning `BrowserTab.id`, because
 * `AndroidView`'s `factory` runs once per *retained composition slot*: at one un-keyed call site,
 * switching between two page tabs recomposes the slot instead of replacing it, leaving the previous
 * tab's `WebView` on screen while the selected tab's controller — never attached to it — silently
 * dropped every `navigate`, `reload`, `goBack` and `goForward`.
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
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val existing = controller.attached()
            // Fixed once, for the renderer's whole lifetime. `currentObserver` deliberately swings
            // to the newest handler on every recomposition; that is only safe because what it
            // delivers names its own tab, so the handler can address the owner rather than the
            // selection. Reading `controller.tabId` per callback would be the same value today and
            // an invitation to make it dynamic later.
            val owner = controller.tabId
            (existing ?: WebView(context)).apply {
                if (existing == null) applyWebViewHardening(this)
                webViewClient = HardenedWebViewClient(
                    onPageStarted = { view, url ->
                        currentObserver.value(WebViewEvent.PageStarted(owner, view.toObservation(url, true)))
                    },
                    onPageChanged = { view, url, isLoading ->
                        currentObserver.value(WebViewEvent.PageChanged(owner, view.toObservation(url, isLoading)))
                    },
                    onMainFrameCompleted = { view, url, title ->
                        currentObserver.value(
                            WebViewEvent.MainFrameCompleted(owner, view.toObservation(url, false), title),
                        )
                    },
                    onMainFrameFailed = { url, kind ->
                        currentObserver.value(WebViewEvent.MainFrameFailed(owner, url, kind))
                    },
                    onExternalNavigation = onExternalNavigation,
                )
                webChromeClient = UploadWebChromeClient(onFileChooser)
                setDownloadListener { url, _, _, _, _ -> url?.let(onDownload) }
                controller.attach(this)
                if (existing == null) loadUrl(initialUrl)
            }
        },
    )
    // The controller already holds the reference, so the dispose path reads it from the one owner
    // rather than from a body-local `var` that every recomposition reset to null.
    DisposableEffect(controller) {
        onDispose(controller::detachFromParent)
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
