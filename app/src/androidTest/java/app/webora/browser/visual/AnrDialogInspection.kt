package app.webora.browser.visual

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.webora.browser.evidence.DismissalVerdict
import app.webora.browser.evidence.ScreenEvidencePolicy
import app.webora.browser.evidence.WaitAffordances

/**
 * Looks for the one sanctioned affordance on the one dialog the harness may clear.
 *
 * **The lookup used to be one call to `rootInActiveWindow`, and hosted run 13 says that is not where
 * the dialog was.** WindowManager logged `Cannot find window which accessibility connection is added
 * to` half a second before the refusal, the dialog had been on screen for 63 seconds, and the same
 * code cleared the same dialog in run 14. The zero was reachability, not assembly, so the fix is to
 * enumerate the windows the automation can see and search the one the policy already classified.
 *
 * **Widening the search must not widen the allow-list, and this is the load-bearing constraint.**
 * `android:id/button2` is the negative button of *every* `AlertDialog`, Webora's consent dialog
 * included. A search across all windows that fell back to "whatever has a button2" would press a
 * Webora dialog whenever the system dialog's tree was unreachable — the exact thing `CI-002` refused,
 * arriving through a mechanism `CI-002` did not have. Every searched window is therefore identified
 * twice: by an OS-supplied package in `SYSTEM_PACKAGES`, and by an OS-supplied identity — the window
 * title [ScreenEvidencePolicy] already classified, or the input focus that classification came from.
 * Webora's windows carry the app's package and can never pass the first check, whichever identity
 * rule fires.
 *
 * The inspector only observes. [app.webora.browser.evidence.AnrDismissalPolicy] decides, in a source
 * set `./gradlew test` compiles, for the reason this ticket exists.
 */
internal class AnrDialogInspector(private val uiAutomation: UiAutomation) {

    /**
     * Asks for the two flags the lookup depends on.
     *
     * Without `FLAG_REPORT_VIEW_IDS`, `findAccessibilityNodeInfosByViewId` returns nothing at all;
     * without `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, [UiAutomation.getWindows] is empty and there is
     * nothing to enumerate. Neither failure aborts construction — assigning `serviceInfo` reconnects
     * the accessibility service, the framework may already have set a flag, and failing early would
     * turn a possibly-fine run red on a condition that can be reported instead. What is *not*
     * tolerated is silence: [inspect] re-reads both flags on every observation, so a null
     * `serviceInfo` or a reconnect that dropped one turns a zero into `NotYet` and says so in the
     * record, rather than being reported as a dialog with no Wait button.
     */
    fun enableRequiredFlags() {
        val info = uiAutomation.serviceInfo ?: return
        val wanted = info.flags or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        if (wanted == info.flags) return
        info.flags = wanted
        uiAutomation.serviceInfo = info
    }

    /** Observes the dialog titled [dialogTitle]; the caller's policy turns this into a decision. */
    fun inspect(dialogTitle: String): AnrDialogObservation {
        val flags = currentFlags()
        val visible = uiAutomation.windows.orEmpty()
        val searched = searchableWindows(visible, dialogTitle)
        val (pressable, unpressable) = affordanceNodes(searched)
            .partition { (_, node) -> node.isClickable && node.isVisibleToUser }

        return AnrDialogObservation(
            affordances = WaitAffordances(
                pressable = pressable.map { it.first },
                matchedButNotPressable = unpressable.map { it.first },
                rootAvailable = searched.isNotEmpty(),
                viewIdReportingEnabled = flags.viewIdReporting,
            ),
            pressableNodes = pressable,
            searchedWindows = searched.map { it.description },
            visibleWindows = visible.map { describe(it) },
            presentViewIds = presentViewIds(searched),
            flags = flags,
        )
    }

    private fun currentFlags(): AutomationFlags {
        val flags = uiAutomation.serviceInfo?.flags
        return AutomationFlags(
            serviceInfoAvailable = flags != null,
            viewIdReporting = flags != null && flags and AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS != 0,
            interactiveWindows = flags != null &&
                flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS != 0,
        )
    }

    /**
     * The windows this observation is allowed to search, by the first rule that identifies one.
     *
     * **Every rule is filtered through [isSystemOwned] first, so none of them can widen what may be
     * pressed.** They differ only in how the system-owned dialog is recognised, and each records
     * which one fired so the next reader knows what the device actually supplied:
     *
     * 1. `title` — the OS-supplied window title equals the one [ScreenEvidencePolicy] classified.
     * 2. `focused` — the window holding input focus, which is the same notion as `mCurrentFocus` in
     *    the dump that produced that classification. Not a guess: this branch runs only because that
     *    dump already said the focused window is the System UI ANR dialog.
     * 3. `active-window` — `rootInActiveWindow`, for a device where the interactive-windows flag
     *    never took and the first two rules therefore have nothing to enumerate.
     *
     * One root per window, hoisted out of the per-id loop: the old code re-read `rootInActiveWindow`
     * once per view id, so two lookups could see different trees. De-duplication by window id matters
     * for the same reason in the other direction — one window supplying the same node twice would
     * manufacture an `Ambiguous` and fail a run that should have pressed.
     */
    private fun searchableWindows(
        visible: List<AccessibilityWindowInfo>,
        dialogTitle: String,
    ): List<SearchedWindow> {
        val systemOwned = visible.filter { window -> window.root?.let(::isSystemOwned) == true }
        return rootsOf(systemOwned.filter { it.title?.toString() == dialogTitle }, "title")
            .ifEmpty { rootsOf(systemOwned.filter { it.isFocused }, "focused") }
            .ifEmpty { activeWindow() }
    }

    private fun rootsOf(windows: List<AccessibilityWindowInfo>, rule: String): List<SearchedWindow> {
        val byWindowId = LinkedHashMap<Int, SearchedWindow>()
        windows.forEach { window ->
            val root = window.root ?: return@forEach
            byWindowId[root.windowId] = SearchedWindow(root, "${describe(window)} matched=$rule")
        }
        return byWindowId.values.toList()
    }

    private fun activeWindow(): List<SearchedWindow> {
        val active = uiAutomation.rootInActiveWindow ?: return emptyList()
        if (!isSystemOwned(active)) return emptyList()
        return listOf(
            SearchedWindow(active, "id=${active.windowId} pkg=${active.packageName} matched=active-window"),
        )
    }

    /**
     * Whether a window is owned by the OS rather than by Webora.
     *
     * A closed list of two OS-supplied names, not a heuristic. The AOSP dialog is built by
     * `system_server` (`package=android` in `dumpsys window`, `mOwnerUid=1000`); the System UI
     * package is accepted alongside it because it is the process
     * [ScreenEvidencePolicy.focusVerdict] has already allow-listed, and a package mismatch that made
     * this check reject everything would turn the fix into a silent no-op.
     */
    private fun isSystemOwned(root: AccessibilityNodeInfo): Boolean =
        root.packageName?.toString() in SYSTEM_PACKAGES

    private fun affordanceNodes(searched: List<SearchedWindow>): List<Pair<String, AccessibilityNodeInfo>> =
        searched.flatMap { window ->
            WAIT_VIEW_IDS.flatMap { viewId ->
                window.root.findAccessibilityNodeInfosByViewId(viewId).map { viewId to it }
            }
        }

    /**
     * A bounded sample of the view ids actually on the searched tree.
     *
     * This is the diagnostic that did not exist. Run 13's message named the ids the harness *looked
     * for* and never the ids that were *there*, which is why two hosted runs and a log download
     * produced no more information than the first assertion did. Breadth-first with a node budget,
     * because a walk of an arbitrary tree on a device that is already not responding is not a cost
     * worth paying for a diagnostic.
     */
    private fun presentViewIds(searched: List<SearchedWindow>): List<String> {
        val ids = LinkedHashSet<String>()
        val queue = ArrayDeque(searched.map { it.root })
        var budget = MAX_NODES_WALKED
        while (queue.isNotEmpty() && budget > 0 && ids.size < MAX_IDS_RECORDED) {
            budget--
            val node = queue.removeFirst()
            node.viewIdResourceName?.let { ids += it }
            repeat(node.childCount) { index -> node.getChild(index)?.let { queue += it } }
        }
        return ids.toList()
    }

    private fun describe(window: AccessibilityWindowInfo): String =
        "id=${window.id} type=${window.type} title=${window.title} " +
            "active=${window.isActive} focused=${window.isFocused}"

    private class SearchedWindow(val root: AccessibilityNodeInfo, val description: String)

    companion object {
        /**
         * `AppNotRespondingDialog` is an `AlertDialog`, so `Wait` is its negative button; the crash
         * dialog uses the `aerr_*` layout instead. Both are checked because neither id is public
         * API, and finding anything other than exactly one match is a failure or a retry rather than
         * a guess.
         */
        val WAIT_VIEW_IDS = listOf("android:id/button2", "android:id/aerr_wait")

        private val SYSTEM_PACKAGES = setOf("android", ScreenEvidencePolicy.DISMISSABLE_ANR_PROCESS)
        private const val MAX_NODES_WALKED = 400
        private const val MAX_IDS_RECORDED = 60
    }
}

/** Which of the two lookup flags were in effect at the moment of an observation. */
internal data class AutomationFlags(
    val serviceInfoAvailable: Boolean,
    val viewIdReporting: Boolean,
    val interactiveWindows: Boolean,
)

/** One look at the dialog: what the policy decides on, plus everything a reader would want after. */
internal class AnrDialogObservation(
    val affordances: WaitAffordances,
    val pressableNodes: List<Pair<String, AccessibilityNodeInfo>>,
    val searchedWindows: List<String>,
    val visibleWindows: List<String>,
    val presentViewIds: List<String>,
    val flags: AutomationFlags,
)

/**
 * What the guard saw across one `requireAppOwnsScreen` call, written out as `dismissal-<label>.txt`.
 *
 * One compact line per poll, so a reader can tell "the same condition for forty polls" from "it
 * changed and then stopped changing", plus the full window detail for the last observation. Written
 * **once**, when the guard leaves the dismissable state or fails: forty small writes on a device the
 * harness is supposed to be leaving alone is the kind of self-inflicted load `CI-005` ruled out when
 * it stopped the capture loop dumping windows on every blank poll.
 *
 * The journal is created per call rather than held by the guard, so nothing carries over between
 * frames.
 */
internal class DismissalJournal {

    private val samples = StringBuilder()
    private var polls = 0
    private var last: AnrDialogObservation? = null
    private var window = "none"

    val hasSamples: Boolean get() = polls > 0

    fun record(title: String, decision: DismissalVerdict, observation: AnrDialogObservation) {
        polls++
        last = observation
        window = title
        val found = observation.affordances
        samples.appendLine(
            "poll=$polls verdict=${decision::class.simpleName} reason=${reasonOf(decision)} " +
                "pressable=${found.pressable} matched_not_pressable=${found.matchedButNotPressable} " +
                "root_available=${found.rootAvailable} view_id_reporting=${found.viewIdReportingEnabled} " +
                "interactive_windows=${observation.flags.interactiveWindows} " +
                "service_info=${observation.flags.serviceInfoAvailable} " +
                "searched=${observation.searchedWindows.size} visible=${observation.visibleWindows.size}",
        )
    }

    fun report(label: String): String = buildString {
        appendLine("label=$label")
        appendLine("window=$window")
        appendLine("polls=$polls")
        appendLine()
        append(samples)
        appendLine()
        appendLine("--- last observation ---")
        val observation = last ?: return@buildString
        appendLine("searched_windows=${observation.searchedWindows}")
        appendLine("visible_windows=${observation.visibleWindows}")
        appendLine("view_ids_present=${observation.presentViewIds}")
    }

    private fun reasonOf(decision: DismissalVerdict): String = when (decision) {
        is DismissalVerdict.Press -> "pressed ${decision.viewId}"
        is DismissalVerdict.NotYet -> decision.reason
        is DismissalVerdict.Ambiguous -> "ambiguous: ${decision.viewIds.joinToString()}"
    }
}

