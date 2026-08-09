package dev.siteskin.core

import dev.siteskin.core.manifest.ManifestDiagnosticCode
import dev.siteskin.core.manifest.ManifestDocumentResult
import dev.siteskin.core.manifest.ManifestParser
import dev.siteskin.core.model.SiteSkinConfiguration
import java.io.InputStream
import kotlinx.serialization.json.JsonObject

/** Total outcome of validating untrusted manifest bytes against one browser-observed origin. */
public sealed interface SiteSkinValidationOutcome {
    /** A trusted configuration that may activate, together with non-rejecting diagnostics. */
    public data class Accepted(
        public val configuration: SiteSkinConfiguration,
        public val diagnostics: List<ManifestDiagnostic>,
    ) : SiteSkinValidationOutcome

    /** A manifest that cannot activate, together with its rejecting diagnostics. */
    public data class Rejected(
        public val diagnostics: List<ManifestDiagnostic>,
    ) : SiteSkinValidationOutcome
}

/** Runs the browser-owned SiteSkin validation stages in their normative order. */
public object SiteSkinValidator {
    /**
     * Consumes [input] without closing it and binds any accepted configuration to [servingOrigin].
     */
    public fun validate(input: InputStream, servingOrigin: String): SiteSkinValidationOutcome {
        val parsed = ManifestParser.parseDocument(input)
        if (parsed is ManifestDocumentResult.Rejected) {
            return rejected(parsed.error.code.toCoreCode())
        }
        parsed as ManifestDocumentResult.Parsed

        val schema = SchemaValidator.validate(parsed.element)
        if (!schema.isValid) return SiteSkinValidationOutcome.Rejected(schema.errors)

        val root = parsed.element as JsonObject
        val security = SecurityValidator.validate(root, servingOrigin)
        val parserWarnings = parsed.warnings.map { warning ->
            ManifestDiagnostic(warning.code.toCoreCode(), warning.path?.toJsonPointer())
        }
        val diagnostics = parserWarnings + schema.warnings + security.diagnostics
        val configuration = security.configuration
            ?: return SiteSkinValidationOutcome.Rejected(diagnostics)
        return SiteSkinValidationOutcome.Accepted(configuration, diagnostics)
    }

    private fun rejected(code: DiagnosticCode): SiteSkinValidationOutcome.Rejected =
        SiteSkinValidationOutcome.Rejected(listOf(ManifestDiagnostic(code)))
}

private fun ManifestDiagnosticCode.toCoreCode(): DiagnosticCode = when (this) {
    ManifestDiagnosticCode.SIZE_EXCEEDED -> DiagnosticCode.SIZE_EXCEEDED
    ManifestDiagnosticCode.PARSE -> DiagnosticCode.PARSE
    ManifestDiagnosticCode.FIELD_UNKNOWN -> DiagnosticCode.FIELD_UNKNOWN
}

private fun String.toJsonPointer(): String = removePrefix("$")
    .replace(Regex("\\[(\\d+)]"), "/$1")
    .replace('.', '/')
