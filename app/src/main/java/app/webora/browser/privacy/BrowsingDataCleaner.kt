package app.webora.browser.privacy

import android.webkit.CookieManager
import android.webkit.WebStorage
import app.webora.browser.inspector.SiteSkinTraceRecorder
import app.webora.browser.siteskin.ManifestDiscoveryCoordinator
import app.webora.browser.siteskin.SiteConsentStore
import app.webora.browser.web.BrowserWebViewController
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal fun interface CookieDataCleaner {
    suspend fun clear(): Boolean
}

internal class BrowsingDataCleaner(
    private val cookies: CookieDataCleaner,
    private val clearWebStorage: () -> Unit,
    private val clearWebView: () -> Unit,
    private val clearManifestCache: () -> Unit,
    private val clearConsent: () -> Unit,
    private val clearTrace: () -> Unit = {},
) {
    suspend fun clear(): Boolean {
        var complete = runCatching { cookies.clear() }.isSuccess
        listOf(clearWebStorage, clearWebView, clearManifestCache, clearConsent, clearTrace).forEach { clear ->
            if (runCatching(clear).isFailure) complete = false
        }
        return complete
    }

    companion object {
        fun android(
            controller: BrowserWebViewController,
            discovery: ManifestDiscoveryCoordinator,
            consentStore: SiteConsentStore,
            trace: SiteSkinTraceRecorder?,
        ) = BrowsingDataCleaner(
            cookies = CookieDataCleaner {
                suspendCancellableCoroutine { continuation ->
                    CookieManager.getInstance().removeAllCookies { removed ->
                        if (continuation.isActive) continuation.resume(removed)
                    }
                }
            },
            clearWebStorage = { WebStorage.getInstance().deleteAllData() },
            clearWebView = controller::clearBrowsingData,
            clearManifestCache = discovery::clearCache,
            clearConsent = consentStore::clear,
            // Debug-only and in memory, but it is per-origin state derived from browsing, so it
            // goes when the manifest cache and the stored decisions go.
            clearTrace = { trace?.clear() },
        )
    }
}
