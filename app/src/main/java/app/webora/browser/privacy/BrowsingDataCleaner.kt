package app.webora.browser.privacy

import android.webkit.CookieManager
import android.webkit.WebStorage
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
) {
    suspend fun clear(): Boolean {
        var complete = runCatching { cookies.clear() }.isSuccess
        listOf(clearWebStorage, clearWebView, clearManifestCache, clearConsent).forEach { clear ->
            if (runCatching(clear).isFailure) complete = false
        }
        return complete
    }

    companion object {
        fun android(
            controller: BrowserWebViewController,
            discovery: ManifestDiscoveryCoordinator,
            consentStore: SiteConsentStore,
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
        )
    }
}
