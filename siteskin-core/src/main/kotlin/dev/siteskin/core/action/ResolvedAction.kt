package dev.siteskin.core.action

/** A closed browser effect resolved from a trusted SiteSkin action. */
public sealed interface ResolvedAction {
    /** Navigates the current WebView to a trusted same-origin [url]. */
    public data class NavigateInternal(public val url: String) : ResolvedAction

    /** Requests confirmed navigation away from the current origin to an HTTPS [url]. */
    public data class NavigateExternal(public val url: String) : ResolvedAction

    /** Opens the browser-selected dialer with [value] prefilled without placing a call. */
    public data class Dial(public val value: String) : ResolvedAction

    /** Opens the browser-selected email composer with [value] as inert address data. */
    public data class ComposeEmail(public val value: String) : ResolvedAction

    /** Opens the browser-selected map handler with [value] as inert location data. */
    public data class OpenMap(public val value: String) : ResolvedAction

    /** Opens the system share UI for the browser-observed [pageUrl]. */
    public data class Share(public val pageUrl: String) : ResolvedAction

    /** Reloads the current WebView. */
    public data object Refresh : ResolvedAction

    /** Opens the browser-owned SiteSkin menu. */
    public data object OpenMenu : ResolvedAction
}
