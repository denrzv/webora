package app.webora.browser.inspector

import dev.siteskin.core.SiteSkinLimits

/**
 * The single point at which website-controlled text is made safe to display in the inspector.
 *
 * The inspector is a grid of browser-authored labels beside values, and several of those values are
 * arbitrary website text. `SS-W-FIELD-UNKNOWN` fires on keys the browser does not recognise, so its
 * JSON pointer contains whatever the manifest author wrote; response header values are never
 * validated at all. A manifest carrying a top-level key named `"x\nHTTP status: 200"` would
 * otherwise render a convincing extra row inside the browser's own diagnostic tool — `ADR-006`'s
 * impersonation problem relocated to a surface that did not exist when `ADR-006` was written.
 *
 * So every untrusted value is flattened to a single line and bounded. The bound is
 * [SiteSkinLimits.MAX_SUBTITLE_LENGTH], read from core rather than copied, because a second copy of
 * a limit is a second thing to keep in step.
 *
 * Format characters are removed along with whitespace and control characters. They are neither, but
 * `U+202E RIGHT-TO-LEFT OVERRIDE` reverses everything after it, which is a way to make a value read
 * like a label without containing a newline at all.
 */
internal fun inspectorValue(raw: String?): String {
    if (raw.isNullOrEmpty()) return ""
    val flattened = StringBuilder()
    // A separator is remembered rather than written, so a run collapses and a leading run is
    // dropped. The loop stops at the bound and may overshoot by the one character that crossed it,
    // which the final take removes — response header values arrive unbounded, so the walk itself
    // has to end rather than only its result being trimmed.
    var pendingSeparator = false
    for (character in raw) {
        if (flattened.length >= SiteSkinLimits.MAX_SUBTITLE_LENGTH) break
        if (character.isDisplaySeparator()) {
            pendingSeparator = flattened.isNotEmpty()
        } else {
            if (pendingSeparator) flattened.append(' ')
            pendingSeparator = false
            flattened.append(character)
        }
    }
    return flattened.toString().take(SiteSkinLimits.MAX_SUBTITLE_LENGTH)
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
