package app.webora.browser.siteskin

import app.webora.browser.inspector.BrandAssetStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Which brand-asset outcomes earn a second chance on a later page start.
 *
 * `NET-004`'s exclusions, unchanged, plus the one thing it did not have: an opportunity to ask again
 * after its burst is spent. A field inspector reading recorded `TRANSPORT_UNAVAILABLE` after
 * `32863 ms` and `Attempts 3` — three full call timeouts — and then nothing ever asked again.
 */
class BrandAssetRetryTest {
    @Test
    fun `only a transport failure earns another attempt`() {
        assertTrue(retriesBrandAsset(BrandAssetStage.TRANSPORT_UNAVAILABLE))

        BrandAssetStage.entries
            .filterNot { it == BrandAssetStage.TRANSPORT_UNAVAILABLE }
            .forEach { assertFalse("$it must not be retried", retriesBrandAsset(it)) }
    }

    /**
     * Every one of `NET-004`'s exclusions, named rather than derived.
     *
     * The reflective sweep above would keep passing if a stage were *added* and quietly made
     * retryable-by-omission; these are the three the original ticket argued about, so they are
     * asserted by name where a reader will find the argument.
     */
    @Test
    fun `a rejection, a decode failure and an undeclared logo are never retried`() {
        assertFalse(
            "the server answered and the browser declined",
            retriesBrandAsset(BrandAssetStage.TRANSPORT_REJECTED),
        )
        assertFalse("the same bytes decode the same way", retriesBrandAsset(BrandAssetStage.DECODE_FAILED))
        assertFalse("there is nothing to request", retriesBrandAsset(BrandAssetStage.NOT_DECLARED))
        assertFalse("a success is not a reason to ask again", retriesBrandAsset(BrandAssetStage.DECODED))
    }

    /** Nothing recorded yet is not a failure, and must not trigger a load nobody asked for. */
    @Test
    fun `an absent stage does not retry`() {
        assertFalse(retriesBrandAsset(null))
    }

    /**
     * The retry is driven by a page start and keyed into the load effect — it cannot free-run.
     *
     * Written as a source contract because the wiring is inside a `@Composable`. The two halves that
     * matter: the generation is only incremented under `retriesBrandAsset`, and it is a key of the
     * load effect. A generation that nothing keys on would retry never; one incremented outside that
     * guard would retry on every page start of every origin, including after a 404.
     */
    @Test
    fun `the generation gates the load effect and is bumped only by the decision`() {
        val source = executableLines(File("src/main/java/app/webora/browser/browser/BrowserScreen.kt"))

        assertTrue(
            "the load effect must re-run when the generation changes",
            "LaunchedEffect(integrated?.configuration, brandAssetGeneration)" in source,
        )
        assertTrue(
            "and the generation must only move when the decision says so",
            "if (retriesBrandAsset(brandAssetStage)) brandAssetGeneration += 1" in source,
        )
        assertEquals(
            "exactly one place may advance it",
            1,
            source.split("brandAssetGeneration += 1").size - 1,
        )
        assertTrue(
            "the recorded stage must come from the load's own trace",
            "brandAssetStage = loaded.trace.stage" in source,
        )
    }

    private companion object {
        fun executableLines(file: File): String {
            check(file.exists()) { "source not found at ${file.absolutePath}" }
            return file.readLines()
                .filterNot { line ->
                    val trimmed = line.trimStart()
                    trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
                }
                .joinToString("\n")
        }
    }
}
