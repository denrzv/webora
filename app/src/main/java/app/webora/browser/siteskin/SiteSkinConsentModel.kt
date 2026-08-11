package app.webora.browser.siteskin

import androidx.compose.ui.graphics.Color
import dev.siteskin.core.SiteSkinLimits
import dev.siteskin.core.model.SiteSkinConfiguration

/**
 * Everything the first-use consent sheet may show about the manifest it is asking permission to
 * apply — and, by being a closed six-field projection, everything it may not.
 *
 * `ADR-011` asks the user before a validated manifest restyles the chrome, and until `UX-006` the
 * sheet showed the origin beside two compiled sentences. The user was therefore asked to approve a
 * *specific* manifest and shown nothing from it. This is the projection that closes that gap.
 *
 * It is also the sheet's security boundary, because this is the one surface where website text is
 * displayed **before** the user has agreed to anything — a manifest reading "Your Bank" does more
 * damage above an Allow button than it does in a top bar the user has already accepted. `ADR-006`
 * and `HARDEN-002` settle the rule for active chrome: branding is presentation beside browser
 * identity, never identity itself. The same rule holds here, and the dialog enforces it by taking
 * this model rather than a [SiteSkinConfiguration], so there is no field a later edit can reach for
 * without coming through here.
 *
 * **What is deliberately excluded, and why:**
 * - `branding.logoUrl` — previewing the logo means fetching it, so a user pressing *Never* would
 *   still have caused a second request to the site. Refusal has to cost the site nothing beyond the
 *   discovery request it already received, which is why the sheet has a colour and no image.
 * - `site.homeUrl` and every `action` payload — the sheet describes what would change; it does not
 *   offer navigation. An actionable element in a consent dialog is a way to be used without being
 *   granted.
 * - Item `label`s — listing the tabs by name is more informative than counting them, and it is also
 *   five lines of up to [SiteSkinLimits.MAX_LABEL_LENGTH] attacker-chosen characters stacked
 *   directly above an Allow button. That is a site-authored message, not a description. Counting is
 *   the form that stays a description.
 *
 * Every value is bounded by a constant read from `:siteskin-core`, never a copy: a second spelling
 * of a limit is a second thing to keep in step.
 */
internal data class SiteSkinConsentModel(
    val title: String,
    val subtitle: String?,
    val brandColor: Color?,
    val navigationCount: Int,
    val quickActionCount: Int,
    val menuCount: Int,
) {
    companion object {
        /**
         * @param darkTheme the browser's own light/dark choice, from `isSystemInDarkTheme()`. A
         *   manifest supplies colours; it does not decide whether the user's preference applies.
         */
        fun from(configuration: SiteSkinConfiguration, darkTheme: Boolean): SiteSkinConsentModel {
            val toolbar = configuration.toolbar
            return SiteSkinConsentModel(
                // The same resolution as SiteSkinTopBarModel, because the sheet is a promise about
                // the chrome: showing site.name when toolbar.title is what would render makes the
                // preview wrong in precisely the case a site would exploit. MAX_TITLE_LENGTH bounds
                // both spellings — core clamps toolbar.title to it already, and site.name is the
                // one displayed string core never clamps at all.
                title = untrustedText(
                    toolbar?.title ?: configuration.site.name,
                    SiteSkinLimits.MAX_TITLE_LENGTH,
                ),
                subtitle = untrustedText(toolbar?.subtitle, SiteSkinLimits.MAX_SUBTITLE_LENGTH)
                    .ifEmpty { null },
                brandColor = configuration.brandColor(darkTheme),
                navigationCount = configuration.bottomNavigation
                    .boundedCount(SiteSkinLimits.MAX_NAVIGATION_ITEMS),
                quickActionCount = configuration.quickActions
                    .boundedCount(SiteSkinLimits.MAX_QUICK_ACTIONS),
                menuCount = configuration.menu.boundedCount(SiteSkinLimits.MAX_MENU_ITEMS),
            )
        }
    }
}

/**
 * The swatch colour, or `null` when the site asked for none.
 *
 * Both halves matter. It is absent unless the manifest actually supplied `primaryColor`, because
 * [SiteSkinTheme.from] substitutes compiled Webora defaults for absent branding — an unconditional
 * swatch would show Webora's own indigo and attribute it to a site that requested nothing, and
 * attribution is the one thing this sheet exists to get right.
 *
 * When present it is the projected value rather than the raw hex, for the mirror-image reason:
 * `guardContainer` may move a failing colour before the chrome ever paints it, so the raw value is a
 * colour the browser has already decided not to use. Previewing it would make the sheet a less
 * accurate promise than the chrome it describes.
 */
private fun SiteSkinConfiguration.brandColor(darkTheme: Boolean): Color? =
    branding?.primaryColor?.let { SiteSkinTheme.from(this).scheme(darkTheme).primary }

/**
 * Defense in depth over core's own truncation, on the same terms `SiteSkinChromeModel` applies.
 *
 * The count has to describe the chrome that would render, not the manifest that was fetched, or the
 * sheet promises six tabs and five appear.
 */
private fun List<*>?.boundedCount(limit: Int): Int = orEmpty().size.coerceAtMost(limit)
