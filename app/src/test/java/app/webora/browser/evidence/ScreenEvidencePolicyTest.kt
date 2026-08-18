package app.webora.browser.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val title = "Application Not Responding: $APP_PACKAGE"
        assertBlocked(ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE), APP_PACKAGE)
    }

    @Test
    fun `a Webora crash dialog is never dismissed`() {
        val title = "Application Error: $APP_PACKAGE"
        assertBlocked(ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE), "crashed")
    }

    @Test
    fun `a System UI crash is not the known dialog and is not dismissable`() {
        val title = "Application Error: com.android.systemui"
        assertBlocked(ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE), "crashed")
    }

    @Test
    fun `an ANR in some third process is not dismissable either`() {
        val title = "Application Not Responding: com.android.chrome"
        assertBlocked(ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE), "com.android.chrome")
    }

    @Test
    fun `an unrecognised window fails rather than being photographed`() {
        val title = "com.google.android.apps.nexuslauncher/com.google.android.apps.nexuslauncher.NexusLauncherActivity"
        assertBlocked(ScreenEvidencePolicy.focusVerdict(dumpWith(title), APP_PACKAGE), "unrecognised")
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
        val verdict = ScreenEvidencePolicy.focusVerdict("WINDOW MANAGER WINDOWS\n  no focus here\n", APP_PACKAGE)
        assertBlocked(verdict, "no mCurrentFocus")
    }

    @Test
    fun `two focused windows that disagree fail closed`() {
        val dump = buildString {
            appendLine("  mCurrentFocus=Window{3f2a1b u0 $APP_WINDOW}")
            appendLine("  mCurrentFocus=Window{9c41e7 u0 Application Not Responding: com.android.systemui}")
        }
        assertBlocked(ScreenEvidencePolicy.focusVerdict(dump, APP_PACKAGE), "disagree")
    }

    @Test
    fun `the same window repeated across a dump is still one window`() {
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
    fun `a titled app popup that names the package is still ours`() {
        val verdict = ScreenEvidencePolicy.focusVerdict(dumpWith("PopupWindow:$APP_PACKAGE"), APP_PACKAGE)
        assertTrue("expected OwnedByApp, got $verdict", verdict is FocusVerdict.OwnedByApp)
    }

    @Test
    fun `a generic Compose popup is ours only with app subwindow ownership evidence`() {
        val verdict = ScreenEvidencePolicy.focusVerdict(ownedSubWindowDump(), APP_PACKAGE)
        assertEquals(FocusVerdict.OwnedByApp("Pop-Up Window"), verdict)
    }

    @Test
    fun `a generic popup with a foreign parent is blocked`() {
        val dump = ownedSubWindowDump().replace(
            "mParentWindow=Window{aa00bb u0 $APP_WINDOW}",
            "mParentWindow=Window{aa00bb u0 com.android.systemui/com.android.systemui.SystemUIService}",
        )
        assertBlocked(ScreenEvidencePolicy.focusVerdict(dump, APP_PACKAGE), "unrecognised")
    }

    @Test
    fun `a generic popup with a foreign window package is blocked`() {
        val dump = ownedSubWindowDump().replace(
            "package=$APP_PACKAGE",
            "package=com.android.systemui",
        )
        assertBlocked(ScreenEvidencePolicy.focusVerdict(dump, APP_PACKAGE), "unrecognised")
    }

    @Test
    fun `a generic popup without application sub panel type is blocked`() {
        val dump = ownedSubWindowDump().replace(
            "type=APPLICATION_SUB_PANEL",
            "type=APPLICATION",
        )
        assertBlocked(ScreenEvidencePolicy.focusVerdict(dump, APP_PACKAGE), "unrecognised")
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

    private fun ownedSubWindowDump(): String = buildString {
        appendLine("WINDOW MANAGER WINDOWS (dumpsys window windows)")
        appendLine("  Window #0 Window{f00baa u0 Pop-Up Window}:")
        appendLine("    package=$APP_PACKAGE")
        appendLine("    mAttrs={(0,0)(wrapxwrap) type=APPLICATION_SUB_PANEL}")
        appendLine("    mParentWindow=Window{aa00bb u0 $APP_WINDOW}")
        appendLine("  Window #1 Window{aa00bb u0 $APP_WINDOW}:")
        appendLine("    package=$APP_PACKAGE")
        appendLine("  mCurrentFocus=Window{f00baa u0 Pop-Up Window}")
        appendLine("  mFocusedApp=ActivityRecord{7d3e2f u0 $APP_WINDOW t42}")
    }

    private companion object {
        const val APP_PACKAGE = "app.webora.browser.debug"
        const val APP_WINDOW = "app.webora.browser.debug/app.webora.browser.MainActivity"
    }
}
