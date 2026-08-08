package dev.siteskin.core.origin

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * An origin: `scheme + host + port`, in the one canonical spelling this codebase compares.
 *
 * A `SiteOrigin` that exists is canonical. There is no public constructor and no `copy()`, so the
 * only way to obtain one is [parse], and every instance has therefore been through host
 * canonicalization and the scheme allow-list. This is the same construct-only-via-validator device
 * `conventions.md` requires of `SiteSkinConfiguration`, applied one layer lower: it turns "compare
 * canonical forms, never raw ones" from a rule reviewers have to remember into a rule the type
 * system already enforced.
 *
 * Equality is exactly `(scheme, host, port)` — `ADR-004`. `https://shop.example` and
 * `https://admin.shop.example` are unrelated origins, and so are `https://shop.example` and
 * `http://shop.example`.
 */
public class SiteOrigin private constructor(
    public val scheme: String,
    public val host: String,
    public val port: Int,
) {

    /**
     * The origin as a string, with the scheme's default port elided.
     *
     * This is the spelling the conformance corpus uses in its `"origin"` field and the spelling
     * `NET-002` will use as half of its cache key, so it is a contract rather than a formatting
     * choice.
     */
    public val canonical: String =
        if (port == defaultPortFor(scheme)) "$scheme://$host" else "$scheme://$host:$port"

    /**
     * The origin root, `https://shop.example/`.
     *
     * `SPEC.md` §12 makes `site.homeUrl` always present in a canonical result, falling back to this
     * when the manifest omits it or supplies one that fails origin binding.
     */
    public val rootUrl: String = "$canonical/"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is SiteOrigin && scheme == other.scheme && host == other.host && port == other.port)

    override fun hashCode(): Int = (scheme.hashCode() * HASH_FACTOR + host.hashCode()) * HASH_FACTOR + port

    override fun toString(): String = canonical

    public companion object {
        private const val HASH_FACTOR = 31
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65535

        private const val HTTPS = "https"
        private const val HTTP = "http"
        private const val HTTPS_PORT = 443
        private const val HTTP_PORT = 80

        /**
         * Parses [url] into the origin it belongs to, ignoring path, query and fragment.
         *
         * @return `null` for anything that is not an `http`/`https` origin — a denied scheme, a
         *   missing or malformed authority, an authority carrying userinfo, a host that fails
         *   canonicalization, or a port outside `1..65535`. Never throws: `ADR-010` requires every
         *   failure here to end in regular browsing, and both `java.net.URI` and `java.net.IDN`
         *   report malformed input by throwing.
         */
        public fun parse(url: String): SiteOrigin? {
            val uri = parseUriOrNull(url) ?: return null
            val scheme = uri.scheme
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it == HTTPS || it == HTTP }
                ?: return null

            // Deliberately rawAuthority, never getHost(). Measured: URI.getHost() returns null for
            // a perfectly valid IDN host — it applies RFC 2396 host rules and gives up — so a
            // resolver built on it passes every ASCII test and fails on the first non-ASCII domain.
            // rawAuthority hands back the authority as written, which is what needs checking anyway.
            val authority = uri.rawAuthority ?: return null

            return fromAuthority(scheme, authority)
        }

        private fun fromAuthority(scheme: String, authority: String): SiteOrigin? {
            // Userinfo lets the serving origin's host appear verbatim in a URL pointing elsewhere:
            // https://shop.example@evil.example/. Rejected here so no caller can ever be handed the
            // wrong origin to compare against.
            if (authority.isEmpty() || authority.contains('@')) return null

            val (rawHost, rawPort) = splitHostAndPort(authority) ?: return null
            val host = HostName.canonicalize(rawHost)
            val port = resolvePort(scheme, rawPort)

            return if (host == null || port == null) {
                null
            } else {
                SiteOrigin(scheme = scheme, host = host.ascii, port = port)
            }
        }

        /**
         * Splits an authority into host and optional port.
         *
         * IPv6 literals are bracketed and full of colons, so the port separator is the first colon
         * *after* the closing bracket rather than the last colon in the string.
         */
        private fun splitHostAndPort(authority: String): Pair<String, String?>? {
            val separator =
                if (authority.startsWith("[")) {
                    val close = authority.indexOf(']')
                    if (close < 0) return null
                    authority.indexOf(':', startIndex = close)
                } else {
                    authority.indexOf(':')
                }

            if (separator < 0) return Pair(authority, null)

            val host = authority.substring(0, separator)
            val port = authority.substring(separator + 1)
            // A second colon in a non-bracketed authority is malformed, not a second port.
            return if (port.contains(':')) null else Pair(host, port)
        }

        private fun resolvePort(scheme: String, rawPort: String?): Int? {
            if (rawPort == null) return defaultPortFor(scheme)

            val port = rawPort.toIntOrNull() ?: return null
            return if (port in MIN_PORT..MAX_PORT) port else null
        }

        private fun defaultPortFor(scheme: String): Int = if (scheme == HTTPS) HTTPS_PORT else HTTP_PORT

        private fun parseUriOrNull(url: String): URI? =
            try {
                URI(url)
            } catch (e: URISyntaxException) {
                null
            }
    }
}
