package dev.siteskin.core.model

/** An origin-bound SiteSkin configuration that has passed browser-owned security validation. */
public class SiteSkinConfiguration private constructor(
    public val schemaVersion: String,
    public val origin: String,
    public val site: SiteConfiguration,
    public val branding: BrandingConfiguration?,
    public val toolbar: ToolbarConfiguration?,
    public val presentation: PresentationConfiguration?,
    public val bottomNavigation: List<NavigationItem>?,
    public val menu: List<NavigationItem>?,
    public val quickActions: List<NavigationItem>?,
) {
    /**
     * The hub hint a consumer should act on, with an undeclared object and an explicit `auto`
     * collapsed into one value.
     *
     * `presentation` is nullable because the canonical result must stay silent when the manifest
     * was silent: a non-null default would add a `presentation` object to every `.expected.json`,
     * including `bloom-flowers.expected.json`, whose body is pinned by SHA-256 in two
     * repositories. That nullability is a serialization property and not something every reader
     * should have to restate — without this accessor each one writes `?: AUTO` and one of them
     * eventually writes something else.
     */
    public val hubPresentation: HubPresentation
        get() = presentation?.hub ?: HubPresentation.AUTO

    internal companion object {
        @Suppress("LongParameterList")
        fun create(
            schemaVersion: String,
            origin: String,
            site: SiteConfiguration,
            branding: BrandingConfiguration?,
            toolbar: ToolbarConfiguration?,
            presentation: PresentationConfiguration?,
            bottomNavigation: List<NavigationItem>?,
            menu: List<NavigationItem>?,
            quickActions: List<NavigationItem>?,
        ): SiteSkinConfiguration = SiteSkinConfiguration(
            schemaVersion, origin, site, branding, toolbar, presentation,
            bottomNavigation?.toList(), menu?.toList(), quickActions?.toList(),
        )
    }
}

/**
 * Trusted presentation hints.
 *
 * Present only when the manifest declared a `presentation` object, so a reader can tell "the site
 * asked for the default" from "the site said nothing" — which the inspector's requested-versus-
 * effective row needs and a collapsed value cannot recover.
 */
public class PresentationConfiguration internal constructor(
    public val hub: HubPresentation,
    /**
     * Ordered ids the site nominated for the browser's persistent integrated surface, already
     * bounded, de-duplicated, and resolved against this configuration's own items.
     *
     * Ids, deliberately — not copies of the items. A second copy would be a second thing to keep in
     * step with the first, and the point of the field is that it can only name what validation has
     * already produced. Empty when the manifest declared none, so no consumer writes
     * `?: emptyList()` twice and one of them eventually writes something else.
     */
    public val dock: List<String>,
)

/**
 * The closed set of hub presentations a manifest may ask for.
 *
 * Closed at the validator, so by the time any UI sees this an unknown token is indistinguishable
 * from an absent one. It names a component the browser already compiles and carries no dimension,
 * colour, asset, URL or callback; how each value is presented, and what `AUTO` resolves to, are
 * browser decisions made above this type.
 */
public enum class HubPresentation {
    /** No preference — the browser chooses. */
    AUTO,

    /** The radial quick-action arrangement. */
    BOUQUET,

    /** The start-side list drawer. */
    DRAWER,
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
