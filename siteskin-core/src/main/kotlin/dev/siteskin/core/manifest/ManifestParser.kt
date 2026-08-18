package dev.siteskin.core.manifest

import dev.siteskin.core.SiteSkinLimits
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Stable diagnostics emitted while bounding and parsing untrusted manifest bytes. */
public enum class ManifestDiagnosticCode(public val wireValue: String) {
    SIZE_EXCEEDED("SS-E-SIZE-EXCEEDED"),
    PARSE("SS-E-PARSE"),
    FIELD_UNKNOWN("SS-W-FIELD-UNKNOWN"),
}

/** A parser diagnostic with an optional path into the untrusted document. */
public data class ManifestDiagnostic(
    public val code: ManifestDiagnosticCode,
    public val path: String? = null,
) {
    /** Protocol spelling used by the conformance corpus and user-facing tools. */
    public val wireCode: String get() = code.wireValue
}

/** Total result of bounding and parsing a manifest; neither branch grants trust. */
public sealed interface ManifestParseResult {
    /** Well-formed, schema-shaped but still untrusted manifest data. */
    public data class Parsed(
        public val manifest: SiteSkinManifestDto,
        public val warnings: List<ManifestDiagnostic> = emptyList(),
    ) : ManifestParseResult

    /** Rejection at the transport-size or parse layer. */
    public data class Rejected(public val error: ManifestDiagnostic) : ManifestParseResult
}

/** Bounded parser for untrusted SiteSkin manifest streams. */
public object ManifestParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reads at most 131,073 bytes and does not close [input], which remains caller-owned.
     */
    public fun parse(input: InputStream): ManifestParseResult {
        return when (val result = parseDocument(input)) {
            is ManifestDocumentResult.Parsed -> decodeManifest(result.element, result.warnings)
            is ManifestDocumentResult.Rejected -> ManifestParseResult.Rejected(result.error)
        }
    }

    internal fun parseDocument(input: InputStream): ManifestDocumentResult {
        val bytes = try {
            readBounded(input)
        } catch (_: IOException) {
            return documentRejected(ManifestDiagnosticCode.PARSE)
        } ?: return documentRejected(ManifestDiagnosticCode.SIZE_EXCEEDED)
        val text = decodeUtf8(bytes)?.takeIf(::hasBoundedStructure)
            ?: return documentRejected(ManifestDiagnosticCode.PARSE)
        return try {
            val element = json.parseToJsonElement(text)
            ManifestDocumentResult.Parsed(element, UnknownFieldScanner.scan(element))
        } catch (_: SerializationException) {
            documentRejected(ManifestDiagnosticCode.PARSE)
        } catch (_: IllegalArgumentException) {
            documentRejected(ManifestDiagnosticCode.PARSE)
        }
    }

    private fun decodeManifest(
        element: JsonElement,
        warnings: List<ManifestDiagnostic>,
    ): ManifestParseResult =
        try {
            ManifestParseResult.Parsed(
                manifest = json.decodeFromJsonElement<SiteSkinManifestDto>(element),
                warnings = warnings,
            )
        } catch (_: SerializationException) {
            rejected(ManifestDiagnosticCode.PARSE)
        } catch (_: IllegalArgumentException) {
            rejected(ManifestDiagnosticCode.PARSE)
        }

    private fun decodeUtf8(bytes: ByteArray): String? =
        try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    private fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = SiteSkinLimits.MAX_MANIFEST_BYTES + 1
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) return output.toByteArray()
            if (count == 0) continue
            output.write(buffer, 0, count)
            remaining -= count
        }
        return null
    }

    private fun hasBoundedStructure(text: String): Boolean {
        return JsonStructureScanner().accepts(text)
    }

    private class JsonStructureScanner {
        val openings = CharArray(SiteSkinLimits.MAX_JSON_DEPTH)
        var depth = 0
        var inString = false
        var escaped = false

        fun accepts(text: String): Boolean = text.all(::accept) && depth == 0 && !inString

        private fun accept(character: Char): Boolean =
            if (inString) acceptStringCharacter(character) else acceptStructuralCharacter(character)

        private fun acceptStringCharacter(character: Char): Boolean {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = false
            }
            return true
        }

        private fun acceptStructuralCharacter(character: Char): Boolean = when (character) {
            '"' -> true.also { inString = true }
            '{', '[' -> push(character)
            '}' -> pop('{')
            ']' -> pop('[')
            else -> true
        }

        private fun push(character: Char): Boolean {
            if (depth == openings.size) return false
            openings[depth++] = character
            return true
        }

        private fun pop(expected: Char): Boolean = depth > 0 && openings[--depth] == expected
    }

    private fun rejected(code: ManifestDiagnosticCode): ManifestParseResult.Rejected =
        ManifestParseResult.Rejected(ManifestDiagnostic(code))

    private fun documentRejected(code: ManifestDiagnosticCode): ManifestDocumentResult.Rejected =
        ManifestDocumentResult.Rejected(ManifestDiagnostic(code))
}

internal sealed interface ManifestDocumentResult {
    data class Parsed(
        val element: JsonElement,
        val warnings: List<ManifestDiagnostic>,
    ) : ManifestDocumentResult

    data class Rejected(val error: ManifestDiagnostic) : ManifestDocumentResult
}


private object UnknownFieldScanner {
    private val rootFields = setOf(
        "schemaVersion", "site", "branding", "toolbar", "presentation", "bottomNavigation", "menu",
        "quickActions",
    )
    private val siteFields = setOf("id", "name", "shortName", "homeUrl")
    private val brandingFields = setOf(
        "primaryColor", "secondaryColor", "backgroundColor", "textColor", "logoUrl",
    )
    private val toolbarFields = setOf("title", "subtitle")

    // The third structural copy of the manifest shape, after the JSON schema and
    // ManifestStructure. Nothing cross-checks the three, so a field added to the other two and
    // forgotten here emits SS-W-FIELD-UNKNOWN on `/presentation` for every conforming manifest —
    // a protocol diagnostic accusing a correct document.
    private val presentationFields = setOf("hub")
    private val itemFields = setOf("id", "label", "icon", "action", "match")
    private val actionFields = setOf("type", "url", "value")

    fun scan(element: JsonElement): List<ManifestDiagnostic> {
        if (element !is JsonObject) return emptyList()
        return buildList {
            inspectObject(element, "$", rootFields, this)
        }
    }

    private fun inspectItems(
        element: JsonElement?,
        path: String,
        warnings: MutableList<ManifestDiagnostic>,
    ) {
        (element as? JsonArray)?.forEachIndexed { index, item ->
            val itemPath = "$path[$index]"
            inspectObject(item, itemPath, itemFields, warnings)
        }
    }

    private fun inspectObject(
        element: JsonElement?,
        path: String,
        knownFields: Set<String>,
        warnings: MutableList<ManifestDiagnostic>,
    ) {
        (element as? JsonObject)?.forEach { (field, value) ->
            if (field !in knownFields) {
                warnings += ManifestDiagnostic(ManifestDiagnosticCode.FIELD_UNKNOWN, "$path.$field")
            } else {
                inspectKnownValue(field, value, "$path.$field", warnings)
            }
        }
    }

    private fun inspectKnownValue(
        field: String,
        value: JsonElement,
        path: String,
        warnings: MutableList<ManifestDiagnostic>,
    ) {
        when (field) {
            "site" -> inspectObject(value, path, siteFields, warnings)
            "branding" -> inspectObject(value, path, brandingFields, warnings)
            "toolbar" -> inspectObject(value, path, toolbarFields, warnings)
            "presentation" -> inspectObject(value, path, presentationFields, warnings)
            "bottomNavigation", "menu", "quickActions" -> inspectItems(value, path, warnings)
            "action" -> inspectObject(value, path, actionFields, warnings)
        }
    }
}
