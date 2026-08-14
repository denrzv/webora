package app.webora.browser.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Not queryable yet" and "not present" must stop being the same outcome.
 *
 * Hosted run 13 is the case these tests exist for. The harness identified the one obstruction it is
 * permitted to clear, looked once for the button, found zero, and failed the run — twice, with a
 * byte-identical assertion, producing no frames. The verdict meaning *this is clearable* was the
 * only one that could not be retried, while the verdict meaning *this is unrecoverable* was retried
 * patiently for twenty seconds.
 *
 * The asymmetry these tests pin is the whole ticket: **only the zero case becomes patient.** Two or
 * more candidates is genuine ambiguity and still fails immediately, because the button ids are not
 * public API and a wrong press on an error dialog can close an app.
 */
class AnrDismissalPolicyTest {

    @Test fun `one sanctioned affordance is pressed`() {
        val verdict = AnrDismissalPolicy.verdict(found(pressable = listOf(WAIT)))

        assertEquals(DismissalVerdict.Press(WAIT), verdict)
    }

    /**
     * The fail-closed half. `android:id/button2` is the negative button of every `AlertDialog`, so
     * two matches is not a tie to break — it is a reason to stop.
     */
    @Test fun `two affordances are ambiguous and never pressed`() {
        val verdict = AnrDismissalPolicy.verdict(found(pressable = listOf(BUTTON2, WAIT)))

        assertEquals(DismissalVerdict.Ambiguous(listOf(BUTTON2, WAIT)), verdict)
    }

    /** A null `rootInActiveWindow` yielded zero candidates and was reported as a missing button. */
    @Test fun `a missing accessibility root is not yet`() {
        val verdict = AnrDismissalPolicy.verdict(found(rootAvailable = false))

        assertTrue("no tree to search is an observation gap, not an absence", verdict is DismissalVerdict.NotYet)
    }

    /**
     * Without `FLAG_REPORT_VIEW_IDS`, `findAccessibilityNodeInfosByViewId` returns nothing at all.
     * The guard's own KDoc named this failure and then let it fail the run anyway.
     */
    @Test fun `view id reporting off is not yet`() {
        val verdict = AnrDismissalPolicy.verdict(found(viewIdReportingEnabled = false))

        assertTrue(verdict is DismissalVerdict.NotYet)
        assertTrue(
            "the reason must say the lookup could not report ids, not that no button exists",
            (verdict as DismissalVerdict.NotYet).reason.contains("view-id reporting"),
        )
    }

    @Test fun `present but unpressable is not yet`() {
        val verdict = AnrDismissalPolicy.verdict(found(matchedButNotPressable = listOf(WAIT)))

        assertTrue(verdict is DismissalVerdict.NotYet)
        assertTrue(
            "the reason must name the id that was there, so a reader can tell this from an empty tree",
            (verdict as DismissalVerdict.NotYet).reason.contains(WAIT),
        )
    }

    @Test fun `nothing found is not yet`() {
        val verdict = AnrDismissalPolicy.verdict(found())

        assertTrue("the plain zero is retryable; the deadline decides", verdict is DismissalVerdict.NotYet)
    }

    /**
     * The timeout message is whatever reason the loop stored last, so identical strings would make
     * forty polls unreadable. Each zero has a different cause and must read as one.
     */
    @Test fun `every not yet carries a distinct reason`() {
        val reasons = zeroStates().map { AnrDismissalPolicy.verdict(it) }
            .filterIsInstance<DismissalVerdict.NotYet>()
            .map { it.reason }

        assertEquals("every zero state must produce a NotYet", zeroStates().size, reasons.size)
        assertEquals("distinct causes must not share a reason string", 4, reasons.distinct().size)
    }

    /**
     * The coverage guard. `Ambiguous` is the only immediate failure, so no input that found nothing
     * may reach it — a later edit that made the empty cases fail closed again would reintroduce
     * exactly the defect this ticket removed.
     */
    @Test fun `ambiguity is not reachable from a zero state`() {
        val verdicts = zeroStates().map { AnrDismissalPolicy.verdict(it) }

        assertTrue(
            "a zero must never fail immediately",
            verdicts.none { it is DismissalVerdict.Ambiguous },
        )
        assertTrue("nor may it press anything", verdicts.none { it is DismissalVerdict.Press })
    }

    /** Every combination the guard can observe with an empty `pressable` list. */
    private fun zeroStates(): List<WaitAffordances> = listOf(false, true).flatMap { root ->
        listOf(false, true).flatMap { reporting ->
            listOf(emptyList(), listOf(WAIT)).map { matched ->
                found(matchedButNotPressable = matched, rootAvailable = root, viewIdReportingEnabled = reporting)
            }
        }
    }

    private fun found(
        pressable: List<String> = emptyList(),
        matchedButNotPressable: List<String> = emptyList(),
        rootAvailable: Boolean = true,
        viewIdReportingEnabled: Boolean = true,
    ) = WaitAffordances(pressable, matchedButNotPressable, rootAvailable, viewIdReportingEnabled)

    private companion object {
        const val WAIT = "android:id/aerr_wait"
        const val BUTTON2 = "android:id/button2"
    }
}
