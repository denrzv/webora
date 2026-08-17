package app.webora.browser.browser

import dev.siteskin.core.origin.SiteOrigin

/**
 * What the browser has positively observed about the current main frame's transport.
 *
 * Four values rather than two, because `UX-021` renders this as a shield and a shield is a stronger
 * claim than the 12 sp word `Secure` was. The old two-valued enum was classified from one fact —
 * `origin.scheme == "https"` — which is true of a page whose certificate was rejected, true of a
 * page that has not finished loading, and true of a new tab pointed at an HTTPS URL that never
 * arrived. Making the indicator emphatic without making the signal honest would have shipped a
 * worse defect than the one being fixed.
 *
 * [UNKNOWN] is declared first so it is the natural default: the value that looks uninitialised is
 * the one that claims nothing. [SECURE] is the only value that requires positive evidence, and
 * every route lacking that evidence lands on a neutral state rather than on a guess.
 *
 * `TLS_ERROR` and [UNKNOWN] render identically in this ticket — the visible mapping is two colours,
 * per the issue. They are kept distinct because a later page-information surface has to be able to
 * explain the difference, and a distinction not stored is a distinction that cannot be recovered.
 */
internal enum class TransportSecurity {
    /** No confirmed main-frame result: a new tab, a navigation in progress, or a non-TLS failure. */
    UNKNOWN,

    /** A successful committed HTTPS main frame with no recorded failure. Positive evidence only. */
    SECURE,

    /** A successful committed HTTP main frame. Known, and known to be insecure. */
    NOT_SECURE,

    /** A browser-observed certificate or handshake failure for this navigation. */
    TLS_ERROR,
}

internal data class SecurityPresentation(
    val registrableDomain: String,
    val transportSecurity: TransportSecurity,
)

/**
 * The committed main-frame origin, or `null` when there is none.
 *
 * Extracted so [BrowserState] can compare the origin it just observed against the one it had
 * committed without duplicating this `when`. Two copies of an origin extraction is two places for
 * the answer to differ, and this one decides whether a previous page's `SECURE` survives.
 */
internal val BrowserMode.observedOrigin: SiteOrigin?
    get() = when (this) {
        BrowserMode.Home -> null
        is BrowserMode.Regular -> origin
        is BrowserMode.Integrated -> origin
    }

/**
 * The transport state a *successfully completed* main frame earns.
 *
 * Called only from `BrowserObservation.MainFrameCompleted`, which is the successful-completion
 * signal — `HardenedWebViewClient.onPageFinished` fires `onPageChanged` unconditionally and
 * suppresses `onMainFrameCompleted` for a URL that already failed. A rule written on the page-change
 * signal instead would go green on a failed page.
 *
 * A named function rather than an inline `when` because it is the rule a negative control targets:
 * replacing its body with `SECURE` whenever the origin is non-null must fail three separate rows.
 *
 * The [failure] check is defence in depth behind the client's own suppression, which compares URLs
 * and can therefore be slipped past by a redirect that fails at a different URL than it completes
 * at.
 *
 * The scheme test is written as *only `https` earns `SECURE`* rather than as a two-armed allow-list,
 * because `SiteOrigin`'s constructor is private and [SiteOrigin.parse] already refuses every scheme
 * but `http` and `https`: a third arm would be unreachable by construction, and an unreachable arm
 * is a branch no test can cover honestly. If that allow-list ever widens, this `else` sends the new
 * scheme to the neutral shield, which is the fail-closed direction.
 */
internal fun completedTransport(
    origin: SiteOrigin?,
    failure: BrowserLoadFailure?,
): TransportSecurity = when {
    failure != null -> TransportSecurity.UNKNOWN
    origin == null -> TransportSecurity.UNKNOWN
    origin.scheme == "https" -> TransportSecurity.SECURE
    else -> TransportSecurity.NOT_SECURE
}

/**
 * Projects the committed origin and the browser's observed transport into one identity.
 *
 * This function no longer *classifies* anything — that moved to [completedTransport], behind an
 * observation. It projects, which is why both chrome variants can read it and cannot disagree about
 * one page.
 *
 * The `null` return is `A11Y-001`'s contract and is deliberately unchanged: no committed origin
 * yields no security node rather than a blank one. `BrowserScreen`'s `checkNotNull` on the
 * integrated path relies on it, and is safe because `Integrated.origin` is non-null by construction.
 */
internal fun securityPresentation(
    mode: BrowserMode,
    transport: TransportSecurity,
): SecurityPresentation? {
    val origin = mode.observedOrigin ?: return null
    return SecurityPresentation(
        registrableDomain = origin.registrableDomain,
        transportSecurity = transport,
    )
}
