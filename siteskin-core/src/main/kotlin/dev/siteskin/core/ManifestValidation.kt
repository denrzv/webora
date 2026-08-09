package dev.siteskin.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A stable, machine-readable SiteSkin diagnostic code. */
public enum class DiagnosticCode(public val value: String) {
    /** The manifest body exceeds the transport size limit. */
    SIZE_EXCEEDED("SS-E-SIZE-EXCEEDED"),

    /** The manifest body is not valid JSON. */
    PARSE("SS-E-PARSE"),

    /** The manifest declares a well-formed major this implementation does not support. */
    VERSION_UNSUPPORTED("SS-E-VERSION-UNSUPPORTED"),

    /** The manifest does not satisfy the SiteSkin 1.x structural schema. */
    SCHEMA_INVALID("SS-E-SCHEMA-INVALID"),

    /** A URL resolves outside the serving origin. */
    ORIGIN_MISMATCH("SS-E-ORIGIN-MISMATCH"),

    /** A URI scheme is outside the browser-owned allow-list. */
    SCHEME_DENIED("SS-E-SCHEME-DENIED"),

    /** An asset is not served by the manifest's exact origin. */
    ASSET_CROSS_ORIGIN("SS-E-ASSET-CROSS-ORIGIN"),

    /** An action type is outside the browser-owned allow-list. */
    ACTION_UNKNOWN("SS-E-ACTION-UNKNOWN"),

    /** A later collection item repeats an earlier identifier. */
    DUPLICATE_ID("SS-E-DUPLICATE-ID"),

    /** A bounded string or collection was truncated. */
    LIMIT_TRUNCATED("SS-W-LIMIT-TRUNCATED"),

    /** A manifest color was corrected to preserve browser-owned contrast. */
    CONTRAST_CORRECTED("SS-W-CONTRAST-CORRECTED"),

    /** An unrecognized field was ignored. */
    FIELD_UNKNOWN("SS-W-FIELD-UNKNOWN"),

    /** An unrecognized icon was replaced with a generic glyph. */
    ICON_UNKNOWN("SS-W-ICON-UNKNOWN"),
}

/** One diagnostic produced while validating an untrusted manifest. */
public data class ManifestDiagnostic(
    public val code: DiagnosticCode,
    public val pointer: String? = null,
)

/**
 * The outcome of validating an untrusted manifest.
 *
 * A valid result proves only version and structural validity. It does not establish origin binding,
 * security validity, or construct a trusted SiteSkin configuration.
 */
public data class ManifestValidationResult(
    public val errors: List<ManifestDiagnostic>,
    public val warnings: List<ManifestDiagnostic>,
) {
    /** Whether validation found no rejecting diagnostics. */
    public val isValid: Boolean get() = errors.isEmpty()
}

/** Validates an already parsed JSON value against the SiteSkin 1.x version and structural rules. */
public object SchemaValidator {
    /**
     * Validates [manifest] without performing parsing, origin binding, or security normalization.
     * Unsupported canonical majors short-circuit before the document's v1 shape is interpreted.
     */
    public fun validate(manifest: JsonElement): ManifestValidationResult {
        if (hasUnsupportedMajor(manifest)) {
            return failure(DiagnosticCode.VERSION_UNSUPPORTED)
        }

        return if (ManifestStructure.isValid(manifest)) {
            ManifestValidationResult(emptyList(), emptyList())
        } else {
            failure(DiagnosticCode.SCHEMA_INVALID)
        }
    }

    private fun hasUnsupportedMajor(manifest: JsonElement): Boolean {
        val root = manifest as? JsonObject ?: return false
        val version = root["schemaVersion"] as? JsonPrimitive ?: return false
        if (!version.isString || !VERSION_PATTERN.matches(version.content)) return false
        return version.content.substringBefore('.') != SiteSkinSchema.SUPPORTED_MAJOR.toString()
    }

    private fun failure(code: DiagnosticCode): ManifestValidationResult =
        ManifestValidationResult(listOf(ManifestDiagnostic(code)), emptyList())

    private val VERSION_PATTERN = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
}

private object ManifestStructure {
    private val versionPattern = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
    private val identifierPattern = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
    private val colorPattern = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
    private val iconPattern = Regex("^[a-z][a-z0-9_]{0,31}$")
    private val actionTypePattern = Regex("^[a-z][a-z_]{0,31}$")
    private val matchPattern = Regex("^/([^/].*)?$")

    fun isValid(element: JsonElement): Boolean {
        val root = element as? JsonObject ?: return false
        return root.requiredString("schemaVersion", versionPattern) &&
            root.requiredObject("site", ::validSite) &&
            root.optionalObject("branding", ::validBranding) &&
            root.optionalObject("toolbar", ::validToolbar) &&
            root.optionalArray("bottomNavigation", ::validNavigation) &&
            root.optionalArray("menu", ::validNavigation) &&
            root.optionalArray("quickActions", ::validNavigation)
    }

    private fun validSite(site: JsonObject): Boolean =
        site.requiredString("id", identifierPattern) &&
            site.requiredNonEmptyString("name") &&
            site.optionalNonEmptyString("shortName") &&
            site.optionalNonEmptyString("homeUrl")

    private fun validBranding(branding: JsonObject): Boolean =
        branding.optionalString("primaryColor", colorPattern) &&
            branding.optionalString("secondaryColor", colorPattern) &&
            branding.optionalString("backgroundColor", colorPattern) &&
            branding.optionalString("textColor", colorPattern) &&
            branding.optionalNonEmptyString("logoUrl")

    private fun validToolbar(toolbar: JsonObject): Boolean =
        toolbar.optionalString("title") && toolbar.optionalString("subtitle")

    private fun validNavigation(items: JsonArray): Boolean = items.all { item ->
        val navigation = item as? JsonObject ?: return@all false
        navigation.requiredString("id", identifierPattern) &&
            navigation.requiredNonEmptyString("label") &&
            navigation.optionalString("icon", iconPattern) &&
            navigation.requiredObject("action", ::validAction) &&
            navigation.optionalArray("match") { patterns ->
                patterns.all { it.stringValue()?.matches(matchPattern) == true }
            }
    }

    private fun validAction(action: JsonObject): Boolean {
        val type = action["type"].stringValue() ?: return false
        if (!type.matches(actionTypePattern)) return false
        if (!action.optionalNonEmptyString("url") || !action.optionalNonEmptyString("value")) return false

        return when (type) {
            "internal_url", "external_url" -> action.hasNonEmptyString("url")
            "phone", "email", "map" -> action.hasNonEmptyString("value")
            else -> true
        }
    }
}

private fun JsonObject.requiredString(name: String, pattern: Regex? = null): Boolean =
    this[name].stringValue()?.let { pattern == null || it.matches(pattern) } == true

private fun JsonObject.requiredNonEmptyString(name: String): Boolean = hasNonEmptyString(name)

private fun JsonObject.hasNonEmptyString(name: String): Boolean =
    this[name].stringValue()?.isNotEmpty() == true

private fun JsonObject.optionalString(name: String, pattern: Regex? = null): Boolean =
    this[name]?.let { value -> value.stringValue()?.let { pattern == null || it.matches(pattern) } == true } ?: true

private fun JsonObject.optionalNonEmptyString(name: String): Boolean =
    this[name]?.stringValue()?.isNotEmpty() ?: !containsKey(name)

private fun JsonObject.requiredObject(name: String, validator: (JsonObject) -> Boolean): Boolean =
    (this[name] as? JsonObject)?.let(validator) == true

private fun JsonObject.optionalObject(name: String, validator: (JsonObject) -> Boolean): Boolean =
    this[name]?.let { (it as? JsonObject)?.let(validator) == true } ?: true

private fun JsonObject.optionalArray(name: String, validator: (JsonArray) -> Boolean): Boolean =
    this[name]?.let { (it as? JsonArray)?.let(validator) == true } ?: true

private fun JsonElement?.stringValue(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content
