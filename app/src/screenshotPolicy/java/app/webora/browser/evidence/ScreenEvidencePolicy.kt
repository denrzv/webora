package app.webora.browser.evidence

/**
 * What the screenshot harness found in front of Webora, and what it is allowed to do about it.
 *
 * There are exactly three outcomes and no fourth. In particular there is no "unknown, carry on":
 * a screenshot of a screen nobody could identify is not evidence.
 */
sealed interface FocusVerdict {
    /** Webora owns the focused window. This is the only verdict that permits a capture. */
    data class OwnedByApp(val title: String) : FocusVerdict

    /** The one system-owned dialog the harness may clear, and only by pressing Wait. */
    data class DismissableSystemAnr(val processName: String, val title: String) : FocusVerdict

    /** Anything else, including anything owned by Webora. Fails the run, carrying what was seen. */
    data class Blocked(val reason: String) : FocusVerdict
}

/**
 * Decides whether the device's focused window may be photographed, cleared, or must fail the run.
 *
 * **This is the security-relevant half of `CI-002`, and the reason it is a separate pure object.**
 * The tempting version of this code is "if a dialog is in the way, dismiss it and take the shot",
 * which would also clear a Webora crash dialog, a Webora ANR, a permission prompt or a TLS warning —
 * and would do it silently, leaving a green job and a screenshot of whatever was behind the failure.
 * The whole point of a screenshot is to show what a person would see, so the harness may remove
 * exactly one known system-owned obstruction and must fail on every other one.
 *
 * That is an allow-list of one process, in the shape `PROJECT_RULES.md` already requires of manifest
 * handling: unknown input is refused, never guessed at. There is deliberately no `else` branch that
 * dismisses, and [DISMISSABLE_ANR_PROCESS] is a single constant so that widening it is a visible
 * edit that `ScreenEvidencePolicyTest` fails on.
 *
 * The input is OS-supplied. AOSP builds these window titles from the *process name* —
 * `AppNotRespondingDialog` sets `"Application Not Responding: " + processName`, `AppErrorDialog` sets
 * `"Application Error: " + processName` — so the decision reads no translated text and no page,
 * dialog or manifest content. A website has no path into it.
 *
 * The file lives in `src/screenshotPolicy/java`, shared by the `test` and `androidTest` source sets
 * and by neither `main` nor any variant. `androidTest` alone would put the decision where
 * `./gradlew test` cannot see it, and this repository's managed checkouts have no `/dev/kvm` — the
 * test would exist and never have run. `main` would ship test-harness policy inside the browser.
 */
object ScreenEvidencePolicy {

    /**
     * The only process whose error dialog may be cleared, and only in its not-responding form.
     *
     * A System UI *crash* is not on the list. "The known dialog" is one shape, and a crashed System
     * UI is a device that should not be producing product evidence at all.
     */
    const val DISMISSABLE_ANR_PROCESS = "com.android.systemui"

    private const val ANR_TITLE_PREFIX = "Application Not Responding: "
    private const val CRASH_TITLE_PREFIX = "Application Error: "

    /** `mCurrentFocus=Window{<hash> <user> <title>}`, or the literal `mCurrentFocus=null`. */
    private val CURRENT_FOCUS = Regex("""mCurrentFocus=(?:(null)|Window\{\S+ \S+ ([^}]*)\})""")

    /**
     * Classifies the focused window in the output of `adb shell dumpsys window`.
     *
     * Fails closed on every ambiguity: no `mCurrentFocus` line, a line this parser cannot read, a
     * null focus, or two focused windows that disagree. A parser that silently matched nothing would
     * turn the guard into a no-op while still reporting success, which is the one failure mode a
     * capture-time check cannot afford.
     */
    fun focusVerdict(dumpsysWindowOutput: String, appPackage: String): FocusVerdict {
        val matches = CURRENT_FOCUS.findAll(dumpsysWindowOutput).toList()
        if (matches.isEmpty()) {
            return FocusVerdict.Blocked("no mCurrentFocus line in the dumpsys window output")
        }

        val titles = matches.map { match ->
            match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2].trim()
        }.distinct()

        if (titles.size > 1) {
            return FocusVerdict.Blocked("two focused windows disagree: ${titles.joinToString(" | ")}")
        }
        return classify(titles.first(), appPackage)
    }

    private fun classify(title: String, appPackage: String): FocusVerdict = when {
        title == "null" -> FocusVerdict.Blocked("mCurrentFocus=null; nothing owns the display")

        title.isEmpty() -> FocusVerdict.Blocked("the focused window has no title")

        title.startsWith(ANR_TITLE_PREFIX) -> anrVerdict(title)

        title.startsWith(CRASH_TITLE_PREFIX) ->
            FocusVerdict.Blocked("a process has crashed and its dialog owns the screen: $title")

        // Checked after the error-dialog prefixes, never before: an error dialog for Webora carries
        // Webora's process name in its own title, so ownership by name is only meaningful once the
        // system's dialogs have already been ruled out.
        containsPackageToken(title, appPackage) -> FocusVerdict.OwnedByApp(title)

        else -> FocusVerdict.Blocked("an unrecognised window owns the screen: $title")
    }

    /**
     * Whether [title] names [appPackage] as a whole package token.
     *
     * Not `startsWith("$appPackage/")`: a dialog, popup or presentation window is not guaranteed to
     * be titled `package/activity`, and a capture guard that failed on the consent dialog would
     * break the journey it exists to protect. Not a bare `contains` either — that would accept
     * `com.evil.app.webora.browser.debug` and `app.webora.browser.debugger` as Webora. The package
     * has to appear with a non-package character (or nothing) on each side.
     */
    private fun containsPackageToken(title: String, appPackage: String): Boolean {
        val boundary = "[A-Za-z0-9._]"
        return Regex("(?<!$boundary)${Regex.escape(appPackage)}(?!$boundary)").containsMatchIn(title)
    }

    private fun anrVerdict(title: String): FocusVerdict {
        val processName = title.removePrefix(ANR_TITLE_PREFIX).trim()
        return if (processName == DISMISSABLE_ANR_PROCESS) {
            FocusVerdict.DismissableSystemAnr(processName, title)
        } else {
            FocusVerdict.Blocked("$processName is not responding, and only System UI may be waited on")
        }
    }
}
