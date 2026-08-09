package dev.siteskin.core.origin

import java.net.IDN

/**
 * Detects hosts whose labels mix writing systems — the shape of an IDN homograph attack.
 *
 * `https://аpple.com` with a Cyrillic а is a **different origin** from `https://apple.com`, and
 * origin binding already treats it as one. Nothing here protects a comparison. What it supplies is
 * the signal `SKIN-002` needs to tell the user that what they are reading is not what they think,
 * which is the only defence against an attack whose whole mechanism is that the two strings render
 * identically.
 *
 * **Known gap, deliberate.** This detects *mixed*-script labels, not *whole*-script confusables: an
 * all-Cyrillic `раураӏ.com` uses one script and is not flagged. Catching those needs a confusable
 * mapping rather than a script partition, and `HARDEN-001` owns the adversarial corpus that would
 * justify carrying one. The PRD asks for mixed-script; this is exactly that and no more.
 */
internal object IdnGuard {

    /**
     * Script sets that legitimately co-occur inside one label, from UTS #39's Highly Restrictive
     * profile.
     *
     * Japanese mixes Han, Hiragana and Katakana within a single label as a matter of course, so a
     * plain "more than one script is suspicious" rule would flag a large share of the legitimate
     * Japanese web. Latin is included in each set because a Latin brand fragment beside CJK is
     * ordinary rather than adversarial.
     */
    private val ALLOWED_COMBINATIONS: List<Set<Character.UnicodeScript>> = listOf(
        setOf(
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
        ),
        setOf(
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.BOPOMOFO,
        ),
        setOf(
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HANGUL,
        ),
    )

    /**
     * @param canonicalHost a host in the canonical form [HostName] produces — punycode, lowercase.
     *
     * Taking the *canonical* form rather than whatever the caller was handed is what makes the
     * answer independent of spelling: `аpple.com` and `xn--pple-43d.com` are the same origin, so
     * they must produce the same flag. Decoding back to Unicode here is the only way to get that,
     * since the scripts are not visible in the punycode.
     */
    fun hasMixedScript(canonicalHost: String): Boolean {
        val unicode = toUnicodeOrNull(canonicalHost) ?: return false

        return unicode.split('.').any(::labelMixesScripts)
    }

    /**
     * Scripts are partitioned **per label**. A Japanese second-level name under a Latin TLD —
     * `日本語.example` — is the normal case, and comparing scripts across the whole host would flag
     * every internationalized domain under a Latin suffix.
     */
    private fun labelMixesScripts(label: String): Boolean {
        val scripts = scriptsIn(label)

        return scripts.size > 1 && ALLOWED_COMBINATIONS.none { allowed -> allowed.containsAll(scripts) }
    }

    /**
     * `COMMON` and `INHERITED` are dropped rather than counted. Digits, `-` and the Katakana
     * prolonged sound mark are all `COMMON`; counting them would make every hyphenated host mixed.
     */
    private fun scriptsIn(label: String): Set<Character.UnicodeScript> =
        label.codePoints()
            .toArray()
            .map(Character.UnicodeScript::of)
            .filterTo(mutableSetOf()) { script ->
                script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED
            }

    /**
     * `IDN.toUnicode` is documented not to throw, but it is handed attacker-controlled bytes and
     * `ADR-010` does not leave room for finding out otherwise in a navigation.
     */
    private fun toUnicodeOrNull(host: String): String? =
        try {
            IDN.toUnicode(host)
        } catch (e: IllegalArgumentException) {
            null
        }
}
