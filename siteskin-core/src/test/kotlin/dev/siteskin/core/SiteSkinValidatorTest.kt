package dev.siteskin.core

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSkinValidatorTest {
    @Test fun `well formed wrong shape is a schema rejection rather than parse rejection`() {
        val result = SiteSkinValidator.validate("[]".byteInputStream(), ORIGIN)

        assertRejected(result, DiagnosticCode.SCHEMA_INVALID)
    }

    @Test fun `unsupported version short circuits alien structure`() {
        val body = """{"schemaVersion":"2.0","site":"alien","surfaces":[]}"""

        val result = SiteSkinValidator.validate(body.byteInputStream(), ORIGIN)

        assertRejected(result, DiagnosticCode.VERSION_UNSUPPORTED)
    }

    @Test fun `accepted result merges unknown and security diagnostics in stage order`() {
        val body = """
            {"schemaVersion":"1.0","future":true,"site":{"id":"x","name":"X"},
            "branding":{"primaryColor":"#fff","textColor":"#fff"}}
        """.trimIndent()

        val result = SiteSkinValidator.validate(body.byteInputStream(), ORIGIN)
            as SiteSkinValidationOutcome.Accepted

        assertEquals(
            listOf(DiagnosticCode.FIELD_UNKNOWN, DiagnosticCode.CONTRAST_CORRECTED),
            result.diagnostics.map { it.code },
        )
        assertEquals("/future", result.diagnostics.first().pointer)
        assertEquals(ORIGIN, result.configuration.origin)
    }

    @Test fun `invalid serving origin cannot produce a trusted configuration`() {
        val result = SiteSkinValidator.validate(MINIMAL.byteInputStream(), "https://evil.example/path")

        assertTrue(result is SiteSkinValidationOutcome.Rejected)
    }

    @Test fun `validator reads once and leaves caller stream open`() {
        val input = CloseTrackingInputStream(MINIMAL.encodeToByteArray())

        val result = SiteSkinValidator.validate(input, ORIGIN)

        assertTrue(result is SiteSkinValidationOutcome.Accepted)
        assertFalse(input.closed)
    }

    private fun assertRejected(result: SiteSkinValidationOutcome, code: DiagnosticCode) {
        assertTrue(result is SiteSkinValidationOutcome.Rejected)
        assertEquals(listOf(code), (result as SiteSkinValidationOutcome.Rejected).diagnostics.map { it.code })
    }

    private class CloseTrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }
    }

    private companion object {
        const val ORIGIN = "https://example.com"
        const val MINIMAL = """{"schemaVersion":"1.0","site":{"id":"x","name":"X"}}"""
    }
}
