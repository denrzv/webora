package app.webora.browser.siteskin

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestDiscoveryCoordinatorTest {
    @Test fun `filters non https pages without fetching`() = runTest {
        val origins = mutableListOf<String>()
        val coordinator = ManifestDiscoveryCoordinator(this, source { origins += it; VALID }) { }

        coordinator.onPageStarted("http://example.com/page")
        coordinator.onPageStarted("not a url")
        testScheduler.advanceUntilIdle()

        assertTrue(origins.isEmpty())
    }

    @Test fun `validates fetched bytes for the observed canonical origin`() = runTest {
        val outcomes = mutableListOf<ManifestDiscoveryOutcome>()
        val coordinator = ManifestDiscoveryCoordinator(this, source { VALID }, onOutcome = outcomes::add)

        coordinator.onPageStarted("https://EXAMPLE.com:443/page?q=1", generation = 7)
        testScheduler.advanceUntilIdle()

        val outcome = outcomes.single() as ManifestDiscoveryOutcome.Available
        assertEquals("https://example.com", outcome.origin.canonical)
        assertEquals(7, outcome.generation)
    }

    @Test fun `returns immediately and cancels superseded discovery`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val origins = mutableListOf<String>()
        val outcomes = mutableListOf<ManifestDiscoveryOutcome>()
        val source = source { origin ->
            origins += origin
            if (origin == "https://first.example") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            VALID
        }
        val coordinator = ManifestDiscoveryCoordinator(this, source, onOutcome = outcomes::add)

        coordinator.onPageStarted("https://first.example/page")
        testScheduler.runCurrent()
        firstStarted.await()
        coordinator.onPageStarted("https://second.example/page")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("https://first.example", "https://second.example"), origins)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes.single() is ManifestDiscoveryOutcome.Available)
    }

    @Test fun `reports absent and rejected manifests as unavailable`() = runTest {
        val outcomes = mutableListOf<ManifestDiscoveryOutcome>()
        var body: ByteArray? = null
        val coordinator = ManifestDiscoveryCoordinator(this, source { body }, onOutcome = outcomes::add)

        coordinator.onPageStarted("https://example.com")
        testScheduler.advanceUntilIdle()
        body = "{}".toByteArray()
        coordinator.onPageStarted("https://example.com/next")
        testScheduler.advanceUntilIdle()

        assertTrue(outcomes.all { it is ManifestDiscoveryOutcome.Unavailable })
    }

    @Test fun `fresh cache avoids transport and remains exact origin`() = runTest {
        val cache = ManifestCache { 0 }
        cache.put(ManifestCacheKey("https://shop.example", "1.0"), VALID, ManifestCacheMetadata("max-age=60"))
        val requested = mutableListOf<String>()
        val outcomes = mutableListOf<ManifestDiscoveryOutcome>()
        val transport = CacheableManifestSource { origin, _ ->
            requested += origin
            ManifestFetchResult.Rejected
        }
        val coordinator = ManifestDiscoveryCoordinator(this, transport, cache, outcomes::add)

        coordinator.onPageStarted("https://other.example")
        testScheduler.advanceUntilIdle()
        coordinator.onPageStarted("https://shop.example/products")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("https://other.example"), requested)
        assertTrue(outcomes.first() is ManifestDiscoveryOutcome.Unavailable)
        assertTrue(outcomes.last() is ManifestDiscoveryOutcome.Available)
    }

    @Test fun `stale entry revalidates and only unavailable transport reuses it`() = runTest {
        val cache = ManifestCache { 1_000 }
        cache.put(
            ManifestCacheKey("https://shop.example", "1.0"),
            VALID,
            ManifestCacheMetadata(etag = "tag", lastModified = "yesterday"),
        )
        val validators = mutableListOf<ManifestRequestValidators>()
        var result: ManifestFetchResult = ManifestFetchResult.Rejected
        val outcomes = mutableListOf<ManifestDiscoveryOutcome>()
        val source = CacheableManifestSource { _, sent -> validators += sent; result }
        val coordinator = ManifestDiscoveryCoordinator(this, source, cache, outcomes::add)

        coordinator.onPageStarted("https://shop.example")
        testScheduler.advanceUntilIdle()
        result = ManifestFetchResult.Unavailable
        coordinator.onPageStarted("https://shop.example")
        testScheduler.advanceUntilIdle()

        assertEquals(ManifestRequestValidators("tag", "yesterday"), validators.first())
        assertTrue(outcomes.first() is ManifestDiscoveryOutcome.Unavailable)
        assertTrue(outcomes.last() is ManifestDiscoveryOutcome.Available)
    }

    @Test fun `accepted fetch is cached and not modified refreshes it`() = runTest {
        var now = 0L
        val cache = ManifestCache { now }
        val results = ArrayDeque<ManifestFetchResult>().apply {
            add(ManifestFetchResult.Fetched(VALID, ManifestCacheMetadata(etag = "tag")))
            add(ManifestFetchResult.NotModified(ManifestCacheMetadata(cacheControl = "max-age=60")))
        }
        val outcomes = mutableListOf<ManifestDiscoveryOutcome>()
        val coordinator = ManifestDiscoveryCoordinator(
            this,
            CacheableManifestSource { _, _ -> results.removeFirst() },
            cache,
            outcomes::add,
        )

        coordinator.onPageStarted("https://shop.example")
        testScheduler.advanceUntilIdle()
        now = 1
        coordinator.onPageStarted("https://shop.example")
        testScheduler.advanceUntilIdle()
        coordinator.onPageStarted("https://shop.example")
        testScheduler.advanceUntilIdle()

        assertEquals(3, outcomes.size)
        assertTrue(outcomes.all { it is ManifestDiscoveryOutcome.Available })
        assertEquals("tag", cache.active("https://shop.example")?.metadata?.etag)
        assertTrue(results.isEmpty())
    }

    private fun source(fetch: suspend (String) -> ByteArray?): CacheableManifestSource =
        CacheableManifestSource { origin, _ ->
            fetch(origin)?.let { ManifestFetchResult.Fetched(it, ManifestCacheMetadata()) }
                ?: ManifestFetchResult.Unavailable
        }

    private companion object {
        val VALID = """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"}}"""
            .toByteArray()
    }
}
