package app.webora.browser.browser

import dev.siteskin.core.origin.SiteOrigin

internal data class BrowserState(
    val mode: BrowserMode = BrowserMode.Home,
    val displayedUrl: String = "",
    val addressText: String = "",
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

internal sealed interface BrowserObservation {
    data class Page(
        val url: String,
        val isLoading: Boolean,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    ) : BrowserObservation

    data class AddressEdited(val text: String) : BrowserObservation
}

internal fun BrowserState.observe(observation: BrowserObservation): BrowserState =
    when (observation) {
        is BrowserObservation.AddressEdited -> copy(addressText = observation.text)
        is BrowserObservation.Page -> copy(
            mode = BrowserMode.Regular(SiteOrigin.parse(observation.url)),
            displayedUrl = observation.url,
            addressText = observation.url,
            isLoading = observation.isLoading,
            canGoBack = observation.canGoBack,
            canGoForward = observation.canGoForward,
        )
    }
