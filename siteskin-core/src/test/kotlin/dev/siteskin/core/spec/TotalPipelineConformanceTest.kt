package dev.siteskin.core.spec

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TotalPipelineConformanceTest {
    @Test fun `production pipeline matches every corpus activation and diagnostic`() {
        SpecCorpus.fixtures.forEach { fixture ->
            val origin = fixture.origin ?: "https://fixture.invalid"
            val result = fixture.bodyFile.inputStream().use { SiteSkinValidator.validate(it, origin) }
            val accepted = result is SiteSkinValidationOutcome.Accepted
            val diagnostics = when (result) {
                is SiteSkinValidationOutcome.Accepted -> result.diagnostics
                is SiteSkinValidationOutcome.Rejected -> result.diagnostics
            }

            assertEquals(fixture.name, fixture.hasResult, accepted)
            assertEquals(fixture.name, fixture.diagnostics.map { it.code }, diagnostics.map { it.code.value })
        }
        assertTrue(SpecCorpus.fixtures.isNotEmpty())
    }
}
