package app.webora.browser.siteskin

import dev.siteskin.core.ManifestSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestDiscoveryCoordinatorTest {
    @Test fun `filters non https pages without fetching`() = runTest {
        val origins = mutableListOf<String>()
        val coordinator = ManifestDiscoveryCoordinator(this, ManifestSource { origins += it; VALID }) { }

        coordinator.onPageStarted("http://example.com/page")
        coordinator.onPageStarted("not a url")
        testScheduler.advanceUntilIdle()

        assertTrue(origins.isEmpty())
    }

    @Test fun `validates fetched bytes for the observed canonical origin`() = runTest {
        val outcomes = mutableListOf<ManifestDiscoveryOutcome>()
        val coordinator = ManifestDiscoveryCoordinator(this, ManifestSource { VALID }, outcomes::add)

        coordinator.onPageStarted("https://EXAMPLE.com:443/page?q=1")
        testScheduler.advanceUntilIdle()

        assertTrue(outcomes.single() is ManifestDiscoveryOutcome.Available)
    }

    @Test fun `returns immediately and cancels superseded discovery`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val origins = mutableListOf<String>()
        val outcomes = mutableListOf<ManifestDiscoveryOutcome>()
        val source = ManifestSource { origin ->
            origins += origin
            if (origin == "https://first.example") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            VALID
        }
        val coordinator = ManifestDiscoveryCoordinator(this, source, outcomes::add)

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
        val coordinator = ManifestDiscoveryCoordinator(this, ManifestSource { body }, outcomes::add)

        coordinator.onPageStarted("https://example.com")
        testScheduler.advanceUntilIdle()
        body = "{}".toByteArray()
        coordinator.onPageStarted("https://example.com/next")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(ManifestDiscoveryOutcome.Unavailable, ManifestDiscoveryOutcome.Unavailable), outcomes)
    }

    private companion object {
        val VALID = """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"}}"""
            .toByteArray()
    }
}
