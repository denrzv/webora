package app.webora.browser.browser

import dev.siteskin.core.origin.SiteOrigin

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
        is BrowserObservation.Page -> copy(
            mode = BrowserMode.Regular(SiteOrigin.parse(observation.url)),
            displayedUrl = observation.url,
            addressText = observation.url,
            isLoading = observation.isLoading,
            canGoBack = observation.canGoBack,
            canGoForward = observation.canGoForward,
            loadFailure = if (observation.isLoading) null else loadFailure,
        )
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
