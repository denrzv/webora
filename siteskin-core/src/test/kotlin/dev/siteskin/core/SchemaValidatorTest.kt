package dev.siteskin.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaValidatorTest {

    @Test
    fun `unsupported major short-circuits an alien document`() {
        val result = validate("""{"schemaVersion":"2.0","site":"alien"}""")

        assertEquals(listOf(DiagnosticCode.VERSION_UNSUPPORTED), result.errors.map { it.code })
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `missing malformed and non-string versions are schema invalid`() {
        listOf(
            """{"site":{"id":"site","name":"Site"}}""",
            """{"schemaVersion":"1","site":{"id":"site","name":"Site"}}""",
            """{"schemaVersion":1.0,"site":{"id":"site","name":"Site"}}""",
        ).forEach { document ->
            assertEquals(
                listOf(DiagnosticCode.SCHEMA_INVALID),
                validate(document).errors.map { it.code },
            )
        }
    }

    @Test
    fun `unknown fields action types and icons remain structurally valid`() {
        val result = validate(
            """
            {
              "schemaVersion": "1.999",
              "future": {"enabled": true},
              "site": {"id": "site", "name": "Site"},
              "bottomNavigation": [{
                "id": "future", "label": "Future", "icon": "future_icon",
                "action": {"type": "future_action", "futureValue": 3}
              }]
            }
            """.trimIndent(),
        )

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `each navigation collection validates nested requirements`() {
        listOf("bottomNavigation", "menu", "quickActions").forEach { collection ->
            val result = validate(
                """
                {
                  "schemaVersion": "1.0",
                  "site": {"id": "site", "name": "Site"},
                  "$collection": [{"id": "item", "label": "Item", "action": {}}]
                }
                """.trimIndent(),
            )

            assertEquals(listOf(DiagnosticCode.SCHEMA_INVALID), result.errors.map { it.code })
        }
    }

    @Test
    fun `known actions require their payload`() {
        listOf("internal_url", "external_url", "phone", "email", "map").forEach { type ->
            val result = validate(
                """
                {
                  "schemaVersion": "1.0",
                  "site": {"id": "site", "name": "Site"},
                  "menu": [{"id": "item", "label": "Item", "action": {"type": "$type"}}]
                }
                """.trimIndent(),
            )

            assertEquals(listOf(DiagnosticCode.SCHEMA_INVALID), result.errors.map { it.code })
        }
    }

    private fun validate(document: String): ManifestValidationResult =
        SchemaValidator.validate(Json.parseToJsonElement(document))
}
