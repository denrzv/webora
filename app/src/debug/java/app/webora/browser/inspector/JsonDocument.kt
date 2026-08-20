package app.webora.browser.inspector

import java.util.Locale

/**
 * A JSON document, as a closed tree the browser builds and a writer renders.
 *
 * Content-blind on purpose. It knows nothing about inspectors, snapshots or websites, so its only
 * failure mode is an escaping or layout bug — a different kind of bug from "the document says more
 * than the panel does", and one that belongs in a different file with a different test. The policy
 * half lives in [inspectorJson].
 *
 * There is no JSON library on `:app`'s classpath and this deliberately does not add one:
 * `kotlinx.serialization` is an `implementation` dependency of `:siteskin-core`, so pulling it up
 * would put a parser on the browser's *release* classpath in service of a tool that cannot run
 * there. What is needed is a writer for six shapes, which is smaller than the wiring test a library
 * would deserve.
 */
internal sealed interface JsonValue

internal data object JsonNull : JsonValue

internal data class JsonBool(val value: Boolean) : JsonValue

/**
 * An integral number.
 *
 * `Long` only, and there is deliberately no `Double` case. Every number in the diagnostic model is
 * an `Int` or a `Long`; admitting floating point would admit `NaN` and the infinities, which have no
 * JSON representation and no failure mode better than a corrupt document a consumer finds later.
 */
internal data class JsonNumber(val value: Long) : JsonValue

internal data class JsonString(val value: String) : JsonValue

internal data class JsonArray(val items: List<JsonValue>) : JsonValue

/**
 * An object, as an **ordered list of pairs** rather than a `Map`.
 *
 * Determinism is then a property of the type instead of a habit of remembering `LinkedHashMap`, and
 * the issue's "deterministic field names and order, so repeated captures are easy to compare" needs
 * no configuration flag. A duplicate key is also representable rather than silently swallowed, which
 * is what lets a totality test see one.
 */
internal data class JsonObject(val fields: List<Pair<String, JsonValue>>) : JsonValue

/** The document as text: two-space indentation, no trailing newline. */
internal fun JsonValue.render(): String = StringBuilder().also { write(it, 0) }.toString()

private fun JsonValue.write(out: StringBuilder, depth: Int) {
    when (this) {
        JsonNull -> out.append("null")
        is JsonBool -> out.append(if (value) "true" else "false")
        is JsonNumber -> out.append(value.toString())
        is JsonString -> out.appendEscaped(value)
        is JsonArray -> out.writeItems(depth, '[', ']', items.size) { index -> items[index].write(out, depth + 1) }
        is JsonObject -> out.writeItems(depth, '{', '}', fields.size) { index ->
            out.appendEscaped(fields[index].first).append(": ")
            fields[index].second.write(out, depth + 1)
        }
    }
}

/**
 * One container layout for both containers.
 *
 * An empty container renders as `[]` or `{}` on one line rather than as a bracket, a blank line and
 * a bracket. An empty diagnostics array is the common case here, and it should not cost three lines
 * of a document someone is reading in a phone-sized paste.
 */
private inline fun StringBuilder.writeItems(
    depth: Int,
    open: Char,
    close: Char,
    size: Int,
    writeItem: (Int) -> Unit,
) {
    if (size == 0) {
        append(open).append(close)
        return
    }
    append(open).append('\n')
    for (index in 0 until size) {
        append(INDENT.repeat(depth + 1))
        writeItem(index)
        if (index < size - 1) append(',')
        append('\n')
    }
    append(INDENT.repeat(depth)).append(close)
}

/**
 * A quoted, escaped JSON string.
 *
 * Website text reaching a document has already been flattened by `untrustedText`, which removes
 * every ISO control character before it arrives. This is the second line rather than the first, and
 * it exists because a second line that is missing is indistinguishable from one that is present
 * until the day the first line changes. Non-ASCII is emitted literally: the clipboard is UTF-8, and
 * escaping it would only make the paste unreadable.
 */
private fun StringBuilder.appendEscaped(raw: String): StringBuilder {
    append('"')
    for (character in raw) {
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            FORM_FEED -> append("\\f")
            else -> if (character < ' ') append(escaped(character)) else append(character)
        }
    }
    return append('"')
}

private fun escaped(character: Char): String = String.format(Locale.ROOT, "\\u%04X", character.code)

private const val FORM_FEED = '\u000C'
private const val INDENT = "  "
