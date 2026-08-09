package dev.siteskin.core.origin

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Why a URL was refused.
 *
 * Not a diagnostic vocabulary: `CORE-004` collapses most of these to `SS-E-ORIGIN-MISMATCH` and the
 * scheme cases to `SS-E-SCHEME-DENIED`, because the disposition belongs to the layer that owns the
 * code. Keeping them apart here is what lets each test name the rule it is testing, and what
 * `DEVX-001`'s inspector will show a site owner who needs to know *which* rule bit.
 */
public enum class UrlRejection {
    /** Not parseable as a URI reference at all. */
    MALFORMED,

    /** Absolute but carries no authority — `https:evil`, `https:/evil.example/x`. */
    OPAQUE,

    /** A scheme outside the `http`/`https` allow-list. */
    SCHEME_NOT_ALLOWED,

    /** The authority carries userinfo, hiding the real host behind an `@`. */
    USERINFO_PRESENT,

    /** A `//host/path` reference, which inherits the scheme and lands off-origin. */
    PROTOCOL_RELATIVE,

    /** Path traversal that escapes the origin root. */
    TRAVERSAL_ESCAPE,

    /** Parses and resolves, but to a different origin. */
    CROSS_ORIGIN,
}

/** The outcome of resolving an untrusted URL against a serving origin. */
public sealed interface UrlResolution {
    /** @property url an absolute URL whose origin is, canonically, the serving origin. */
    public data class Resolved(val url: String) : UrlResolution

    public data class Rejected(val reason: UrlRejection) : UrlResolution
}

/**
 * Resolves manifest-supplied URLs against the origin that served the manifest.
 *
 * `SPEC.md` §3 names five forms that MUST be rejected rather than silently normalized away, and
 * each has a fixture in `spec/fixtures/invalid/`. The ordering below is the load-bearing part:
 *
 * - **Protocol-relative is caught before resolution.** Measured: `resolve("//evil.example/x")`
 *   against an `https` base returns `https://evil.example/x`. After resolution it is an ordinary
 *   absolute URL and there is nothing left to notice.
 * - **Traversal is checked independently of the origin comparison.** `/a/../../b` normalizes to
 *   `/../b`, whose origin is still the serving origin — so an origin check alone accepts it.
 */
public object UrlResolver {

    private const val HTTPS = "https"
    private const val HTTP = "http"

    /** Every spelling of a `..` path segment: each `.` may be written literally or percent-encoded. */
    private val TRAVERSAL_SEGMENTS = setOf("..", "%2E.", ".%2E", "%2E%2E")

    /**
     * @return [UrlResolution.Resolved] with an absolute URL inside [origin], or
     *   [UrlResolution.Rejected]. Never throws — the manifest is untrusted remote input and
     *   `ADR-010` requires every failure to degrade to regular browsing.
     */
    public fun resolveInternal(origin: SiteOrigin, raw: String): UrlResolution {
        // Before parsing: a protocol-relative reference is syntactically a path to any check that
        // only looks for a leading slash, and resolution erases the distinction.
        if (raw.startsWith("//")) return rejected(UrlRejection.PROTOCOL_RELATIVE)

        val reference = parseUriOrNull(raw) ?: return rejected(UrlRejection.MALFORMED)

        val schemeRejection = if (reference.isAbsolute) absoluteRejection(origin, reference) else null
        if (schemeRejection != null) return rejected(schemeRejection)

        return resolveAgainstRoot(origin, reference)
    }

    /**
     * Checks an absolute reference against the origin, returning the reason it fails or `null` if
     * it belongs to [origin].
     */
    private fun absoluteRejection(origin: SiteOrigin, reference: URI): UrlRejection? {
        val scheme = reference.scheme?.lowercase(Locale.ROOT)
        val authority = reference.rawAuthority

        return when {
            scheme != HTTPS && scheme != HTTP -> UrlRejection.SCHEME_NOT_ALLOWED
            authority == null -> UrlRejection.OPAQUE
            // Userinfo lets the serving origin's host appear verbatim in a URL pointing elsewhere.
            // Reported distinctly from CROSS_ORIGIN so the inspector can say which trick was used.
            authority.contains('@') -> UrlRejection.USERINFO_PRESENT
            else -> originRejection(origin, reference)
        }
    }

    /** `null` when [reference] belongs to [origin]; the reason it does not otherwise. */
    private fun originRejection(origin: SiteOrigin, reference: URI): UrlRejection? {
        val candidate = SiteOrigin.parse(reference.toString()) ?: return UrlRejection.MALFORMED
        return if (candidate == origin) null else UrlRejection.CROSS_ORIGIN
    }

    /**
     * Resolves against the origin root and rebuilds the URL from [SiteOrigin.canonical].
     *
     * Rebuilding rather than returning the resolved URI's own string is deliberate: it guarantees
     * the origin portion of every returned URL is canonical, so a caller cannot be handed
     * `https://BLOOMFLOWERS.example/x` and compare it against something else by string.
     */
    private fun resolveAgainstRoot(origin: SiteOrigin, reference: URI): UrlResolution {
        val resolved = resolveOrNull(URI(origin.rootUrl), reference)
            ?: return rejected(UrlRejection.MALFORMED)

        val normalized = resolved.normalize()
        val path = normalized.rawPath.orEmpty().ifEmpty { "/" }

        if (escapesRoot(path)) return rejected(UrlRejection.TRAVERSAL_ESCAPE)

        val query = normalized.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = normalized.rawFragment?.let { "#$it" }.orEmpty()

        return UrlResolution.Resolved(origin.canonical + path + query + fragment)
    }

    /**
     * True when a normalized path still contains a `..` segment.
     *
     * `URI.normalize()` collapses traversal that stays inside the path but — measured —
     * deliberately leaves a leading `..` in place, because there is nothing above the root to
     * collapse it into. That residue is precisely "this reference tried to leave the origin", which
     * is why detection is a check for leftovers rather than a reimplementation of path arithmetic.
     *
     * Percent-encoded spellings are compared too. Browsers decode `%2e%2e` before resolving, so a
     * check against the literal `..` alone passes a reference that escapes in the WebView.
     */
    private fun escapesRoot(path: String): Boolean =
        path.split('/').any { segment -> segment.uppercase(Locale.ROOT) in TRAVERSAL_SEGMENTS }

    private fun rejected(reason: UrlRejection): UrlResolution = UrlResolution.Rejected(reason)

    private fun parseUriOrNull(raw: String): URI? =
        try {
            URI(raw)
        } catch (e: URISyntaxException) {
            null
        }

    private fun resolveOrNull(base: URI, reference: URI): URI? =
        try {
            base.resolve(reference)
        } catch (e: IllegalArgumentException) {
            null
        }
}
