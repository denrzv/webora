package app.webora.browser.inspector

import app.webora.browser.siteskin.CacheableManifestSource
import app.webora.browser.siteskin.FetchRejection
import app.webora.browser.siteskin.ManifestCache
import app.webora.browser.siteskin.ManifestCacheKey
import app.webora.browser.siteskin.ManifestCacheMetadata
import app.webora.browser.siteskin.ManifestDiscoveryCoordinator
import app.webora.browser.siteskin.ManifestFetchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the browser recorded while discovering a manifest.
 *
 * Two of these assertions cover data the pipeline computed and then dropped before this ticket: a
 * rejection's diagnostics, and the HTTP status of a response the browser refused.
 */
class ManifestDiscoveryTraceTest {

    @Test fun `a rejection keeps the diagnostics that explain it`() = runTest {
        val recorder = SiteSkinTraceRecorder()
        val coordinator = coordinator(this, recorder, fetched(REJECTED_BODY))

        coordinator.onPageStarted("$ORIGIN/page")
        testScheduler.advanceUntilIdle()

        val validation = recorder.latest(ORIGIN)?.validation
        assertEquals(TraceValidationResult.REJECTED, validation?.result)
        assertNull("a rejected manifest has no trusted version to report", validation?.schemaVersion)
        assertEquals(listOf(TraceDiagnostic("SS-E-SCHEMA-INVALID", null)), validation?.diagnostics)
    }

    @Test fun `an accepted manifest keeps the warnings that explain its rendered chrome`() = runTest {
        val recorder = SiteSkinTraceRecorder()
        val coordinator = coordinator(this, recorder, fetched(WARNING_BODY))

        coordinator.onPageStarted("$ORIGIN/page")
        testScheduler.advanceUntilIdle()

        val validation = recorder.latest(ORIGIN)?.validation
        assertEquals(TraceValidationResult.ACCEPTED, validation?.result)
        assertEquals("1.0", validation?.schemaVersion)
        assertEquals(listOf("SS-W-FIELD-UNKNOWN"), validation?.diagnostics?.map(TraceDiagnostic::code))
    }

    @Test fun `a refused response reports its status and why it was refused`() = runTest {
        val recorder = SiteSkinTraceRecorder()
        val source = CacheableManifestSource { _, _ ->
            ManifestFetchResult.Rejected(FetchRejection.HTTP_ERROR, 404)
        }
        val coordinator = coordinator(this, recorder, source)

        coordinator.onPageStarted("$ORIGIN/page")
        testScheduler.advanceUntilIdle()

        val transport = recorder.latest(ORIGIN)?.transport
        assertEquals(TraceTransportOutcome.REJECTED, transport?.outcome)
        assertEquals(404, transport?.httpStatus)
        assertEquals(FetchRejection.HTTP_ERROR, transport?.rejection)
        assertEquals("$ORIGIN/.well-known/siteskin.json", transport?.manifestUrl)
        assertEquals(TraceValidationResult.NOT_RUN, recorder.latest(ORIGIN)?.validation?.result)
    }

    @Test fun `a page that was never eligible still says so`() = runTest {
        val recorder = SiteSkinTraceRecorder()
        val coordinator = coordinator(this, recorder, fetched(VALID_BODY))

        coordinator.onPageStarted("http://plain.example/page")
        testScheduler.advanceUntilIdle()

        val record = recorder.latest("http://plain.example")
        assertEquals(TraceTransportOutcome.NOT_ELIGIBLE, record?.transport?.outcome)
        assertEquals(FetchRejection.NOT_HTTPS, record?.transport?.rejection)
        assertEquals(TraceCacheState.NOT_APPLICABLE, record?.transport?.cacheState)
        assertEquals(TraceValidationResult.NOT_RUN, record?.validation?.result)
    }

    @Test fun `each cache path names itself`() = runTest {
        assertEquals(TraceCacheState.MISS, cacheStateOf(this, cached = null, fetched(VALID_BODY)))
        assertEquals(TraceCacheState.REFETCHED, cacheStateOf(this, stale(), fetched(VALID_BODY)))
        assertEquals(
            TraceCacheState.REVALIDATED,
            cacheStateOf(this, stale(), notModified()),
        )
        assertEquals(
            TraceCacheState.STALE_REPLAYED,
            cacheStateOf(this, stale(), CacheableManifestSource { _, _ -> ManifestFetchResult.Unavailable }),
        )
        assertEquals(
            TraceCacheState.FRESH_HIT,
            cacheStateOf(this, fresh(), CacheableManifestSource { _, _ -> error("no request may be made") }),
        )
    }

    @Test fun `one navigation produces exactly one record`() = runTest {
        val recorder = SiteSkinTraceRecorder()
        val coordinator = coordinator(this, recorder, fetched(VALID_BODY))
        val before = recorder.version

        coordinator.onPageStarted("$ORIGIN/page")
        testScheduler.advanceUntilIdle()

        assertEquals(before + 1, recorder.version)
    }

    @Test fun `a superseded navigation records nothing`() = runTest {
        // A trace written past the cancellation point would describe an origin the browser left.
        val recorder = SiteSkinTraceRecorder()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val source = CacheableManifestSource { origin, _ ->
            if (origin == ORIGIN) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            ManifestFetchResult.Fetched(VALID_BODY, ManifestCacheMetadata())
        }
        val coordinator = coordinator(this, recorder, source)

        coordinator.onPageStarted("$ORIGIN/page")
        testScheduler.runCurrent()
        firstStarted.await()
        coordinator.onPageStarted("$OTHER_ORIGIN/page")
        testScheduler.advanceUntilIdle()

        assertNull(recorder.latest(ORIGIN))
        assertTrue(recorder.latest(OTHER_ORIGIN) != null)
    }

    private fun cacheStateOf(
        scope: TestScope,
        cached: ManifestCache?,
        source: CacheableManifestSource,
    ): TraceCacheState {
        val recorder = SiteSkinTraceRecorder()
        val coordinator = coordinator(scope, recorder, source, cached ?: ManifestCache { 0 })
        coordinator.onPageStarted("$ORIGIN/page")
        scope.testScheduler.advanceUntilIdle()
        return recorder.latest(ORIGIN)!!.transport.cacheState
    }

    private fun coordinator(
        scope: CoroutineScope,
        recorder: SiteSkinTraceRecorder,
        source: CacheableManifestSource,
        cache: ManifestCache = ManifestCache { 0 },
    ) = ManifestDiscoveryCoordinator(scope, source, cache, recorder) { }

    private fun fetched(body: ByteArray) = CacheableManifestSource { _, _ ->
        ManifestFetchResult.Fetched(body, ManifestCacheMetadata())
    }

    private fun notModified() = CacheableManifestSource { _, _ ->
        ManifestFetchResult.NotModified(ManifestCacheMetadata(etag = "tag"))
    }

    /** A cache holding an entry whose freshness has already lapsed. */
    private fun stale(): ManifestCache = ManifestCache { 0 }.apply {
        put(ManifestCacheKey(ORIGIN, "1.0"), VALID_BODY, ManifestCacheMetadata(etag = "tag"))
    }

    private fun fresh(): ManifestCache = ManifestCache { 0 }.apply {
        put(ManifestCacheKey(ORIGIN, "1.0"), VALID_BODY, ManifestCacheMetadata(cacheControl = "max-age=60"))
    }

    private companion object {
        const val ORIGIN = "https://shop.example"
        const val OTHER_ORIGIN = "https://other.example"
        val VALID_BODY = """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"}}""".toByteArray()
        val WARNING_BODY =
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},"surprise":1}""".toByteArray()
        val REJECTED_BODY = "{}".toByteArray()
    }
}
