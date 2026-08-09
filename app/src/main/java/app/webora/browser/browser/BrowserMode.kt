package app.webora.browser.browser

import dev.siteskin.core.model.SiteSkinConfiguration
import dev.siteskin.core.origin.SiteOrigin

/** The mutually exclusive chrome modes Webora can display. */
internal sealed interface BrowserMode {
    data object Home : BrowserMode

    data class Regular(val origin: SiteOrigin?) : BrowserMode

    data class Integrated(
        val origin: SiteOrigin,
        val configuration: SiteSkinConfiguration,
    ) : BrowserMode
}
