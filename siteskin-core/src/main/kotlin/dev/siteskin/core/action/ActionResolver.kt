package dev.siteskin.core.action

import dev.siteskin.core.model.NormalizedAction
import dev.siteskin.core.model.SiteConfiguration

/** Converts a trusted inert action into a closed browser-owned effect. */
public object ActionResolver {
    /**
     * Resolves [action], using trusted [site] and browser-observed [currentPageUrl] as context.
     * Returns null if a future internal change supplies an unknown or inconsistent trusted value.
     */
    public fun resolve(
        action: NormalizedAction,
        site: SiteConfiguration,
        currentPageUrl: String,
    ): ResolvedAction? = when (action.type) {
        "internal_url", "external_url", "phone", "email", "map" -> resolvePayloadAction(action)
        "share" -> ResolvedAction.Share(currentPageUrl)
        "home" -> ResolvedAction.NavigateInternal(site.homeUrl)
        "refresh" -> ResolvedAction.Refresh
        "open_menu" -> ResolvedAction.OpenMenu
        else -> null
    }

    private fun resolvePayloadAction(action: NormalizedAction): ResolvedAction? = when (action.type) {
        "internal_url" -> action.url?.let(ResolvedAction::NavigateInternal)
        "external_url" -> action.url?.let(ResolvedAction::NavigateExternal)
        "phone" -> action.value?.let(ResolvedAction::Dial)
        "email" -> action.value?.let(ResolvedAction::ComposeEmail)
        "map" -> action.value?.let(ResolvedAction::OpenMap)
        else -> null
    }
}
