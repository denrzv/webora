package dev.siteskin.core.model

/** An origin-bound SiteSkin configuration that has passed browser-owned security validation. */
public class SiteSkinConfiguration private constructor(
    public val schemaVersion: String,
    public val origin: String,
    public val site: SiteConfiguration,
    public val branding: BrandingConfiguration?,
    public val toolbar: ToolbarConfiguration?,
    public val bottomNavigation: List<NavigationItem>?,
    public val menu: List<NavigationItem>?,
    public val quickActions: List<NavigationItem>?,
) {
    internal companion object {
        fun create(
            schemaVersion: String,
            origin: String,
            site: SiteConfiguration,
            branding: BrandingConfiguration?,
            toolbar: ToolbarConfiguration?,
            bottomNavigation: List<NavigationItem>?,
            menu: List<NavigationItem>?,
            quickActions: List<NavigationItem>?,
        ): SiteSkinConfiguration = SiteSkinConfiguration(
            schemaVersion, origin, site, branding, toolbar,
            bottomNavigation?.toList(), menu?.toList(), quickActions?.toList(),
        )
    }
}

/** Trusted site identity and same-origin home location. */
public class SiteConfiguration internal constructor(
    public val id: String,
    public val name: String,
    public val shortName: String?,
    public val homeUrl: String,
)

/** Trusted, canonical branding values. */
public class BrandingConfiguration internal constructor(
    public val primaryColor: String?,
    public val secondaryColor: String?,
    public val backgroundColor: String?,
    public val textColor: String?,
    public val logoUrl: String?,
)

/** Bounded toolbar text. */
public class ToolbarConfiguration internal constructor(public val title: String?, public val subtitle: String?)

/** One normalized browser navigation or action item. */
public class NavigationItem internal constructor(
    public val id: String,
    public val label: String,
    public val icon: String?,
    public val action: NormalizedAction,
    public val match: List<String>,
)

/** An inert allow-listed action; CORE-005 maps this value to a sealed executable model. */
public class NormalizedAction internal constructor(
    public val type: String,
    public val url: String?,
    public val value: String?,
)
