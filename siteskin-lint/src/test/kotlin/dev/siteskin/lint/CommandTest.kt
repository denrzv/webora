package dev.siteskin.lint

import dev.siteskin.core.DiagnosticCode
import dev.siteskin.core.ManifestDiagnostic
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandTest {
    @Test fun `requires exactly one origin-only HTTPS argument`() {
        listOf(
            emptyArray(), arrayOf("http://example.com"), arrayOf("https://example.com/path"),
            arrayOf("https://example.com?query"), arrayOf("https://user@example.com"),
            arrayOf("https://one.example", "https://two.example"),
        ).forEach { args ->
            val invocation = invoke(args) { error("loader must not run") }
            assertEquals(args.contentToString(), 2, invocation.status)
            assertTrue(invocation.error.startsWith("usage:"))
        }
    }

    @Test fun `accepted result prints diagnostics and exits zero`() {
        val accepted = SiteSkinValidator.validate(
            """{"schemaVersion":"1.0","future":true,"site":{"id":"x","name":"X"}}"""
                .byteInputStream(),
            "https://example.com",
        )

        val invocation = invoke(arrayOf("https://EXAMPLE.com/")) {
            ManifestLoadResult.Validated(accepted)
        }

        assertEquals(0, invocation.status)
        assertEquals("SS-W-FIELD-UNKNOWN /future\n", invocation.output)
        assertTrue(invocation.error.isEmpty())
    }

    @Test fun `rejection prints stable code and exits one`() {
        val rejected = SiteSkinValidationOutcome.Rejected(
            listOf(ManifestDiagnostic(DiagnosticCode.SCHEMA_INVALID)),
        )
        val invocation = invoke(arrayOf("https://example.com")) {
            ManifestLoadResult.Validated(rejected)
        }

        assertEquals(1, invocation.status)
        assertEquals("SS-E-SCHEMA-INVALID\n", invocation.output)
    }

    @Test fun `operational failure is concise and does not leak exception or body`() {
        val invocation = invoke(arrayOf("https://example.com")) {
            ManifestLoadResult.Failed("manifest request failed")
        }

        assertEquals(1, invocation.status)
        assertEquals("siteskin-lint: manifest request failed\n", invocation.error)
        assertFalse(invocation.error.contains("Exception"))
    }

    private fun invoke(args: Array<String>, load: () -> ManifestLoadResult): Invocation {
        val output = ByteArrayOutputStream()
        val error = ByteArrayOutputStream()
        val status = Command(ManifestLoader { load() }).run(args, PrintStream(output), PrintStream(error))
        return Invocation(status, output.toString(), error.toString())
    }

    private data class Invocation(val status: Int, val output: String, val error: String)
}
