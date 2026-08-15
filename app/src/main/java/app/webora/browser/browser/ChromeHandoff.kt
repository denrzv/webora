package app.webora.browser.browser

/** Browser-authorised top chrome. Site data cannot construct or modify these contracts. */
internal enum class TopChrome(
    val showsSecurityIdentity: Boolean,
    val showsBackEscape: Boolean,
) {
    NONE(false, false),
    REGULAR(true, false),
    PROTECTED_INTEGRATED(true, true),
}

internal enum class ContentActions {
    NONE,
    SITESKIN,
}

internal enum class BottomChrome {
    BROWSER,
    SITESKIN,
}

/** Exactly one visible chrome-layer set derived from the browser-observed mode. */
internal data class ChromeHandoff(
    val top: TopChrome,
    val contentActions: ContentActions,
    val bottom: BottomChrome,
) {
    companion object {
        val HOME = ChromeHandoff(TopChrome.NONE, ContentActions.NONE, BottomChrome.BROWSER)
        val REGULAR = ChromeHandoff(TopChrome.REGULAR, ContentActions.NONE, BottomChrome.BROWSER)
        val INTEGRATED = ChromeHandoff(
            TopChrome.PROTECTED_INTEGRATED,
            ContentActions.SITESKIN,
            BottomChrome.SITESKIN,
        )
    }
}

internal fun BrowserMode.chromeHandoff(): ChromeHandoff = when (this) {
    BrowserMode.Home -> ChromeHandoff.HOME
    is BrowserMode.Regular -> ChromeHandoff.REGULAR
    is BrowserMode.Integrated -> ChromeHandoff.INTEGRATED
}

/** Only the selected browser-owned tab is allowed to project visible chrome. */
internal fun BrowserSession.chromeHandoff(): ChromeHandoff = activeTab.state.mode.chromeHandoff()
