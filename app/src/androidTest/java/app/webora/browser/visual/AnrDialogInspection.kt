package app.webora.browser.visual

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.webora.browser.evidence.AnrDismissalPolicy
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
 * twice: by [AnrDismissalPolicy.mayBeSearchedForWaitAffordance], which owns the package allow-list
 * and carries the negative controls, and by the input focus that [ScreenEvidencePolicy]'s
 * classification came from. Webora's windows carry the app's package and can never pass the first
 * check, whichever identity rule fires.
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

    /**
     * Observes the focused system dialog; the caller's policy turns this into a decision.
     *
     * Takes no title, and that is deliberate. `CI-004`'s plan identified the dialog by matching the
     * window title `focusVerdict` had classified, and hosted run 21 recorded what
     * `AccessibilityWindowInfo.getTitle()` actually returns for it: `System UI isn't responding` —
     * the translated, user-facing string, not `Application Not Responding: com.android.systemui`. So
     * the rule matched nothing, and it could only have been repaired by comparing against a
     * localised string, which is exactly what `CI-002` refused when it keyed the classification on
     * process-derived titles. No OS-authored text reaches this path now.
     */
    fun inspect(): AnrDialogObservation {
        val flags = currentFlags()
        val visible = uiAutomation.windows.orEmpty()
        val searched = searchableWindows(visible)
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
            // A thunk, not a list. The fix that made a stuck dialog patient turned one look into
            // ~40, and only the last observation's ids are ever printed — so an eager walk here is
            // 400 accessibility reads per poll to produce a diagnostic read once, on a device that
            // is by construction already not responding.
            walkPresentViewIds = { presentViewIds(searched) },
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
     * **Both rules are filtered through [AnrDismissalPolicy.mayBeSearchedForWaitAffordance] first, so
     * neither can widen what may be pressed.** They differ only in how the system-owned dialog is
     * recognised, both read structural facts rather than text, and each records which one fired:
     *
     * 1. `focused` — the window holding input focus, which is the same notion as `mCurrentFocus` in
     *    the dump that produced the classification. Not a guess: this branch runs only because that
     *    dump already said the focused window is the System UI ANR dialog. Hosted run 21 shows this
     *    is the rule that finds it.
     * 2. `active-window` — `rootInActiveWindow`, for a device where the interactive-windows flag
     *    never took and the first rule therefore has nothing to enumerate.
     *
     * There was a third, first, matching the classified window title, and run 21 deleted it: the
     * accessibility title is `System UI isn't responding`, a translated user-facing string, so it
     * never matched and could only have been repaired into a locale-dependent identification rule.
     *
     * One root per window, hoisted out of the per-id loop: the old code re-read `rootInActiveWindow`
     * once per view id, so two lookups could see different trees. De-duplication by window id matters
     * for the same reason in the other direction — one window supplying the same node twice would
     * manufacture an `Ambiguous` and fail a run that should have pressed.
     */
    private fun searchableWindows(visible: List<AccessibilityWindowInfo>): List<SearchedWindow> {
        val systemOwned = visible.mapNotNull { window ->
            window.root?.takeIf { AnrDismissalPolicy.mayBeSearchedForWaitAffordance(it.packageName?.toString()) }
                ?.let { window to it }
        }
        return rootsOf(systemOwned.filter { (window, _) -> window.isFocused }, "focused")
            .ifEmpty { activeWindow() }
    }

    private fun rootsOf(
        windows: List<Pair<AccessibilityWindowInfo, AccessibilityNodeInfo>>,
        rule: String,
    ): List<SearchedWindow> {
        val byWindowId = LinkedHashMap<Int, SearchedWindow>()
        windows.forEach { (window, root) ->
            byWindowId[root.windowId] = SearchedWindow(root, "${describe(window)} matched=$rule")
        }
        return byWindowId.values.toList()
    }

    private fun activeWindow(): List<SearchedWindow> {
        val active = uiAutomation.rootInActiveWindow ?: return emptyList()
        if (!AnrDismissalPolicy.mayBeSearchedForWaitAffordance(active.packageName?.toString())) return emptyList()
        return listOf(
            SearchedWindow(active, "id=${active.windowId} pkg=${active.packageName} matched=active-window"),
        )
    }

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

/**
 * One look at the dialog: what the policy decides on, plus everything a reader would want after.
 *
 * [presentViewIds] is deferred until something reads it. The roots may be a poll stale by then; a
 * stale node yields fewer ids, which degrades a diagnostic and cannot change a decision.
 */
internal class AnrDialogObservation(
    val affordances: WaitAffordances,
    val pressableNodes: List<Pair<String, AccessibilityNodeInfo>>,
    val searchedWindows: List<String>,
    val visibleWindows: List<String>,
    val flags: AutomationFlags,
    walkPresentViewIds: () -> List<String>,
) {
    val presentViewIds: List<String> by lazy(LazyThreadSafetyMode.NONE, walkPresentViewIds)
}

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
    private var viewIds = NOT_GATHERED

    val hasSamples: Boolean get() = polls > 0

    fun record(title: String, decision: DismissalVerdict, observation: AnrDialogObservation) {
        polls++
        last = observation
        window = title
        // Gathered here, not at report time, and only when the dialog was not cleared. `TASK-FIX-1`
        // made the walk lazy to keep it off the per-poll path; runs 21 and 22 showed the other end of
        // that, recording `view_ids_present=[]` on every pressed frame because the dialog was gone by
        // the time the record was written. **An empty list and "never gathered" must not look alike**
        // — `DEVX-002` learned that from `tiles=0` — so the success path says so in words, and the
        // walk only runs on the refusal path it exists for, while the tree is still on screen.
        viewIds = if (decision is DismissalVerdict.Press) NOT_GATHERED else observation.presentViewIds.toString()
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
        val observation = last ?: return@buildString
        appendLine()
        appendLine("--- last observation ---")
        appendLine("searched_windows=${observation.searchedWindows}")
        appendLine("visible_windows=${observation.visibleWindows}")
        appendLine("view_ids_present=$viewIds")
    }

    private fun reasonOf(decision: DismissalVerdict): String = when (decision) {
        is DismissalVerdict.Press -> "pressed ${decision.viewId}"
        is DismissalVerdict.NotYet -> decision.reason
        is DismissalVerdict.Ambiguous -> "ambiguous: ${decision.viewIds.joinToString()}"
    }

    private companion object {
        const val NOT_GATHERED = "not gathered (the dialog was cleared)"
    }
}

