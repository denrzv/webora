package app.webora.browser.inspector

import androidx.compose.ui.graphics.Color
import app.webora.browser.siteskin.BrandAsset
import app.webora.browser.siteskin.SiteConsentDecision
import app.webora.browser.siteskin.SiteSkinChromeModel
import app.webora.browser.siteskin.SiteSkinColorScheme
import app.webora.browser.siteskin.SiteSkinItemModel
import app.webora.browser.siteskin.SiteSkinTheme
import app.webora.browser.siteskin.scheme
import dev.siteskin.core.model.SiteSkinConfiguration
import dev.siteskin.core.origin.SiteOrigin
import java.util.Locale
import kotlin.math.roundToInt

/** What the browser knows about the origin the developer is currently looking at. */
internal data class InspectorBrowserState(
    val origin: SiteOrigin?,
    val pageUrl: String,
    val configuration: SiteSkinConfiguration?,
    val consent: SiteConsentDecision?,
    val siteSkinEnabled: Boolean,
    val brandAsset: BrandAsset?,
    val darkTheme: Boolean,
)

/** Everything the panel renders, assembled once and outside composition. */
internal data class InspectorSnapshot(
    val origin: String?,
    val activation: InspectorActivation,
    val consent: SiteConsentDecision?,
    val siteSkinEnabled: Boolean,
    val brandAsset: InspectorBrandAsset,
    val record: ManifestTraceRecord?,
    val applied: InspectorAppliedChrome?,
)

/** The chrome that a trusted configuration actually produced, as opposed to what it asked for. */
internal data class InspectorAppliedChrome(
    val siteName: String,
    val siteId: String,
    val homeUrl: String,
    val activeNavigationId: String?,
    val counts: List<InspectorItemCount>,
    val navigation: List<InspectorItem>,
    val theme: InspectorTheme,
)

/**
 * How many items a collection has in the trusted configuration, and how many the chrome renders.
 *
 * These should always match, and that is the useful thing about showing them. Core already truncated
 * over-limit collections during normalization — an over-eager manifest reaches the app layer already
 * bounded, and `SS-W-LIMIT-TRUNCATED` in the diagnostics is the only record that it was ever longer.
 * `SiteSkinChromeModel`'s own 5/5/20 cap is defence in depth on top of that, so [diverged] firing
 * means the two layers disagree, which is a bug rather than a manifest problem.
 */
internal data class InspectorItemCount(val collection: InspectorCollection, val trusted: Int, val rendered: Int) {
    val diverged: Boolean get() = trusted != rendered
}

internal data class InspectorItem(val id: String, val label: String, val actionType: String, val active: Boolean)

internal data class InspectorTheme(val darkTheme: Boolean, val roles: List<InspectorColorRole>)

/**
 * One colour role, as it arrived from core and as the projection paints it.
 *
 * [trusted] is the *normalized* value, not the value the manifest wrote: core's security validation
 * may already have corrected it, and `SS-W-CONTRAST-CORRECTED` in the record's diagnostics is the
 * only place that correction is visible at all. It is `null` when the browser chose the value rather
 * than the manifest — an omitted branding field, or the content colour of the dark projection.
 *
 * [applied] is what `SKIN-001`'s projection produces on top: identical in the light projection when
 * core left nothing to correct, and deliberately different in the dark one, whose surface is derived
 * from the manifest colour rather than taken from it.
 */
internal data class InspectorColorRole(
    val role: InspectorColorRoleName,
    val trusted: String?,
    val applied: String,
)

internal enum class InspectorCollection { BOTTOM_NAVIGATION, QUICK_ACTIONS, MENU }

internal enum class InspectorColorRoleName {
    PRIMARY,
    ON_PRIMARY,
    SECONDARY,
    ON_SECONDARY,
    BACKGROUND,
    ON_BACKGROUND,
}

internal enum class InspectorBrandAsset { NONE, DECODED_BITMAP, MONOGRAM }

/** Where this origin stands with respect to SiteSkin activation. */
internal enum class InspectorActivation {
    /** The global preference is off, so discovery never ran. */
    DISABLED,

    /** Nothing has been recorded for this origin yet. */
    PENDING,

    /** No manifest was accepted for this origin. */
    UNAVAILABLE,

    /** A manifest was accepted and the user has not been asked yet. */
    AWAITING_CONSENT,

    /** A manifest was accepted and the user refused it for this origin. */
    REFUSED,

    /** Branding is applied. */
    INTEGRATED,
}

/**
 * Assembles the panel's view of one origin.
 *
 * Pure, and deliberately outside composition: it calls `SiteSkinChromeModel.from` and
 * `SiteSkinTheme.from` — the same functions the chrome calls — rather than guessing what they
 * produced. Recomputing is cheaper than plumbing a second copy of the answer through the browser,
 * and a second copy is a second thing that can be stale.
 */
internal fun inspectorSnapshot(
    state: InspectorBrowserState,
    record: ManifestTraceRecord?,
): InspectorSnapshot = InspectorSnapshot(
    origin = state.origin?.canonical,
    activation = activation(state, record),
    consent = state.consent,
    siteSkinEnabled = state.siteSkinEnabled,
    brandAsset = when (state.brandAsset) {
        is BrandAsset.BitmapAsset -> InspectorBrandAsset.DECODED_BITMAP
        is BrandAsset.Monogram -> InspectorBrandAsset.MONOGRAM
        null -> InspectorBrandAsset.NONE
    },
    record = record,
    applied = state.configuration?.let { appliedChrome(it, state.pageUrl, state.darkTheme) },
)

private fun activation(state: InspectorBrowserState, record: ManifestTraceRecord?): InspectorActivation = when {
    !state.siteSkinEnabled -> InspectorActivation.DISABLED
    state.configuration != null -> InspectorActivation.INTEGRATED
    record == null -> InspectorActivation.PENDING
    record.validation.result != TraceValidationResult.ACCEPTED -> InspectorActivation.UNAVAILABLE
    state.consent == SiteConsentDecision.NEVER -> InspectorActivation.REFUSED
    state.consent == null -> InspectorActivation.AWAITING_CONSENT
    else -> InspectorActivation.UNAVAILABLE
}

private fun appliedChrome(
    configuration: SiteSkinConfiguration,
    pageUrl: String,
    darkTheme: Boolean,
): InspectorAppliedChrome {
    val chrome = SiteSkinChromeModel.from(configuration, pageUrl)
    return InspectorAppliedChrome(
        siteName = configuration.site.name,
        siteId = configuration.site.id,
        homeUrl = configuration.site.homeUrl,
        activeNavigationId = chrome.bottomNavigation.firstOrNull { it.isActive }?.id,
        counts = listOf(
            count(InspectorCollection.BOTTOM_NAVIGATION, configuration.bottomNavigation?.size, chrome.bottomNavigation),
            count(InspectorCollection.QUICK_ACTIONS, configuration.quickActions?.size, chrome.quickActions),
            count(InspectorCollection.MENU, configuration.menu?.size, chrome.siteMenu),
        ),
        navigation = chrome.bottomNavigation.map {
            InspectorItem(it.id, it.label, it.item.action.type, it.isActive)
        },
        theme = theme(configuration, darkTheme),
    )
}

private fun count(
    collection: InspectorCollection,
    trusted: Int?,
    rendered: List<SiteSkinItemModel>,
) = InspectorItemCount(collection, trusted ?: 0, rendered.size)

/**
 * The colour roles the browser will actually paint, beside the trusted values that fed them.
 *
 * The dark/light choice comes from [darkTheme], which the browser derives from the system setting.
 * No manifest field participates: a site supplies colours and does not decide whether the user's
 * dark-theme preference applies to them.
 */
private fun theme(configuration: SiteSkinConfiguration, darkTheme: Boolean): InspectorTheme {
    val branding = configuration.branding
    val scheme: SiteSkinColorScheme = SiteSkinTheme.from(configuration).scheme(darkTheme)
    // In the dark projection the content colour is browser-derived, so the manifest requested
    // nothing for it and the panel must not imply otherwise.
    val trustedContent = branding?.textColor.takeUnless { darkTheme }
    return InspectorTheme(
        darkTheme = darkTheme,
        roles = listOf(
            InspectorColorRole(InspectorColorRoleName.PRIMARY, branding?.primaryColor, scheme.primary.hex()),
            InspectorColorRole(InspectorColorRoleName.ON_PRIMARY, trustedContent, scheme.onPrimary.hex()),
            InspectorColorRole(InspectorColorRoleName.SECONDARY, branding?.secondaryColor, scheme.secondary.hex()),
            InspectorColorRole(InspectorColorRoleName.ON_SECONDARY, trustedContent, scheme.onSecondary.hex()),
            // The background of the dark projection is derived from the manifest's colour rather
            // than taken from it, so it is browser-chosen in exactly the same sense.
            InspectorColorRole(
                InspectorColorRoleName.BACKGROUND,
                branding?.backgroundColor.takeUnless { darkTheme },
                scheme.background.hex(),
            ),
            InspectorColorRole(InspectorColorRoleName.ON_BACKGROUND, trustedContent, scheme.onBackground.hex()),
        ),
    )
}

private fun Color.hex(): String = String.format(
    Locale.ROOT,
    "#%02X%02X%02X",
    (red * MAX_CHANNEL).roundToInt(),
    (green * MAX_CHANNEL).roundToInt(),
    (blue * MAX_CHANNEL).roundToInt(),
)

private const val MAX_CHANNEL = 255
