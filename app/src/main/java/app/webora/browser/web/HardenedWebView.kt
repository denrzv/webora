package app.webora.browser.web

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hosts an untrusted web page with Webora's fixed renderer security policy.
 */
@Composable
internal fun HardenedWebView(
    initialUrl: String,
    controller: BrowserWebViewController,
    onEvent: (WebViewEvent) -> Unit,
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
                )
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

private fun WebView.toObservation(url: String, isLoading: Boolean): WebViewObservation = WebViewObservation(
    url = url,
    isLoading = isLoading,
    canGoBack = canGoBack(),
    canGoForward = canGoForward(),
)
