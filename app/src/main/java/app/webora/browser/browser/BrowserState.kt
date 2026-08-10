package app.webora.browser.browser

import dev.siteskin.core.origin.SiteOrigin
import dev.siteskin.core.model.SiteSkinConfiguration

internal data class BrowserState(
    val mode: BrowserMode = BrowserMode.Home,
    val displayedUrl: String = "",
    val addressText: String = "",
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loadFailure: BrowserLoadFailure? = null,
)

internal enum class LoadErrorKind {
    NETWORK,
    CONNECTION,
    TLS,
    UNKNOWN,
}

internal data class BrowserLoadFailure(
    val kind: LoadErrorKind,
    val registrableDomain: String?,
    val retryUrl: String?,
)

internal sealed interface BrowserObservation {
    data class PageStarted(
        val url: String,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    ) : BrowserObservation

    data class Page(
        val url: String,
        val isLoading: Boolean,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    ) : BrowserObservation

    data class AddressEdited(val text: String) : BrowserObservation

    data class PageFailed(val url: String, val kind: LoadErrorKind) : BrowserObservation
}

internal fun BrowserState.observe(observation: BrowserObservation): BrowserState =
    when (observation) {
        is BrowserObservation.AddressEdited -> copy(addressText = observation.text)
        is BrowserObservation.PageFailed -> observeFailure(observation)
        is BrowserObservation.PageStarted -> observePage(
            url = observation.url,
            isLoading = true,
            canGoBack = observation.canGoBack,
            canGoForward = observation.canGoForward,
            failure = null,
        )
        is BrowserObservation.Page -> observePage(
            url = observation.url,
            isLoading = observation.isLoading,
            canGoBack = observation.canGoBack,
            canGoForward = observation.canGoForward,
            failure = loadFailure,
        )
    }

private fun BrowserState.observePage(
    url: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    failure: BrowserLoadFailure?,
): BrowserState = copy(
    mode = mode.forObservedOrigin(SiteOrigin.parse(url)),
    displayedUrl = url,
    addressText = url,
    isLoading = isLoading,
    canGoBack = canGoBack,
    canGoForward = canGoForward,
    loadFailure = failure,
)

private fun BrowserMode.forObservedOrigin(origin: SiteOrigin?): BrowserMode =
    if (this is BrowserMode.Integrated && this.origin == origin) this else BrowserMode.Regular(origin)

internal fun BrowserState.activateSiteSkin(
    origin: SiteOrigin,
    configuration: SiteSkinConfiguration,
): BrowserState = if ((mode as? BrowserMode.Regular)?.origin == origin) {
    copy(mode = BrowserMode.Integrated(origin, configuration))
} else {
    this
}

private fun BrowserState.observeFailure(failure: BrowserObservation.PageFailed): BrowserState {
    val retryUrl = resolveAddressInput(failure.url)?.takeIf { it == failure.url }
    val origin = retryUrl?.let(dev.siteskin.core.origin.SiteOrigin::parse)
    return copy(
        isLoading = false,
        loadFailure = BrowserLoadFailure(failure.kind, origin?.registrableDomain, retryUrl),
    )
}

internal fun BrowserState.navigateFromHome(url: String): BrowserState = copy(
    mode = BrowserMode.Regular(SiteOrigin.parse(url)),
    displayedUrl = url,
    addressText = url,
    isLoading = true,
)
