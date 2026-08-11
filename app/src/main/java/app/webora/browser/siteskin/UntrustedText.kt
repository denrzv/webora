package app.webora.browser.siteskin

/**
 * The single point at which website-controlled text is made safe to display in browser-owned UI.
 *
 * Two surfaces need this and neither owns it. The `DEVX-001` inspector renders arbitrary manifest
 * keys — `SS-W-FIELD-UNKNOWN` reports the key it did not recognise, so its JSON pointer is whatever
 * the manifest author wrote — beside response header values, which are never validated at all. The
 * `UX-006` consent sheet renders the site's requested title and subtitle inside the dialog that
 * decides whether to trust that site, which is the higher-stakes of the two: it is the one place
 * manifest text appears *before* the user has agreed to anything.
 *
 * In both cases the failure is the same shape. A manifest string containing `"\nHTTP status: 200"`
 * renders a convincing extra row inside the browser's own UI — `ADR-006`'s impersonation problem
 * relocated to a surface `ADR-006` was not written for. So every untrusted value is flattened to a
 * single line and bounded.
 *
 * The bound is a parameter rather than a constant because the two callers bound different fields:
 * the inspector uses [dev.siteskin.core.SiteSkinLimits.MAX_SUBTITLE_LENGTH] for values with no
 * natural limit, and the consent sheet bounds a title by `MAX_TITLE_LENGTH`. Both read the number
 * from core rather than copying it, because a second copy of a limit is a second thing to keep in
 * step.
 *
 * Format characters are removed along with whitespace and control characters. They are neither, but
 * `U+202E RIGHT-TO-LEFT OVERRIDE` reverses everything after it, which is a way to make a value read
 * like a label — or reorder the browser-authored copy around it — without containing a newline at
 * all.
 */
internal fun untrustedText(raw: String?, limit: Int): String {
    if (raw.isNullOrEmpty()) return ""
    val flattened = StringBuilder()
    // A separator is remembered rather than written, so a run collapses and a leading run is
    // dropped. The loop stops at the bound and may overshoot by the one character that crossed it,
    // which the final take removes — response header values arrive unbounded, so the walk itself
    // has to end rather than only its result being trimmed.
    var pendingSeparator = false
    for (character in raw) {
        if (flattened.length >= limit) break
        if (character.isDisplaySeparator()) {
            pendingSeparator = flattened.isNotEmpty()
        } else {
            if (pendingSeparator) flattened.append(' ')
            pendingSeparator = false
            flattened.append(character)
        }
    }
    return flattened.toString().take(limit)
}

/**
 * Anything that can move the cursor, start a line, or reorder what follows it.
 *
 * Kotlin's [Char.isWhitespace] already covers `Character.isSpaceChar`, so `U+00A0` and the
 * `U+2028`/`U+2029` line separators are included — which `\s` in a `java.util.regex` pattern is not,
 * and that gap is why this is a character walk rather than a regex.
 */
private fun Char.isDisplaySeparator(): Boolean =
    isWhitespace() || isISOControl() || Character.getType(this) == Character.FORMAT.toInt()
