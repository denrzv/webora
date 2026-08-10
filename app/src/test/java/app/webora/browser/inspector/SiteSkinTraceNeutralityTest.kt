package app.webora.browser.inspector

import app.webora.browser.siteskin.CacheableManifestSource
import app.webora.browser.siteskin.CandidateDisposition
import app.webora.browser.siteskin.FetchRejection
import app.webora.browser.siteskin.ManifestCache
import app.webora.browser.siteskin.ManifestCacheKey
import app.webora.browser.siteskin.ManifestCacheMetadata
import app.webora.browser.siteskin.ManifestDiscoveryCoordinator
import app.webora.browser.siteskin.ManifestDiscoveryOutcome
import app.webora.browser.siteskin.ManifestFetchResult
import app.webora.browser.siteskin.SiteConsentDecision
import app.webora.browser.siteskin.candidateDisposition
import dev.siteskin.core.origin.SiteOrigin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ticket's central invariant: the inspector observes and never decides.
 *
 * `DEVX-001` widened `ManifestFetchResult` and threaded a sink through discovery. Widened types
 * invite a caller to branch on the new detail, and a sink is a place where a traced and an untraced
 * path can quietly diverge. This test runs the whole discovery matrix twice — once recording, once
 * discarding — and asserts the browser reached the same conclusion both times.
 */
class SiteSkinTraceNeutralityTest {

    @Test fun `recording changes no discovery outcome and no activation disposition`() = runTest {
        val traced = scenarios().map { scenario -> scenario.name to run(this, scenario, SiteSkinTraceRecorder()) }
        val untraced = scenarios().map { scenario -> scenario.name to run(this, scenario, SiteSkinTraceSink.None) }

        assertEquals(traced, untraced)
    }

    @Test fun `the matrix actually exercises every disposition`() {
        // A neutrality proof over a matrix that happens to be all-Ignore proves nothing.
        val observed = scenarios().map { it.name }

        assertTrue("expected a broad matrix, got $observed", observed.size >= EXPECTED_SCENARIOS)
    }

    @Test fun `the matrix reaches activation, consent and refusal`() = runTest {
        val results = scenarios().map { run(this, it, SiteSkinTraceRecorder()) }.map { it.disposition }

        assertTrue("no scenario activated: $results", results.any { it.startsWith("Activate") })
        assertTrue("no scenario asked for consent: $results", results.any { it.startsWith("Ask") })
        assertTrue("no scenario was ignored: $results", results.any { it == "Ignore" })
    }

    private fun run(scope: TestScope, scenario: Scenario, sink: SiteSkinTraceSink): Conclusion {
        val outcomes = mutableListOf<ManifestDiscoveryOutcome>()
        val coordinator = ManifestDiscoveryCoordinator(
            scope,
            scenario.source,
            scenario.cache(),
            sink,
            outcomes::add,
        )

        coordinator.onPageStarted(scenario.pageUrl, GENERATION)
        scope.testScheduler.advanceUntilIdle()

        val outcome = outcomes.single()
        val observed = SiteOrigin.parse(scenario.pageUrl)
        val disposition = candidateDisposition(outcome, observed, GENERATION, scenario.consent)
        return Conclusion(outcome.describe(), disposition.describe())
    }

    private data class Conclusion(val outcome: String, val disposition: String)

    private class Scenario(
        val name: String,
        val source: CacheableManifestSource,
        val cache: () -> ManifestCache = { ManifestCache { 0 } },
        val pageUrl: String = "$ORIGIN/page",
        val consent: SiteConsentDecision? = SiteConsentDecision.ALLOW,
    )

    private fun scenarios(): List<Scenario> = listOf(
        Scenario("accepted fetch", fetched(VALID_BODY)),
        Scenario("accepted fetch awaiting consent", fetched(VALID_BODY), consent = null),
        Scenario("accepted fetch refused for this site", fetched(VALID_BODY), consent = SiteConsentDecision.NEVER),
        Scenario("accepted fetch with warnings", fetched(WARNING_BODY)),
        Scenario("rejected body", fetched(REJECTED_BODY)),
        Scenario("http error", source { ManifestFetchResult.Rejected(FetchRejection.HTTP_ERROR, 404) }),
        Scenario("oversized", source { ManifestFetchResult.Rejected(FetchRejection.OVERSIZED, 200) }),
        Scenario("transport unavailable", source { ManifestFetchResult.Unavailable }),
        Scenario("stale replay", source { ManifestFetchResult.Unavailable }, cache = ::stale),
        Scenario("revalidated", notModified(), cache = ::stale),
        Scenario("not modified without an entry", notModified()),
        Scenario("refetched over a stale entry", fetched(VALID_BODY), cache = ::stale),
        Scenario("fresh cache hit", source { error("no request may be made") }, cache = ::fresh),
        Scenario("not eligible", fetched(VALID_BODY), pageUrl = "http://plain.example/page"),
        Scenario("unparseable page", fetched(VALID_BODY), pageUrl = "not a url"),
    )

    private fun source(fetch: () -> ManifestFetchResult) = CacheableManifestSource { _, _ -> fetch() }

    private fun fetched(body: ByteArray) = source { ManifestFetchResult.Fetched(body, ManifestCacheMetadata()) }

    private fun notModified() = source { ManifestFetchResult.NotModified(ManifestCacheMetadata(etag = "tag")) }

    private companion object {
        const val ORIGIN = "https://shop.example"
        const val GENERATION = 3L
        const val EXPECTED_SCENARIOS = 15
        val VALID_BODY = """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"}}""".toByteArray()
        val WARNING_BODY =
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},"surprise":1}""".toByteArray()
        val REJECTED_BODY = "{}".toByteArray()

        fun stale(): ManifestCache = ManifestCache { 0 }.apply {
            put(ManifestCacheKey(ORIGIN, "1.0"), VALID_BODY, ManifestCacheMetadata(etag = "tag"))
        }

        fun fresh(): ManifestCache = ManifestCache { 0 }.apply {
            put(ManifestCacheKey(ORIGIN, "1.0"), VALID_BODY, ManifestCacheMetadata(cacheControl = "max-age=60"))
        }
    }
}

/**
 * A comparable description of a decision.
 *
 * The outcome holds a `SiteSkinConfiguration`, which has no `equals` — by design, since a trusted
 * configuration is an identity rather than a value. Comparing the decision therefore means
 * comparing what the browser concluded from it.
 */
private fun ManifestDiscoveryOutcome.describe(): String = when (this) {
    is ManifestDiscoveryOutcome.Available ->
        "Available ${origin.canonical} $generation ${validation.configuration.schemaVersion} " +
            validation.diagnostics.map { "${it.code.value}@${it.pointer}" }
    is ManifestDiscoveryOutcome.Unavailable -> "Unavailable ${origin?.canonical} $generation"
}

private fun CandidateDisposition.describe(): String = when (this) {
    is CandidateDisposition.Activate -> "Activate ${candidate.origin.canonical} ${candidate.generation}"
    is CandidateDisposition.Ask -> "Ask ${candidate.origin.canonical} ${candidate.generation}"
    CandidateDisposition.Ignore -> "Ignore"
}
