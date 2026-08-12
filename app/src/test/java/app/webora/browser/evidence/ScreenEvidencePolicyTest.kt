package app.webora.browser.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate for what the screenshot harness may clear off the screen.
 *
 * This decision runs on a device this checkout cannot start, which is exactly why it was separated
 * from the code that touches one. The dangerous edit is not a crash — it is a widening: one more
 * package on the allow-list, one more accepted title prefix, one fewer ambiguity treated as a
 * failure. Each of those has a case here that turns red, and each was verified by making the
 * widening and watching it turn red (see `docs/tasklist/CI-002.md`, TASK-3).
 *
 * The fragments are shaped like real `dumpsys window` output, including the repeated
 * `mCurrentFocus=` line a real dump carries.
 */
class ScreenEvidencePolicyTest {

    @Test
    fun `the app owning the focused window is the only capturable state`() {
        val verdict = ScreenEvidencePolicy.focusVerdict(dumpWith(APP_WINDOW), APP_PACKAGE)

        assertEquals(FocusVerdict.OwnedByApp(APP_WINDOW), verdict)
    }

    @Test
    fun `the known System UI ANR dialog is the one thing that may be waited on`() {
        val title = "Application Not Responding: com.android.systemui"

        val verdict = ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE)

        assertEquals(FocusVerdict.DismissableSystemAnr("com.android.systemui", title), verdict)
    }

    @Test
    fun `a Webora ANR is never dismissed`() {
        // The failure this whole object exists to prevent. A harness that cleared this dialog would
        // photograph whatever was behind Webora's own ANR and report a green run.
        val title = "Application Not Responding: $APP_PACKAGE"

        val verdict = ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE)

        assertBlocked(verdict, APP_PACKAGE)
    }

    @Test
    fun `a Webora crash dialog is never dismissed`() {
        val title = "Application Error: $APP_PACKAGE"

        val verdict = ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE)

        assertBlocked(verdict, "crashed")
    }

    @Test
    fun `a System UI crash is not the known dialog and is not dismissable`() {
        // Only the not-responding form is on the allow-list. A crashed System UI is a device that
        // should not be producing product evidence at all, and `Wait` is not even offered.
        val title = "Application Error: com.android.systemui"

        val verdict = ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE)

        assertBlocked(verdict, "crashed")
    }

    @Test
    fun `an ANR in some third process is not dismissable either`() {
        val title = "Application Not Responding: com.android.chrome"

        val verdict = ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE)

        assertBlocked(verdict, "com.android.chrome")
    }

    @Test
    fun `an unrecognised window fails rather than being photographed`() {
        val title = "com.google.android.apps.nexuslauncher/com.google.android.apps.nexuslauncher.NexusLauncherActivity"

        val verdict = ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE)

        assertBlocked(verdict, "unrecognised")
    }

    @Test
    fun `a null focus is not readiness`() {
        val verdict = ScreenEvidencePolicy.focusVerdict(
            "  mCurrentFocus=null\n  mFocusedApp=null\n",
            APP_PACKAGE,
        )

        assertBlocked(verdict, "null")
    }

    @Test
    fun `a dump with no focus line at all fails closed`() {
        // The no-op failure mode: a parser that matches nothing must not look like a pass.
        val verdict = ScreenEvidencePolicy.focusVerdict("WINDOW MANAGER WINDOWS\n  no focus here\n", APP_PACKAGE)

        assertBlocked(verdict, "no mCurrentFocus")
    }

    @Test
    fun `two focused windows that disagree fail closed`() {
        val dump = buildString {
            appendLine("  mCurrentFocus=Window{3f2a1b u0 $APP_WINDOW}")
            appendLine("  mCurrentFocus=Window{9c41e7 u0 Application Not Responding: com.android.systemui}")
        }

        val verdict = ScreenEvidencePolicy.focusVerdict(dump, APP_PACKAGE)

        assertBlocked(verdict, "disagree")
    }

    @Test
    fun `the same window repeated across a dump is still one window`() {
        // A real `dumpsys window` prints mCurrentFocus more than once. Treating repetition as
        // ambiguity would fail every healthy run.
        val dump = dumpWith(APP_WINDOW) + dumpWith(APP_WINDOW)

        assertEquals(FocusVerdict.OwnedByApp(APP_WINDOW), ScreenEvidencePolicy.focusVerdict(dump, APP_PACKAGE))
    }

    @Test
    fun `a titleless window is not assumed to be ours`() {
        val verdict = ScreenEvidencePolicy.focusVerdict("  mCurrentFocus=Window{3f2a1b u0 }\n", APP_PACKAGE)

        assertBlocked(verdict, "no title")
    }

    @Test
    fun `a package that merely contains ours is not ours`() {
        // Ownership is a whole-token match, so neither an evil twin with a longer name nor one that
        // ends with our package can pass as Webora.
        val longerName = ScreenEvidencePolicy.focusVerdict(
            dumpWith("app.webora.browser.debugger/com.example.Main"),
            APP_PACKAGE,
        )
        val longerPrefix = ScreenEvidencePolicy.focusVerdict(
            dumpWith("com.evil.app.webora.browser.debug/com.example.Main"),
            APP_PACKAGE,
        )

        assertBlocked(longerName, "unrecognised")
        assertBlocked(longerPrefix, "unrecognised")
    }

    @Test
    fun `a window Android did not title as package slash activity is still ours`() {
        // A dialog, popup or presentation window is not guaranteed to be titled `package/activity`,
        // and the consent screenshot is taken with a dialog focused. Requiring that exact shape
        // would break the journey this guard exists to protect.
        val verdict = ScreenEvidencePolicy.focusVerdict(dumpWith("PopupWindow:$APP_PACKAGE"), APP_PACKAGE)

        assertTrue("expected OwnedByApp, got $verdict", verdict is FocusVerdict.OwnedByApp)
    }

    private fun assertBlocked(verdict: FocusVerdict, expectedFragment: String) {
        assertTrue("expected Blocked, got $verdict", verdict is FocusVerdict.Blocked)
        val reason = (verdict as FocusVerdict.Blocked).reason
        assertTrue("`$reason` does not mention `$expectedFragment`", reason.contains(expectedFragment))
    }

    private fun dumpWith(title: String): String = buildString {
        appendLine("WINDOW MANAGER WINDOWS (dumpsys window windows)")
        appendLine("  Window #0 Window{3f2a1b u0 $title}:")
        appendLine("    mDisplayId=0 rootTaskId=1")
        appendLine("  mCurrentFocus=Window{3f2a1b u0 $title}")
        appendLine("  mFocusedApp=ActivityRecord{7d3e2f u0 $APP_WINDOW t42}")
    }

    private companion object {
        const val APP_PACKAGE = "app.webora.browser.debug"
        const val APP_WINDOW = "app.webora.browser.debug/app.webora.browser.MainActivity"
    }
}
