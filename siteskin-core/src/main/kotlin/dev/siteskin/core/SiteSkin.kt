package dev.siteskin.core

/**
 * Schema versions this implementation understands.
 *
 * Compatibility policy (SPEC-002): a manifest is accepted when its major version matches
 * [SUPPORTED_MAJOR]. A newer minor version is accepted and its unknown fields ignored, because
 * minor versions are additive by definition. An unknown major version is rejected outright and the
 * browser stays in regular mode — silently reinterpreting a format you do not know is how a
 * security boundary becomes a guess.
 */
public object SiteSkinSchema {
    public const val SUPPORTED_MAJOR: Int = 1
    public const val CURRENT: String = "1.0"

    /** Where a manifest is discovered, relative to the origin root. */
    public const val WELL_KNOWN_PATH: String = "/.well-known/siteskin.json"
}

/**
 * Hard limits applied before and during validation.
 *
 * These exist to bound memory and to prevent a manifest from producing a pathological layout —
 * a navigation bar with two hundred items is a denial of service against the user, not a design
 * choice. Over-limit collections are truncated with a warning rather than rejected, so a slightly
 * over-eager site still gets a working integration.
 *
 * [MAX_MANIFEST_BYTES] is different: it is enforced *before* parsing, so an oversized payload is
 * never fully read into memory. [MAX_JSON_DEPTH] bounds structural nesting before a JSON tree is
 * constructed, preventing parser-stack exhaustion from hostile but size-compliant input.
 */
public object SiteSkinLimits {
    public const val MAX_MANIFEST_BYTES: Int = 128 * 1024
    public const val MAX_JSON_DEPTH: Int = 64
    public const val MAX_NAVIGATION_ITEMS: Int = 5
    public const val MAX_MENU_ITEMS: Int = 20
    public const val MAX_QUICK_ACTIONS: Int = 5

    /**
     * Ids a manifest may nominate for the browser's persistent integrated surface.
     *
     * Three because the browser reserves the remaining two of its five slots for its own brand hub
     * and overflow. This is a protocol limit rather than a layout constant: it bounds what a site
     * may *ask for*, and the browser remains free to project fewer.
     */
    public const val MAX_DOCK_ITEMS: Int = 3
    public const val MAX_TITLE_LENGTH: Int = 64
    public const val MAX_SUBTITLE_LENGTH: Int = 128
    public const val MAX_LABEL_LENGTH: Int = 32
    public const val MAX_REDIRECTS: Int = 2
    public const val MAX_CACHE_TTL_SECONDS: Long = 24 * 60 * 60
}

/**
 * Supplies raw manifest bytes for an origin.
 *
 * Declared here and implemented in `:app` with OkHttp. Core stays free of any HTTP client — and of
 * Android — so that validation is testable without a network or an emulator.
 */
public fun interface ManifestSource {
    /**
     * @return the raw response body, or `null` when no manifest is available for any reason
     *   (404, timeout, oversized payload, non-HTTPS origin). The distinction between "absent" and
     *   "broken" does not change browser behaviour: both fall back to regular mode.
     */
    public suspend fun fetch(origin: String): ByteArray?
}
