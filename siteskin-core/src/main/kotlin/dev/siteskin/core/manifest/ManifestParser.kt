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
        val bytes = try {
            readBounded(input)
        } catch (_: IOException) {
            return rejected(ManifestDiagnosticCode.PARSE)
        } ?: return rejected(ManifestDiagnosticCode.SIZE_EXCEEDED)
        val text = decodeUtf8(bytes) ?: return rejected(ManifestDiagnosticCode.PARSE)
        return decodeManifest(text)
    }

    private fun decodeManifest(text: String): ManifestParseResult =
        try {
            val element = json.parseToJsonElement(text)
            val warnings = UnknownFieldScanner.scan(element)
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

    private fun rejected(code: ManifestDiagnosticCode): ManifestParseResult.Rejected =
        ManifestParseResult.Rejected(ManifestDiagnostic(code))
}


private object UnknownFieldScanner {
    private val rootFields = setOf(
        "schemaVersion", "site", "branding", "toolbar", "bottomNavigation", "menu", "quickActions",
    )
    private val siteFields = setOf("id", "name", "shortName", "homeUrl")
    private val brandingFields = setOf(
        "primaryColor", "secondaryColor", "backgroundColor", "textColor", "logoUrl",
    )
    private val toolbarFields = setOf("title", "subtitle")
    private val itemFields = setOf("id", "label", "icon", "action", "match")
    private val actionFields = setOf("type", "url", "value")

    fun scan(element: JsonElement): List<ManifestDiagnostic> {
        if (element !is JsonObject) return emptyList()
        return buildList {
            inspectObject(element, "$", rootFields, this)
            inspectObject(element["site"], "$.site", siteFields, this)
            inspectObject(element["branding"], "$.branding", brandingFields, this)
            inspectObject(element["toolbar"], "$.toolbar", toolbarFields, this)
            inspectItems(element["bottomNavigation"], "$.bottomNavigation", this)
            inspectItems(element["menu"], "$.menu", this)
            inspectItems(element["quickActions"], "$.quickActions", this)
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
            inspectObject((item as? JsonObject)?.get("action"), "$itemPath.action", actionFields, warnings)
        }
    }

    private fun inspectObject(
        element: JsonElement?,
        path: String,
        knownFields: Set<String>,
        warnings: MutableList<ManifestDiagnostic>,
    ) {
        (element as? JsonObject)?.keys?.filterNot(knownFields::contains)?.forEach { field ->
            warnings += ManifestDiagnostic(ManifestDiagnosticCode.FIELD_UNKNOWN, "$path.$field")
        }
    }
}
