package app.webora.browser.visual

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.io.PlatformTestStorage
import app.webora.browser.evidence.FocusVerdict
import app.webora.browser.evidence.ScreenEvidencePolicy

/**
 * Refuses to photograph a screen Webora does not own.
 *
 * `waitForIdle()` says the Compose tree has settled; it says nothing about a system dialog
 * composited above the activity. The first green screenshot run passed every semantic assertion and
 * returned three frames covered by `System UI isn't responding`, which is the failure this class
 * exists to make impossible.
 *
 * It reads the device — the same `dumpsys window` output `scripts/android-emulator-ready.sh` samples
 * — and hands it to [ScreenEvidencePolicy], which owns the decision and carries the negative
 * controls. Everything here is interaction: reading the dump, pressing one button, recording what it
 * did. Keeping the two apart is what lets a checkout with no `/dev/kvm` still prove that a Webora
 * ANR can never be dismissed.
 *
 * The bounded poll exists because a dialog dismissal is asynchronous: for a moment after `Wait` the
 * focused window can legitimately be nothing at all. So a blocking verdict is retried until the
 * deadline and only then fails — with the last reason and the dump that produced it.
 */
class ScreenEvidenceGuard(
    private val uiAutomation: UiAutomation,
    private val appPackage: String,
    private val storage: PlatformTestStorage,
) {

    init {
        enableViewIdReporting()
    }

    /**
     * Returns only when Webora owns the focused window.
     *
     * Clears at most [MAX_DISMISSALS] instances of the one known System UI ANR dialog, recording
     * each. Every other obstruction — including one owned by Webora — throws.
     */
    fun requireAppOwnsScreen(label: String) {
        val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MILLIS
        var dismissals = 0
        var lastDump = ""
        var lastReason = "the screen was never inspected"

        while (SystemClock.uptimeMillis() < deadline) {
            lastDump = readWindowDump()
            when (val verdict = ScreenEvidencePolicy.focusVerdict(lastDump, appPackage)) {
                is FocusVerdict.OwnedByApp -> {
                    recordFocus(label, lastDump)
                    return
                }

                is FocusVerdict.Blocked -> lastReason = verdict.reason

                is FocusVerdict.DismissableSystemAnr -> if (dismissals < MAX_DISMISSALS) {
                    dismissals++
                    dismissSystemAnr(verdict, label, dismissals)
                } else {
                    lastReason = "${verdict.title} survived $MAX_DISMISSALS dismissals"
                }
            }
            uiAutomation.waitForIdle(IDLE_QUIET_MILLIS, IDLE_TIMEOUT_MILLIS)
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }

        throw evidenceFailure(label, lastDump, lastReason)
    }

    private fun dismissSystemAnr(verdict: FocusVerdict.DismissableSystemAnr, label: String, attempt: Int) {
        val candidates = WAIT_VIEW_IDS.flatMap { viewId ->
            val root = uiAutomation.rootInActiveWindow ?: return@flatMap emptyList()
            root.findAccessibilityNodeInfosByViewId(viewId)
                .filter { it.isClickable && it.isVisibleToUser }
                .map { viewId to it }
        }

        // Fail closed rather than guess. The button ids are not public API, so "press whatever looks
        // like Wait" is not a thing this may do — a wrong press on an error dialog can close an app.
        if (candidates.size != 1) {
            throw evidenceFailure(
                label,
                readWindowDump(),
                "${verdict.title} is dismissable but ${candidates.size} Wait affordances were found " +
                    "among ${WAIT_VIEW_IDS.joinToString()}",
            )
        }

        val (viewId, node) = candidates.single()
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        record(
            "interference-$label-$attempt.txt",
            buildString {
                appendLine("window=${verdict.title}")
                appendLine("process=${verdict.processName}")
                appendLine("pressed=$viewId")
                appendLine("label=${node.text}")
                appendLine("click_accepted=$clicked")
            },
        )
        if (!clicked) {
            throw evidenceFailure(label, readWindowDump(), "the Wait button at $viewId refused the click")
        }
    }

    /**
     * `findAccessibilityNodeInfosByViewId` returns nothing at all without this flag, which would be
     * indistinguishable from a dialog with no Wait button — a recoverable run failing as an
     * unrecoverable one.
     */
    private fun enableViewIdReporting() {
        val info = uiAutomation.serviceInfo ?: return
        if (info.flags and AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS != 0) return
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        uiAutomation.serviceInfo = info
    }

    private fun readWindowDump(): String =
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(WINDOW_DUMP_COMMAND))
            .use { stream -> stream.readBytes().toString(Charsets.UTF_8) }

    /**
     * The focus lines behind a successful capture, so the recorded fixtures in
     * `ScreenEvidencePolicyTest` can be reconciled against what a real API 33 device prints. An
     * excerpt, not a second classification — the whole dump is hundreds of kilobytes and only these
     * lines decided anything.
     */
    private fun recordFocus(label: String, dump: String) {
        val lines = dump.lineSequence().filter { it.contains("mCurrentFocus") }.joinToString("\n")
        record("focus-$label.txt", lines)
    }

    private fun evidenceFailure(label: String, dump: String, reason: String): AssertionError {
        record("window-$label.txt", dump)
        return AssertionError(
            "Refusing to capture $label: $reason. The full dumpsys window output is in " +
                "$DIAGNOSTICS_DIRECTORY/window-$label.txt.",
        )
    }

    private fun record(name: String, content: String) {
        storage.openOutputFile("$DIAGNOSTICS_DIRECTORY/$name").use { output ->
            output.write(content.toByteArray())
        }
    }

    private companion object {
        const val WINDOW_DUMP_COMMAND = "dumpsys window"
        const val DIAGNOSTICS_DIRECTORY = "diagnostics"
        const val MAX_DISMISSALS = 2
        const val SETTLE_TIMEOUT_MILLIS = 20_000L
        const val POLL_INTERVAL_MILLIS = 500L
        const val IDLE_QUIET_MILLIS = 500L
        const val IDLE_TIMEOUT_MILLIS = 5_000L

        /**
         * `AppNotRespondingDialog` is an `AlertDialog`, so `Wait` is its negative button; the crash
         * dialog uses the `aerr_*` layout instead. Both are checked because neither id is public
         * API, and finding anything other than exactly one match is a failure rather than a guess.
         */
        val WAIT_VIEW_IDS = listOf("android:id/button2", "android:id/aerr_wait")
    }
}
