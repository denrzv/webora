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
    /**
     * What the browser has observed about this tab's transport.
     *
     * Lives here rather than on [BrowserMode] for two reasons, and the second one is the load-bearing
     * one. Mode equality decides security outcomes — `forObservedOrigin` compares origins to decide
     * whether integrated mode survives a page start, and `activateSiteSkin` compares a `Regular`
     * origin before activating — so a lifecycle value inside the mode would put transport into
     * comparisons that are about identity. And `NET-004` records that `forObservedOrigin` returns the
     * *same* `Integrated` instance for every same-origin page start, which is what keys
     * `BrowserScreen`'s brand-asset effect: a mode whose identity changed with transport would
     * re-run that load on every navigation.
     *
     * Deliberately unpersisted. `BROWSE-006` requires a restored tab to re-traverse discovery and
     * activation, and a persisted `SECURE` would be a way to skip the observation that earns it.
     */
    val transport: TransportSecurity = TransportSecurity.UNKNOWN,
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

    /**
     * A main frame that finished *successfully*, which [Page] does not mean.
     *
     * Its own case rather than a flag on [Page] because the distinction is the whole basis of the
     * secure claim, and `toBrowserObservation` used to collapse the two: `onPageFinished` fires
     * `onPageChanged` unconditionally and only then fires `onMainFrameCompleted`, suppressed for a
     * URL that already failed. A `Boolean` would have carried the same information and let a call
     * site pass the wrong one silently; a case makes `observe`'s `when` exhaustive over it.
     */
    data class MainFrameCompleted(
        val url: String,
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
        // A new navigation always clears the secure claim. This reset is what makes every gap in
        // the observation surface fail closed rather than silently: `mainFrameTlsFailure` publishes
        // nothing when it cannot identify the main frame, so "no news" must never be able to mean
        // the previous page's good news.
        is BrowserObservation.PageStarted -> observePage(
            url = observation.url,
            isLoading = true,
            canGoBack = observation.canGoBack,
            canGoForward = observation.canGoForward,
            failure = null,
            transport = TransportSecurity.UNKNOWN,
        )
        is BrowserObservation.Page -> observePage(
            url = observation.url,
            isLoading = observation.isLoading,
            canGoBack = observation.canGoBack,
            canGoForward = observation.canGoForward,
            failure = loadFailure,
            transport = transportAcross(observation.url),
        )
        is BrowserObservation.MainFrameCompleted -> observePage(
            url = observation.url,
            isLoading = false,
            canGoBack = observation.canGoBack,
            canGoForward = observation.canGoForward,
            failure = loadFailure,
            transport = completedTransport(SiteOrigin.parse(observation.url), loadFailure),
        )
    }

/**
 * The transport that survives a page *change*, which is not a navigation and earns nothing new.
 *
 * `doUpdateVisitedHistory` reports same-document URL changes, where the connection is the one
 * already confirmed — so preserving is correct. It is only correct while the origin is the same one,
 * hence the comparison: `SiteOrigin` equality is `ADR-004`'s full canonical tuple, so a change that
 * crosses scheme, host or port drops to [TransportSecurity.UNKNOWN] instead of carrying a previous
 * origin's green onto a new one.
 */
private fun BrowserState.transportAcross(url: String): TransportSecurity =
    if (mode.observedOrigin != null && mode.observedOrigin == SiteOrigin.parse(url)) {
        transport
    } else {
        TransportSecurity.UNKNOWN
    }

@Suppress("LongParameterList")
private fun BrowserState.observePage(
    url: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    failure: BrowserLoadFailure?,
    transport: TransportSecurity,
): BrowserState = copy(
    mode = mode.forObservedOrigin(SiteOrigin.parse(url)),
    displayedUrl = url,
    addressText = url,
    isLoading = isLoading,
    canGoBack = canGoBack,
    canGoForward = canGoForward,
    loadFailure = failure,
    transport = transport,
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

internal fun BrowserState.deactivateSiteSkin(): BrowserState = when (val current = mode) {
    is BrowserMode.Integrated -> copy(mode = BrowserMode.Regular(current.origin))
    else -> this
}

/**
 * A failed navigation never preserves the previous transport, whatever failed.
 *
 * A certificate failure is recorded as itself so a later page-information surface can say which of
 * the neutral states this was; every other failure kind leaves no confirmed transport at all, which
 * is [TransportSecurity.UNKNOWN] rather than a certificate claim the browser did not observe. What
 * both share is the thing that matters: no green survives a failure.
 */
private fun BrowserState.observeFailure(failure: BrowserObservation.PageFailed): BrowserState {
    val retryUrl = resolveAddressInput(failure.url)?.takeIf { it == failure.url }
    val origin = retryUrl?.let(dev.siteskin.core.origin.SiteOrigin::parse)
    return copy(
        isLoading = false,
        loadFailure = BrowserLoadFailure(failure.kind, origin?.registrableDomain, retryUrl),
        transport = if (failure.kind == LoadErrorKind.TLS) {
            TransportSecurity.TLS_ERROR
        } else {
            TransportSecurity.UNKNOWN
        },
    )
}

internal fun BrowserState.navigateFromHome(url: String): BrowserState = copy(
    mode = BrowserMode.Regular(SiteOrigin.parse(url)),
    displayedUrl = url,
    addressText = url,
    isLoading = true,
    // A destination the browser has asked for and not yet reached. The URL says `https`; nothing
    // has confirmed it, and the scheme of a requested URL is exactly the evidence this ticket
    // removed from the classification.
    transport = TransportSecurity.UNKNOWN,
)
