package dev.siteskin.core

import dev.siteskin.core.model.BrandingConfiguration
import dev.siteskin.core.model.NavigationItem
import dev.siteskin.core.model.NormalizedAction
import dev.siteskin.core.model.SiteConfiguration
import dev.siteskin.core.model.SiteSkinConfiguration
import dev.siteskin.core.model.ToolbarConfiguration
import dev.siteskin.core.validate.ColorPolicy
import dev.siteskin.core.validate.OriginPolicy
import dev.siteskin.core.validate.TrustedOrigin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.BreakIterator

/** Result of applying browser-owned security policy to a structurally valid manifest. */
public data class SecurityValidationResult(
    public val configuration: SiteSkinConfiguration?,
    public val diagnostics: List<ManifestDiagnostic>,
) {
    /** Whether an origin-bound trusted configuration was constructed. */
    public val isValid: Boolean get() = configuration != null
}

/** Establishes the trust boundary between schema-valid remote JSON and browser-owned configuration. */
public object SecurityValidator {
    /**
     * Normalizes [manifest] against the browser-observed HTTPS [servingOrigin].
     * [manifest] must already have passed [SchemaValidator].
     */
    public fun validate(manifest: JsonObject, servingOrigin: String): SecurityValidationResult {
        val origin = OriginPolicy.parse(servingOrigin) ?: return SecurityValidationResult(null, emptyList())
        return runCatching { Normalizer(manifest, origin).normalize() }
            .getOrElse { SecurityValidationResult(null, emptyList()) }
    }
}

private class Normalizer(private val root: JsonObject, private val origin: TrustedOrigin) {
    private val diagnostics = mutableListOf<ManifestDiagnostic>()

    fun normalize(): SecurityValidationResult {
        val site = normalizeSite(root.objectValue("site"))
        val preparedBranding = root.objectValueOrNull("branding")?.let(::prepareBranding)
        val bottom = normalizeCollection("bottomNavigation", SiteSkinLimits.MAX_NAVIGATION_ITEMS)
        val menu = normalizeCollection("menu", SiteSkinLimits.MAX_MENU_ITEMS)
        val quick = normalizeCollection("quickActions", SiteSkinLimits.MAX_QUICK_ACTIONS)
        val toolbar = root.objectValueOrNull("toolbar")?.let(::normalizeToolbar)
        val branding = preparedBranding?.let(::normalizeColors)
        val configuration = SiteSkinConfiguration.create(
            root.stringValue("schemaVersion"), origin.value, site, branding, toolbar, bottom, menu, quick,
        )
        return SecurityValidationResult(configuration, diagnostics.toList())
    }

    private fun normalizeSite(value: JsonObject): SiteConfiguration {
        val home = value.stringValueOrNull("homeUrl")?.let(origin::resolveInternal)
        if (value.containsKey("homeUrl") && home == null) {
            diagnostic(DiagnosticCode.ORIGIN_MISMATCH, "/site/homeUrl")
        }
        return SiteConfiguration(
            value.stringValue("id"), value.stringValue("name"), value.stringValueOrNull("shortName"),
            home ?: "${origin.value}/",
        )
    }

    private fun prepareBranding(value: JsonObject): PendingBranding {
        val logo = value.stringValueOrNull("logoUrl")?.let(origin::resolveInternal)
        if (value.containsKey("logoUrl") && logo == null) {
            diagnostic(DiagnosticCode.ASSET_CROSS_ORIGIN, "/branding/logoUrl")
        }
        return PendingBranding(value, logo)
    }

    private fun normalizeColors(pending: PendingBranding): BrandingConfiguration {
        val text = pending.value.color("textColor")
        return BrandingConfiguration(
            correctColor(pending.value, "primaryColor", text, UI_CONTRAST),
            correctColor(pending.value, "secondaryColor", text, UI_CONTRAST),
            correctColor(pending.value, "backgroundColor", text, BODY_CONTRAST),
            text,
            pending.logo,
        )
    }

    private fun correctColor(value: JsonObject, field: String, text: String?, target: Double): String? {
        val color = value.color(field) ?: return null
        val corrected = ColorPolicy.correct(color, text ?: DEFAULT_TEXT_COLOR, target)
        if (corrected.corrected) diagnostic(DiagnosticCode.CONTRAST_CORRECTED, "/branding/$field")
        return corrected.color
    }

    private fun normalizeToolbar(value: JsonObject): ToolbarConfiguration = ToolbarConfiguration(
        value.stringValueOrNull("title")?.let { clamp(it, SiteSkinLimits.MAX_TITLE_LENGTH, "/toolbar/title") },
        value.stringValueOrNull("subtitle")
            ?.let { clamp(it, SiteSkinLimits.MAX_SUBTITLE_LENGTH, "/toolbar/subtitle") },
    )

    private fun normalizeCollection(name: String, limit: Int): List<NavigationItem>? {
        val source = root[name] as? JsonArray ?: return null
        val actionSafe = source.mapIndexedNotNull { index, element ->
            val item = element as JsonObject
            val action = normalizeAction(item.objectValue("action"), "/$name/$index") ?: return@mapIndexedNotNull null
            PendingItem(index, item, action)
        }
        val iconsSafe = actionSafe.map { pending -> pending.withIcon(normalizeIcon(pending, name)) }
        val unique = removeDuplicates(iconsSafe, name)
        val bounded = if (unique.size > limit) {
            diagnostic(DiagnosticCode.LIMIT_TRUNCATED, "/$name")
            unique.take(limit)
        } else {
            unique
        }
        return bounded.map { pending -> pending.toNavigationItem(name) }
    }

    private fun normalizeAction(action: JsonObject, itemPointer: String): NormalizedAction? {
        val type = action.stringValue("type")
        if (type !in ACTION_TYPES) {
            diagnostic(DiagnosticCode.ACTION_UNKNOWN, itemPointer)
            return null
        }
        return when (type) {
            "internal_url" -> actionUrl(
                type, action, itemPointer, origin::resolveInternal, DiagnosticCode.ORIGIN_MISMATCH,
            )
            "external_url" -> actionUrl(
                type, action, itemPointer, OriginPolicy::resolveExternal, DiagnosticCode.SCHEME_DENIED,
            )
            "phone", "email", "map" -> NormalizedAction(type, null, action.stringValue("value"))
            else -> NormalizedAction(type, null, null)
        }
    }

    private fun actionUrl(
        type: String,
        action: JsonObject,
        itemPointer: String,
        resolver: (String) -> String?,
        code: DiagnosticCode,
    ): NormalizedAction? {
        val resolved = resolver(action.stringValue("url"))
        if (resolved == null) diagnostic(code, "$itemPointer/action/url")
        return resolved?.let { NormalizedAction(type, it, null) }
    }

    private fun normalizeIcon(pending: PendingItem, collection: String): String? {
        val icon = pending.value.stringValueOrNull("icon") ?: return null
        if (icon in ICONS) return icon
        diagnostic(DiagnosticCode.ICON_UNKNOWN, "/$collection/${pending.index}/icon")
        return GENERIC_ICON
    }

    private fun removeDuplicates(items: List<PendingItem>, collection: String): List<PendingItem> {
        val seen = mutableSetOf<String>()
        return items.filter { item ->
            val added = seen.add(item.value.stringValue("id"))
            if (!added) diagnostic(DiagnosticCode.DUPLICATE_ID, "/$collection/${item.index}")
            added
        }
    }

    private fun PendingItem.toNavigationItem(collection: String): NavigationItem = NavigationItem(
        value.stringValue("id"),
        clamp(value.stringValue("label"), SiteSkinLimits.MAX_LABEL_LENGTH, "/$collection/$index/label"),
        icon,
        action,
        value["match"]?.let { element -> (element as JsonArray).map { (it as JsonPrimitive).content } }.orEmpty(),
    )

    private fun clamp(value: String, limit: Int, pointer: String): String {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(value) }
        var boundary = iterator.first()
        repeat(limit) {
            val next = iterator.next()
            if (next == BreakIterator.DONE) return value
            boundary = next
        }
        if (iterator.next() == BreakIterator.DONE) return value
        diagnostic(DiagnosticCode.LIMIT_TRUNCATED, pointer)
        return value.substring(0, boundary)
    }

    private fun diagnostic(code: DiagnosticCode, pointer: String) {
        diagnostics += ManifestDiagnostic(code, pointer)
    }
}

private data class PendingBranding(val value: JsonObject, val logo: String?)

private data class PendingItem(
    val index: Int,
    val value: JsonObject,
    val action: NormalizedAction,
    val icon: String? = null,
) {
    fun withIcon(normalizedIcon: String?): PendingItem = copy(icon = normalizedIcon)
}

private fun JsonObject.objectValue(name: String): JsonObject = this[name] as JsonObject
private fun JsonObject.objectValueOrNull(name: String): JsonObject? = this[name] as? JsonObject
private fun JsonObject.stringValue(name: String): String = (this[name] as JsonPrimitive).content
private fun JsonObject.stringValueOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.content
private fun JsonObject.color(name: String): String? = stringValueOrNull(name)?.let(ColorPolicy::canonicalize)

private const val BODY_CONTRAST = 4.5
private const val UI_CONTRAST = 3.0
private const val DEFAULT_TEXT_COLOR = "#000000"
private const val GENERIC_ICON = "generic"
private val ACTION_TYPES = setOf(
    "internal_url", "external_url", "phone", "email", "map", "share", "home", "refresh", "open_menu",
)
private val ICONS = setOf("home", "grid_view", "shopping_cart", "person", "call", "share", "menu")
