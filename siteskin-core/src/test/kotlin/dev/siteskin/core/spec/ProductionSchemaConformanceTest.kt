package dev.siteskin.core.spec

import dev.siteskin.core.DiagnosticCode
import dev.siteskin.core.SchemaValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionSchemaConformanceTest {

    @Test
    fun diagnosticCodesMatchThePublishedRegistry() {
        val publicCodes = DiagnosticCode.entries.map { it.value }

        assertEquals("Diagnostic wire values must be unique", publicCodes.size, publicCodes.toSet().size)
        assertEquals(SpecCorpus.registry.keys.sorted(), publicCodes.sorted())
    }

    @Test
    fun productionValidatorExecutesEveryVersionDecision() {
        SpecCorpus.versionDecisions.forEach { decision ->
            val document = buildJsonObject {
                decision.declared?.let { put("schemaVersion", it) }
                put("site", buildJsonObject {
                    put("id", "probe")
                    put("name", "Probe")
                })
            }

            assertEquals(
                decision.label,
                listOfNotNull(decision.code),
                SchemaValidator.validate(document).errors.map { it.code.value },
            )
        }
    }

    @Test
    fun productionValidatorMatchesReachableCorpusDiagnosticsExactly() {
        SpecCorpus.fixtures.filter { it.bodyParses }.forEach { fixture ->
            val expected = fixture.diagnostics
                .map { it.code }
                .filter { it == DiagnosticCode.VERSION_UNSUPPORTED.value || it == DiagnosticCode.SCHEMA_INVALID.value }
                .distinct()
            val document = Json.parseToJsonElement(fixture.bodyFile.readText())

            assertEquals(
                fixture.name,
                expected,
                SchemaValidator.validate(document).errors.map { it.code.value },
            )
        }
    }
}
