package app.webora.browser.privacy

import android.webkit.CookieManager
import android.webkit.WebStorage
import app.webora.browser.inspector.SiteSkinTraceRecorder
import app.webora.browser.siteskin.ManifestDiscoveryCoordinator
import app.webora.browser.siteskin.SiteConsentStore
import app.webora.browser.web.BrowserWebViewController
import app.webora.browser.browser.BrowsingRecordStore
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
    private val clearHistory: () -> Unit = {},
    private val clearTrace: () -> Unit = {},
) {
    suspend fun clear(): Boolean {
        var complete = runCatching { cookies.clear() }.isSuccess
        val adapters = listOf(
            clearWebStorage, clearWebView, clearManifestCache, clearConsent, clearHistory, clearTrace,
        )
        adapters.forEach { clear ->
            if (runCatching(clear).isFailure) complete = false
        }
        return complete
    }

    companion object {
        fun android(
            controllers: Collection<BrowserWebViewController>,
            discovery: ManifestDiscoveryCoordinator,
            consentStore: SiteConsentStore,
            recordStore: BrowsingRecordStore,
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
            clearWebView = { controllers.forEach(BrowserWebViewController::clearBrowsingData) },
            clearManifestCache = discovery::clearCache,
            clearConsent = consentStore::clear,
            clearHistory = recordStore::clearHistory,
            // Debug-only and in memory, but it is per-origin state derived from browsing, so it
            // goes when the manifest cache and the stored decisions go.
            clearTrace = { trace?.clear() },
        )
    }
}
