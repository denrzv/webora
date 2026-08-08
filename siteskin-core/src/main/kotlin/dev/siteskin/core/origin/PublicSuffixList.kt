package dev.siteskin.core.origin

import java.net.IDN
import java.util.Locale

/**
 * The registrable domain (eTLD+1) of a host, from a bundled Public Suffix List snapshot.
 *
 * This value exists for one reason: `ADR-006` requires the registrable domain to be visible in
 * SiteSkin chrome, in browser-owned typography, with no manifest field able to suppress it. It is
 * on **no** comparison path — origin binding compares full canonical hosts — and that is what makes
 * shipping a dated data file acceptable here. A stale snapshot can render the wrong number of
 * labels; it can never make two origins compare equal.
 *
 * Provenance, the snapshot's SHA-256 and the manual refresh procedure are recorded in
 * `docs/adr/ADR-004-origin-binding.md`, and pinned by `PublicSuffixListTest`.
 */
internal object PublicSuffixList {

    const val RESOURCE: String = "public_suffix_list.dat"

    /** Upstream `// VERSION:` line of the bundled snapshot. */
    const val SNAPSHOT_VERSION: String = "2026-07-25_14-20-03_UTC"

    const val SNAPSHOT_SHA256: String =
        "084a5674d77c1d14900b16da5fc8afee9765af2f00a638552a8c7aa18f44ae81"

    /**
     * @param canonicalHost a host in the form [HostName] produces — punycode, lowercase.
     * @return the registrable domain, or [canonicalHost] unchanged when no rule applies.
     */
    fun registrableDomain(canonicalHost: String): String {
        if (canonicalHost.isEmpty() || canonicalHost.startsWith("[") || isIpv4(canonicalHost)) {
            return canonicalHost
        }

        val labels = canonicalHost.split('.')
        val suffixLabels = publicSuffixLength(labels)

        // 0 means no rule matched. Returning the whole host is a deliberate deviation from the
        // published algorithm's `*` default, which would return the last two labels. See the
        // class KDoc's ADR reference: for an unknown suffix the default renders evil.co.newtld and
        // bank.co.newtld identically as co.newtld, and this value's job is anti-impersonation.
        // Failing toward showing MORE of the host is the safe direction.
        return if (suffixLabels == 0 || suffixLabels >= labels.size) {
            canonicalHost
        } else {
            labels.takeLast(suffixLabels + 1).joinToString(".")
        }
    }

    /**
     * Number of labels forming the public suffix, or 0 when no rule matches.
     *
     * Exception rules win outright, which is why they are checked in their own pass rather than
     * folded into the longest-match search: `!city.kawasaki.jp` must beat `*.kawasaki.jp` even
     * though the wildcard match is not shorter.
     */
    private fun publicSuffixLength(labels: List<String>): Int {
        val rules = rules
        val candidates = labels.indices.map { i -> labels.subList(i, labels.size).joinToString(".") }

        candidates.forEachIndexed { i, candidate ->
            // An exception rule names a domain that is NOT a public suffix; the suffix is the rule
            // minus its own leftmost label.
            if (candidate in rules.exceptions) return labels.size - i - 1
        }

        var longest = 0
        candidates.forEachIndexed { i, candidate ->
            if (candidate in rules.literals) {
                longest = maxOf(longest, labels.size - i)
            }
            // A wildcard rule `*.parent` matches when `parent` sits at position i and some label
            // precedes it to be consumed by the `*`.
            if (i > 0 && candidate in rules.wildcards) {
                longest = maxOf(longest, labels.size - i + 1)
            }
        }
        return longest
    }

    private class Rules(
        val literals: Set<String>,
        val wildcards: Set<String>,
        val exceptions: Set<String>,
    )

    /**
     * Parsed lazily: a browser that never renders skinned chrome never pays for ~10,000 rules, and
     * `SiteOrigin.parse` runs on every navigation.
     */
    private val rules: Rules by lazy { load() }

    private fun load(): Rules {
        val literals = mutableSetOf<String>()
        val wildcards = mutableSetOf<String>()
        val exceptions = mutableSetOf<String>()

        readSnapshot().lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .forEach { line ->
                // 459 rules are written in Unicode while every host reaching this object is
                // punycode. Converting on load rather than at match time keeps the hot path a
                // plain set lookup — and skipping it entirely would make those rules silently
                // never match, failing open in a way that looks like it works.
                val rule = toAsciiOrNull(line.removePrefix("!").removePrefix("*.")) ?: return@forEach

                when {
                    line.startsWith("!") -> exceptions += rule
                    line.startsWith("*.") -> wildcards += rule
                    else -> literals += rule
                }
            }

        return Rules(literals = literals, wildcards = wildcards, exceptions = exceptions)
    }

    private fun readSnapshot(): String =
        requireNotNull(PublicSuffixList::class.java.getResourceAsStream(RESOURCE)) {
            "bundled Public Suffix List is missing from the classpath at $RESOURCE"
        }.use { it.readBytes().decodeToString() }

    private fun toAsciiOrNull(rule: String): String? =
        try {
            IDN.toASCII(rule).lowercase(Locale.ROOT)
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun isIpv4(host: String): Boolean {
        val labels = host.split('.')
        return labels.size == IPV4_LABELS && labels.all { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    private const val IPV4_LABELS = 4
}
