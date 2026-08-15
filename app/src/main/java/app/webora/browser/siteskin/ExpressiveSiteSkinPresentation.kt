package app.webora.browser.siteskin

import dev.siteskin.core.model.SiteSkinConfiguration

/** Browser-selected motion behavior for expressive SiteSkin chrome. */
internal enum class ExpressiveMotionPolicy {
    STANDARD,
    REDUCED,
}

/**
 * The complete website-influenceable presentation passed to M9's expressive chrome primitives.
 *
 * [configuration] has already crossed core's validation and exact-origin boundary. Its normalized
 * branding can select values only in [SiteSkinColorScheme]; dark theme and reduced motion remain
 * explicit browser/platform choices. Geometry, identity, browser controls, assets, actions and
 * callbacks are intentionally absent rather than documented as unsafe to use.
 */
@ConsistentCopyVisibility
internal data class ExpressiveSiteSkinPresentation private constructor(
    val colors: SiteSkinColorScheme,
    val motion: ExpressiveMotionPolicy,
) {
    companion object {
        fun from(
            configuration: SiteSkinConfiguration,
            darkTheme: Boolean,
            reducedMotion: Boolean,
        ): ExpressiveSiteSkinPresentation = ExpressiveSiteSkinPresentation(
            colors = SiteSkinTheme.from(configuration).scheme(darkTheme),
            motion = if (reducedMotion) ExpressiveMotionPolicy.REDUCED else ExpressiveMotionPolicy.STANDARD,
        )
    }
}
