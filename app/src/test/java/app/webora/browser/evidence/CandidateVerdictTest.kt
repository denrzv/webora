package app.webora.browser.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame saved must be the frame validated — on both properties, at the same moment.
 *
 * Hosted run 14 is the case these tests exist for: a blank page region, then a System UI ANR dialog
 * whose own pixels cleared the liveness bar, and a green job with the dialog in the artifact.
 */
class CandidateVerdictTest {

    @Test fun `owned and rendered is accepted`() {
        assertEquals(CandidateVerdict.Accept, candidateVerdict(owned(), rendered()))
    }

    /** Run 14, exactly: the page did not render, the dialog did, and the dialog is not the page. */
    @Test fun `rendered under a system dialog is retried`() {
        val verdict = candidateVerdict(systemAnr(), rendered(0.3047865285496029))

        assertTrue("a dialog-owned screen must never be accepted", verdict is CandidateVerdict.Retry)
        assertTrue(
            "the reason must name the window that owned the screen",
            (verdict as CandidateVerdict.Retry).reason.contains("com.android.systemui"),
        )
    }

    @Test fun `rendered under a blocked screen is retried`() {
        val verdict = candidateVerdict(FocusVerdict.Blocked("mCurrentFocus=null"), rendered())

        assertTrue(verdict is CandidateVerdict.Retry)
        assertTrue(
            "the blocking reason must survive into the record",
            (verdict as CandidateVerdict.Retry).reason.contains("mCurrentFocus=null"),
        )
    }

    @Test fun `owned but blank is retried`() {
        val verdict = candidateVerdict(owned(), blank())

        assertTrue(verdict is CandidateVerdict.Retry)
        assertTrue(
            "the reason must carry the measurement, so the record can be read without the bitmap",
            (verdict as CandidateVerdict.Retry).reason.contains("0.0023"),
        )
    }

    @Test fun `every retry carries a reason`() {
        val retries = everyFocus().flatMap { focus ->
            listOf(candidateVerdict(focus, rendered()), candidateVerdict(focus, blank()))
        }.filterIsInstance<CandidateVerdict.Retry>()

        assertTrue("expected retries to exercise", retries.size >= MINIMUM_RETRIES)
        assertTrue(
            "an empty reason reaches the diagnostic file and tells the next reader nothing",
            retries.none { it.reason.isBlank() },
        )
    }

    /**
     * The coverage guard. `Rendered` must accept in exactly one row of the table, and a later edit
     * that lets content decide alone fails here rather than on a device six weeks later.
     */
    @Test fun `content alone never accepts`() {
        val accepting = everyFocus().filter { candidateVerdict(it, rendered()) == CandidateVerdict.Accept }

        assertEquals(
            "a rendered region is evidence only when Webora owns the screen; accepted for: " +
                accepting.joinToString { it::class.simpleName.orEmpty() },
            1,
            accepting.size,
        )
        assertTrue(accepting.single() is FocusVerdict.OwnedByApp)
    }

    private companion object {
        const val MINIMUM_RETRIES = 5

        fun owned() = FocusVerdict.OwnedByApp("app.webora.browser.debug/app.webora.browser.MainActivity")

        fun systemAnr() = FocusVerdict.DismissableSystemAnr(
            processName = "com.android.systemui",
            title = "Application Not Responding: com.android.systemui",
        )

        fun everyFocus() = listOf(
            owned(),
            systemAnr(),
            FocusVerdict.Blocked("an unrecognised window owns the screen"),
        )

        fun rendered(fraction: Double = 0.7530481592174976) = ContentVerdict.Rendered(fraction)

        fun blank() = ContentVerdict.Blank(
            differingFraction = 0.0023084800967043298,
            modalColor = -1,
            sampleCount = 119126,
        )
    }
}
