package app.webora.browser.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that separates "the harness took a picture" from "there was something to photograph".
 *
 * `CI-002` proved that a frame can pass every semantic assertion while showing the wrong thing; this
 * is the same lie one layer down. `assertIsDisplayed()` is a claim about layout bounds, and a region
 * can have bounds and no pixels. So these cases are mostly about what must be refused.
 */
class RenderedContentPolicyTest {

    @Test fun aUniformRegionIsBlank() {
        val verdict = RenderedContentPolicy.verdict(IntArray(SAMPLE_COUNT) { PAGE_BACKGROUND })

        assertTrue("a single-colour region is not a rendered page", verdict is ContentVerdict.Blank)
        assertEquals(0.0, (verdict as ContentVerdict.Blank).differingFraction, 0.0)
        assertEquals(PAGE_BACKGROUND, verdict.modalColor)
        assertEquals(SAMPLE_COUNT, verdict.sampleCount)
    }

    /**
     * The reference integration's pages are near-white (`#FFF7FA`) and a blank surface is white too,
     * so nothing here may key on brightness. The modal colour is whatever dominates, whatever it is.
     */
    @Test fun aUniformWhiteRegionIsBlankToo() {
        val verdict = RenderedContentPolicy.verdict(IntArray(SAMPLE_COUNT) { WHITE })

        assertTrue(verdict is ContentVerdict.Blank)
        assertEquals(WHITE, (verdict as ContentVerdict.Blank).modalColor)
    }

    @Test fun aRenderedPageIsRendered() {
        val samples = IntArray(SAMPLE_COUNT) { index ->
            if (index % 10 == 0) INK else PAGE_BACKGROUND
        }

        val verdict = RenderedContentPolicy.verdict(samples)

        assertTrue("10% differing pixels is a drawn page", verdict is ContentVerdict.Rendered)
        assertEquals(0.1, (verdict as ContentVerdict.Rendered).differingFraction, 0.001)
    }

    @Test fun atTheThresholdIsRendered() {
        val differing = (SAMPLE_COUNT * RenderedContentPolicy.MINIMUM_DIFFERING_FRACTION).toInt()

        val verdict = RenderedContentPolicy.verdict(samplesWith(differing))

        assertTrue(verdict is ContentVerdict.Rendered)
    }

    @Test fun justBelowTheThresholdIsBlank() {
        val differing = (SAMPLE_COUNT * RenderedContentPolicy.MINIMUM_DIFFERING_FRACTION).toInt() - 1

        val verdict = RenderedContentPolicy.verdict(samplesWith(differing))

        assertTrue(verdict is ContentVerdict.Blank)
    }

    /** No samples is not evidence of rendering, and must not be an exception either. */
    @Test fun noSamplesIsBlank() {
        val verdict = RenderedContentPolicy.verdict(IntArray(0))

        assertTrue(verdict is ContentVerdict.Blank)
        assertEquals(0, (verdict as ContentVerdict.Blank).sampleCount)
    }

    /**
     * The case a "is anything non-uniform?" check would wave through: a cursor artefact, a
     * compression fringe, or one stray antialiased edge is not a page.
     */
    @Test fun oneStrayPixelIsNotRendering() {
        val verdict = RenderedContentPolicy.verdict(samplesWith(differing = 1))

        assertTrue(verdict is ContentVerdict.Blank)
    }

    private fun samplesWith(differing: Int) = IntArray(SAMPLE_COUNT) { index ->
        if (index < differing) INK else PAGE_BACKGROUND
    }

    private companion object {
        /** Close to what a 1080-wide page region yields at stride 4. */
        const val SAMPLE_COUNT = 40_000
        const val PAGE_BACKGROUND = 0xFFFFF7FA.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val INK = 0xFF2B1B24.toInt()
    }
}
