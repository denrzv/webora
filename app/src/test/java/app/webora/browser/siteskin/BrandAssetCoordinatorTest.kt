package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandAssetCoordinatorTest {
    @Test fun `superseded load cannot publish even when loader ignores cancellation`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val published = mutableListOf<BrandAsset>()
        val coordinator = BrandAssetCoordinator(
            this,
            loadAsset = { configuration ->
                if (configuration.site.name == "First") withContext(NonCancellable) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                BrandAsset.Monogram(configuration.site.name.take(1))
            },
            onAsset = published::add,
        )

        coordinator.load(configuration("First"))
        testScheduler.runCurrent()
        firstStarted.await()
        coordinator.load(configuration("Second"))
        releaseFirst.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(BrandAsset.Monogram("S")), published)
    }

    @Test fun `cancel prevents publication and current result publishes`() = runTest {
        val release = CompletableDeferred<Unit>()
        val published = mutableListOf<BrandAsset>()
        val coordinator = BrandAssetCoordinator(
            this,
            loadAsset = { release.await(); BrandAsset.Monogram("B") },
            onAsset = published::add,
        )

        coordinator.load(configuration("Bloom"))
        testScheduler.runCurrent()
        coordinator.cancel()
        release.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertTrue(published.isEmpty())

        val immediate = BrandAssetCoordinator(this, { BrandAsset.Monogram("B") }, published::add)
        immediate.load(configuration("Bloom"))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(BrandAsset.Monogram("B")), published)
    }

    private fun configuration(name: String) = SiteSkinValidator.validate(
        """{"schemaVersion":"1.0","site":{"id":"brand","name":"$name"}}"""
            .byteInputStream(),
        "https://brand.example",
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
}
