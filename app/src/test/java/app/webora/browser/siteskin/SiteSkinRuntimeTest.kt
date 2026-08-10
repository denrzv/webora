package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertSame
import org.junit.Test

class SiteSkinRuntimeTest {
    @Test fun `current allowed candidate activates`() {
        val outcome = available("https://shop.example", 4)

        val result = candidateDisposition(outcome, origin("https://shop.example"), 4, SiteConsentDecision.ALLOW)

        assertSame(CandidateDisposition.Activate::class, result::class)
    }

    @Test fun `current undecided candidate asks and never decision ignores`() {
        val outcome = available("https://shop.example", 4)

        assertSame(
            CandidateDisposition.Ask::class,
            candidateDisposition(outcome, origin("https://shop.example"), 4, null)::class,
        )
        assertSame(
            CandidateDisposition.Ignore,
            candidateDisposition(outcome, origin("https://shop.example"), 4, SiteConsentDecision.NEVER),
        )
    }

    @Test fun `different origin cannot activate even with allow`() {
        val outcome = available("https://shop.example", 4)

        val result = candidateDisposition(outcome, origin("https://admin.shop.example"), 4, SiteConsentDecision.ALLOW)

        assertSame(CandidateDisposition.Ignore, result)
    }

    @Test fun `stale generation cannot activate even on same origin`() {
        val outcome = available("https://shop.example", 3)

        val result = candidateDisposition(outcome, origin("https://shop.example"), 4, SiteConsentDecision.ALLOW)

        assertSame(CandidateDisposition.Ignore, result)
    }

    private fun available(value: String, generation: Long): ManifestDiscoveryOutcome.Available {
        val siteOrigin = origin(value)
        val validation = SiteSkinValidator.validate(
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"}}""".byteInputStream(),
            value,
        ) as SiteSkinValidationOutcome.Accepted
        return ManifestDiscoveryOutcome.Available(siteOrigin, generation, validation)
    }

    private fun origin(value: String) = checkNotNull(SiteOrigin.parse(value))
}
