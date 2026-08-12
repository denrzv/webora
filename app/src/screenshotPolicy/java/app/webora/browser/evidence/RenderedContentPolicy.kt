package app.webora.browser.evidence

/** Whether a sampled screen region had anything drawn in it. */
sealed interface ContentVerdict {

    /** Enough of the region differs from its dominant colour to be a drawn page. */
    data class Rendered(val differingFraction: Double) : ContentVerdict

    /** Carries what was measured, so a refusal explains itself instead of needing a rerun. */
    data class Blank(
        val differingFraction: Double,
        val modalColor: Int,
        val sampleCount: Int,
    ) : ContentVerdict
}

/**
 * Decides whether a region of a captured screen has been drawn.
 *
 * **This is the second half of the lie `CI-002` started catching.** There, three frames passed every
 * semantic assertion while covered by an ANR dialog. Here, a frame passes every assertion while
 * showing an empty page — because `assertIsDisplayed()` asserts a node has bounds, not that anything
 * was painted into them, and `waitForIdle()` waits for Compose, which does not track a `WebView`'s
 * paint. Both are a green job with worthless evidence.
 *
 * The statistic is the fraction of samples differing from the **modal** sampled colour, which is the
 * technique `DEVX-002` used to catch a font-less host writing structurally perfect PNGs with
 * invisible labels: count ink, do not check that a file exists. Brightness would be wrong here — the
 * reference integration's pages are near-white and so is an undrawn surface.
 *
 * [MINIMUM_DIFFERING_FRACTION] is deliberately low and is not a content-quality bar. The failure
 * being detected is *nothing drawn* (≈0%); a rendered page is far above 1%. The wide margin is the
 * point: a tight threshold would trade a real defect for intermittent redness on a slow emulator,
 * which erodes trust in the evidence as surely as a blank frame does.
 *
 * **Unlike [ScreenEvidencePolicy], this rule is not website-independent, and that is unavoidable:**
 * it reads pixels the website drew. The achievable property is directional — website content can
 * make this check **refuse**, never permit. A site rendering blank fails the capture and reddens the
 * run; no page content can produce a capture that would otherwise have been refused, and none
 * reaches [ScreenEvidencePolicy]'s ownership rule, which has already returned before this begins.
 *
 * It takes an `IntArray` of ARGB samples rather than a `Bitmap` on purpose. An Android type would
 * drag the decision into a source set `./gradlew test` cannot compile — the exact mistake that let a
 * compile error in `BrowserFontScaleTest` survive a green `scripts/pre-commit-check.sh`. Sampling is
 * interaction and lives in the guard; deciding lives here.
 */
object RenderedContentPolicy {

    /** One percent of sampled pixels. See the class note: a liveness bar, not a quality bar. */
    const val MINIMUM_DIFFERING_FRACTION: Double = 0.01

    fun verdict(samples: IntArray): ContentVerdict {
        if (samples.isEmpty()) {
            // No samples is not evidence of rendering. Returning Rendered here, or throwing, would
            // both turn an unmeasurable region into something other than a plain refusal.
            return ContentVerdict.Blank(differingFraction = 0.0, modalColor = 0, sampleCount = 0)
        }

        val counts = HashMap<Int, Int>()
        for (sample in samples) {
            counts[sample] = (counts[sample] ?: 0) + 1
        }
        val modal = counts.maxBy { it.value }
        val differingFraction = (samples.size - modal.value).toDouble() / samples.size

        return if (differingFraction >= MINIMUM_DIFFERING_FRACTION) {
            ContentVerdict.Rendered(differingFraction)
        } else {
            ContentVerdict.Blank(differingFraction, modal.key, samples.size)
        }
    }
}
