package dev.siteskin.core.manifest

import kotlinx.serialization.Serializable

/** Untrusted, schema-shaped data decoded from a SiteSkin manifest. */
@Serializable
public data class SiteSkinManifestDto(
    public val schemaVersion: String? = null,
    public val site: SiteDto? = null,
    public val branding: BrandingDto? = null,
    public val toolbar: ToolbarDto? = null,
    public val presentation: PresentationDto? = null,
    public val bottomNavigation: List<NavigationItemDto>? = null,
    public val menu: List<NavigationItemDto>? = null,
    public val quickActions: List<NavigationItemDto>? = null,
)

/** Untrusted site identity and navigation defaults. */
@Serializable
public data class SiteDto(
    public val id: String? = null,
    public val name: String? = null,
    public val shortName: String? = null,
    public val homeUrl: String? = null,
)

/** Untrusted site-controlled visual values. */
@Serializable
public data class BrandingDto(
    public val primaryColor: String? = null,
    public val secondaryColor: String? = null,
    public val backgroundColor: String? = null,
    public val textColor: String? = null,
    public val logoUrl: String? = null,
)

/** Untrusted toolbar copy; browser security chrome is intentionally absent. */
@Serializable
public data class ToolbarDto(
    public val title: String? = null,
    public val subtitle: String? = null,
)

/**
 * Untrusted presentation hints.
 *
 * A hint names a component the browser already compiles. It carries no dimension, colour, asset,
 * URL or callback, and there is deliberately no generic key/value map here — that would be a second
 * manifest-controlled surface with no allow-list behind it.
 */
@Serializable
public data class PresentationDto(
    public val hub: String? = null,
)

/** Untrusted navigation, menu, or quick-action item. */
@Serializable
public data class NavigationItemDto(
    public val id: String? = null,
    public val label: String? = null,
    public val icon: String? = null,
    public val action: ActionDto? = null,
    public val match: List<String>? = null,
)

/** Untrusted action description awaiting allow-list and origin validation. */
@Serializable
public data class ActionDto(
    public val type: String? = null,
    public val url: String? = null,
    public val value: String? = null,
)
