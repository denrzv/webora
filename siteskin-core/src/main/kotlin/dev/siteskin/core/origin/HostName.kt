package dev.siteskin.core.origin

import java.net.IDN
import java.util.Locale

/**
 * A host in the one spelling every comparison in this codebase uses.
 *
 * [ascii] is lowercase, punycode, and carries no trailing root dot. [isIpLiteral] is carried
 * because two later questions — the registrable domain and the mixed-script flag — are meaningless
 * for an address literal and must not be guessed at from the string's shape.
 */
internal data class CanonicalHost(
    val ascii: String,
    val isIpLiteral: Boolean,
)

/**
 * Canonicalizes a host so that origin comparison can be string equality.
 *
 * Three JDK behaviours shape this file, each measured on the build toolchain rather than assumed,
 * and each one a silent bug if it is assumed the other way:
 *
 * - `IDN.toASCII` does **not** lowercase ASCII input. `ShOp.Example` comes back unchanged, so
 *   punycode conversion is not case canonicalization and the lowercase step is mandatory.
 * - The lowercase step runs **after** conversion. By then the string is pure ASCII, so
 *   [Locale.ROOT] is not merely a good habit — there is no longer any character whose casing is
 *   locale-dependent, which removes the Turkish dotted-İ divergence rather than hoping to dodge it.
 * - `IDN.toASCII` applies no STD3 ASCII rules by default: `-bad.example` is returned intact. The
 *   label grammar below is therefore ours to enforce, not a duplicate of a check the JDK already did.
 */
internal object HostName {

    private const val MAX_LABEL_LENGTH = 63
    private const val MAX_HOST_LENGTH = 253
    private const val IPV4_LABELS = 4
    private const val MAX_IPV4_OCTET = 255
    private const val MAX_IPV4_OCTET_DIGITS = 3

    private val LABEL = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
    private val IPV6_CHARS = Regex("^[0-9a-f:.]+$")

    /** @return the canonical form, or `null` if [raw] is not a host this browser will talk to. */
    fun canonicalize(raw: String): CanonicalHost? {
        val trimmed = stripRootDot(raw)

        return when {
            trimmed.isEmpty() -> null
            trimmed.startsWith("[") -> canonicalizeIpv6(trimmed)
            else -> canonicalizeRegisteredName(trimmed)
        }
    }

    /**
     * Removes exactly one trailing dot — the root label. `shop.example.` and `shop.example` are the
     * same DNS name under the same certificate, so folding them lets the two sides of a comparison
     * agree. Exactly one, because `shop.example..` should stay broken: the second dot leaves an
     * empty label, and an empty label is a malformed host, not a verbose spelling of a valid one.
     */
    private fun stripRootDot(raw: String): String =
        if (raw.endsWith(".")) raw.dropLast(1) else raw

    private fun canonicalizeIpv6(raw: String): CanonicalHost? {
        if (!raw.endsWith("]")) return null

        val inner = raw.substring(1, raw.length - 1).lowercase(Locale.ROOT)
        if (inner.isEmpty() || !IPV6_CHARS.matches(inner) || !inner.contains(':')) return null

        return CanonicalHost(ascii = "[$inner]", isIpLiteral = true)
    }

    private fun canonicalizeRegisteredName(raw: String): CanonicalHost? {
        val ascii = toAsciiOrNull(raw)?.lowercase(Locale.ROOT) ?: return null

        if (ascii.length > MAX_HOST_LENGTH) return null

        val labels = ascii.split('.')
        if (labels.any { it.isEmpty() || it.length > MAX_LABEL_LENGTH || !LABEL.matches(it) }) {
            return null
        }

        return CanonicalHost(ascii = ascii, isIpLiteral = isIpv4(labels))
    }

    /**
     * `IDN.toASCII` signals a malformed name by throwing — an empty label and an undecodable
     * `xn--` sequence both do it. `ADR-010` requires every failure path here to end in regular
     * browsing, so the throw is converted to `null` at the boundary rather than allowed to
     * propagate into a navigation.
     */
    private fun toAsciiOrNull(raw: String): String? =
        try {
            IDN.toASCII(raw)
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun isIpv4(labels: List<String>): Boolean =
        labels.size == IPV4_LABELS &&
            labels.all { label ->
                label.isNotEmpty() &&
                    label.length <= MAX_IPV4_OCTET_DIGITS &&
                    label.all(Char::isDigit) &&
                    label.toInt() <= MAX_IPV4_OCTET
            }
}
