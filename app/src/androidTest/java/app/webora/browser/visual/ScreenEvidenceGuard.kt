package app.webora.browser.visual

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.io.PlatformTestStorage
import app.webora.browser.evidence.CandidateVerdict
import app.webora.browser.evidence.ContentVerdict
import app.webora.browser.evidence.FocusVerdict
import app.webora.browser.evidence.RenderedContentPolicy
import app.webora.browser.evidence.ScreenEvidencePolicy
import app.webora.browser.evidence.candidateVerdict

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

    /**
     * Returns a screenshot whose [region] has actually been drawn.
     *
     * `requireAppOwnsScreen` answers *who* owns the window; this answers *what is drawn in it*, and
     * they are different questions. Every wait in the journey before this point is a semantics
     * assertion — `assertIsDisplayed()` claims a node has bounds, not that anything was painted into
     * them, and `waitForIdle()` waits for Compose, which does not track a `WebView`'s paint. So a
     * capture could fire on a populated semantics tree over an empty page, which is precisely what
     * run 9 published.
     *
     * A `null` [region] means the frame carries no content requirement — Home has no renderer, so
     * there is no page rectangle to measure.
     *
     * Returns **the bitmap that satisfied the check**, never a fresh one: a second `takeScreenshot`
     * could differ from the frame that passed, which would make the check a claim about a picture
     * nobody kept.
     */
    fun captureWhenRendered(label: String, region: Rect?, excluded: List<Rect> = emptyList()): Bitmap {
        if (region == null) return takeScreenshot(label)

        val deadline = SystemClock.uptimeMillis() + RENDER_TIMEOUT_MILLIS
        val started = SystemClock.uptimeMillis()
        val samples = StringBuilder()
        var attempt = 0

        var contested = false

        while (SystemClock.uptimeMillis() < deadline) {
            attempt++
            val bitmap = takeScreenshot(label)
            val content = RenderedContentPolicy.verdict(sampleRegion(bitmap, region, excluded))
            val elapsed = SystemClock.uptimeMillis() - started

            if (content is ContentVerdict.Blank) {
                // No window dump on a blank poll. This loop runs up to forty times, and `CI-004`
                // found the System UI ANR on run 13 was raised by System UI's own dump service — a
                // harness that polls dumps harder may provoke what it is trying to detect. The
                // ownership question is only worth asking about a frame we would otherwise keep.
                samples.appendLine(
                    "attempt=$attempt elapsed=${elapsed}ms differing=${content.differingFraction} " +
                        "modal=#${Integer.toHexString(content.modalColor)} samples=${content.sampleCount}",
                )
                SystemClock.sleep(POLL_INTERVAL_MILLIS)
                continue
            }

            val focus = ScreenEvidencePolicy.focusVerdict(readWindowDump(), appPackage)
            when (val verdict = candidateVerdict(focus, content)) {
                is CandidateVerdict.Accept -> {
                    // Recorded on success too, not only on failure. A passing check that leaves no
                    // measurement cannot be told apart from one that barely passed for the wrong
                    // reason — which is exactly what happened in run 10, where a browser-owned
                    // quick-action button inside the measured region cleared the bar on a blank page.
                    // The owner is recorded for the same reason at the second property: run 14's
                    // file could not say the frame it kept had a system dialog over it.
                    val rendered = content as ContentVerdict.Rendered
                    record(
                        "rendered-$label.txt",
                        renderedReport(label, region, excluded, "PASSED differing=" +
                            "${rendered.differingFraction} after ${elapsed}ms " +
                            "owner=${(focus as FocusVerdict.OwnedByApp).title}\n" + samples),
                    )
                    return bitmap
                }

                is CandidateVerdict.Retry -> {
                    contested = true
                    samples.appendLine(
                        "attempt=$attempt elapsed=${elapsed}ms REJECTED ${verdict.reason}",
                    )
                }
            }
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }

        // Never rendered and rendered-but-contested are different stories, and a reader who cannot
        // tell them apart re-diagnoses this from scratch.
        val summary = if (contested) "RENDERED BUT CONTESTED" else "NEVER RENDERED"
        record("rendered-$label.txt", renderedReport(label, region, excluded, "$summary\n$samples"))
        throw AssertionError(
            "Refusing to capture $label: " +
                if (contested) {
                    "the page region rendered, but Webora never owned the screen at the moment a " +
                        "frame was taken, within ${RENDER_TIMEOUT_MILLIS}ms."
                } else {
                    "the page region never rendered within ${RENDER_TIMEOUT_MILLIS}ms."
                } +
                " Every sample is in $DIAGNOSTICS_DIRECTORY/rendered-$label.txt.",
        )
    }

    private fun renderedReport(label: String, region: Rect, excluded: List<Rect>, samples: String) = buildString {
        appendLine("label=$label")
        appendLine("region=$region")
        appendLine("excluded=$excluded")
        appendLine("stride=$SAMPLE_STRIDE")
        appendLine("minimum_differing_fraction=${RenderedContentPolicy.MINIMUM_DIFFERING_FRACTION}")
        appendLine("timeout_ms=$RENDER_TIMEOUT_MILLIS")
        appendLine()
        append(samples)
    }

    /**
     * A strided read, not a full one. `CI-002` established that work on the runner while the device
     * is alive is what starves `system_server`, and every fourth pixel on both axes is a sixteenth
     * of the region — still tens of thousands of samples, which is far more than the modal colour
     * needs to be stable.
     */
    private fun sampleRegion(bitmap: Bitmap, region: Rect, excluded: List<Rect>): IntArray {
        val left = region.left.coerceIn(0, bitmap.width)
        val top = region.top.coerceIn(0, bitmap.height)
        val right = region.right.coerceIn(left, bitmap.width)
        val bottom = region.bottom.coerceIn(top, bitmap.height)

        val samples = ArrayList<Int>()
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                // Browser-owned overlays live inside this rectangle — the quick-action button is a
                // child of the very Box that bounds the page — so a sample landing on one is chrome,
                // not page. Counting it lets a frame pass on the strength of Webora's own UI.
                if (excluded.none { it.contains(x, y) }) samples.add(bitmap.getPixel(x, y))
                x += SAMPLE_STRIDE
            }
            y += SAMPLE_STRIDE
        }
        return samples.toIntArray()
    }

    private fun takeScreenshot(label: String): Bitmap =
        requireNotNull(uiAutomation.takeScreenshot()) { "UiAutomation returned no screenshot for $label" }

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
        const val RENDER_TIMEOUT_MILLIS = 20_000L
        const val SAMPLE_STRIDE = 4

        /**
         * `AppNotRespondingDialog` is an `AlertDialog`, so `Wait` is its negative button; the crash
         * dialog uses the `aerr_*` layout instead. Both are checked because neither id is public
         * API, and finding anything other than exactly one match is a failure rather than a guess.
         */
        val WAIT_VIEW_IDS = listOf("android:id/button2", "android:id/aerr_wait")
    }
}
