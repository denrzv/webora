package app.webora.browser.evidence

/**
 * What the guard saw when it looked for the one sanctioned affordance.
 *
 * Ids and booleans, never `AccessibilityNodeInfo`. `isClickable` and `isVisibleToUser` are
 * *observations* and stay in the guard; an Android type in this signature would drag the decision
 * into a source set `./gradlew test` cannot compile, which is the shape `CI-002` and `CI-003` both
 * refused — and the shape that let this defect reach `main`.
 *
 * @param pressable sanctioned view ids found clickable and visible, one entry per matching node, so
 *   two nodes sharing an id read as two candidates rather than one.
 * @param matchedButNotPressable sanctioned view ids present on the tree but not yet both clickable
 *   and visible.
 * @param rootAvailable whether there was any accessibility root to search at all.
 * @param viewIdReportingEnabled whether `FLAG_REPORT_VIEW_IDS` is in effect. Without it
 *   `findAccessibilityNodeInfosByViewId` returns nothing, so a zero result proves nothing.
 */
data class WaitAffordances(
    val pressable: List<String>,
    val matchedButNotPressable: List<String>,
    val rootAvailable: Boolean,
    val viewIdReportingEnabled: Boolean,
)

/**
 * Whether the harness may press the one affordance it is permitted to press.
 *
 * Three cases, mirroring [FocusVerdict], and deliberately no fourth: there is no `else` that
 * presses, so widening what may be cleared is a visible edit rather than an emergent behaviour.
 */
sealed interface DismissalVerdict {

    /** Exactly one sanctioned affordance was pressable. Press it. */
    data class Press(val viewId: String) : DismissalVerdict

    /** Nothing pressable *yet*. The caller's existing deadline decides when to stop asking. */
    data class NotYet(val reason: String) : DismissalVerdict

    /** Two or more candidates. Fails the run immediately and is never resolved by choosing. */
    data class Ambiguous(val viewIds: List<String>) : DismissalVerdict
}

/**
 * Decides whether an observation justifies pressing `Wait`.
 *
 * **This is the third capture-time decision, and until `CI-004` it was the only one that was not a
 * pure function.** `focusVerdict` answers *what is the focused window*, [RenderedContentPolicy]
 * answers *is the page drawn*, and this answers *do these candidates justify a press* — the same
 * kind of question, a total classification of an observation that fails closed on ambiguity. It
 * lived as `if (candidates.size != 1) throw` inside the emulator-only guard, where `./gradlew test`
 * never compiled it and a checkout with no `/dev/kvm` never ran it.
 *
 * What it cost: hosted run 13 failed twice, byte-identically, with
 * `0 Wait affordances were found among android:id/button2, android:id/aerr_wait` and captured no
 * frames at all. Three conditions produce that zero — the tree was not searchable, the root was
 * null, or view-id reporting was not in effect — and none of them means the dialog has no Wait
 * button. The guard's own KDoc had already named the third as *"a recoverable run failing as an
 * unrecoverable one"*.
 *
 * | condition | verdict | why |
 * |---|---|---|
 * | exactly one pressable | [Press][DismissalVerdict.Press] | the sanctioned affordance |
 * | two or more pressable | [Ambiguous][DismissalVerdict.Ambiguous] | never guessed at |
 * | zero, no root | [NotYet][DismissalVerdict.NotYet] | an observation gap, not an absence |
 * | zero, no view-id reporting | [NotYet][DismissalVerdict.NotYet] | the lookup cannot report ids |
 * | zero, matched but unpressable | [NotYet][DismissalVerdict.NotYet] | the dialog is assembling |
 * | zero, nothing matched | [NotYet][DismissalVerdict.NotYet] | may still be arriving |
 *
 * **Only the zero row changed.** Making ambiguity retryable would be a worse bug than the one being
 * fixed, which is why [DismissalVerdict.Ambiguous] is evaluated before any "not yet" path can be
 * reached and carries its own negative control.
 *
 * Every zero carries a *different* reason, because the caller's timeout message is whatever reason
 * it stored last: forty identical strings are what made run 13 cost two runs and a log download to
 * understand.
 *
 * Patience is not the fix, and this policy does not claim to be one. Research §10 established that
 * run 13's dialog had been on screen for 63 seconds before the test process started, so that zero
 * was deterministic — reachability is the mechanism, and [WaitAffordances] is what the guard has to
 * go and observe correctly. This decides what to do with the observation, and it may only ever make
 * the harness refuse *later*, never make it press something it would refuse today.
 */
object AnrDismissalPolicy {

    fun verdict(found: WaitAffordances): DismissalVerdict = when {
        found.pressable.size == 1 -> DismissalVerdict.Press(found.pressable.single())

        found.pressable.size > 1 -> DismissalVerdict.Ambiguous(found.pressable)

        !found.rootAvailable ->
            DismissalVerdict.NotYet("no accessibility root was available to search")

        !found.viewIdReportingEnabled ->
            DismissalVerdict.NotYet("view-id reporting is not in effect, so a zero result proves nothing")

        found.matchedButNotPressable.isNotEmpty() -> DismissalVerdict.NotYet(
            "present but not yet clickable and visible: ${found.matchedButNotPressable.joinToString()}",
        )

        else -> DismissalVerdict.NotYet("no sanctioned affordance is on the searched tree yet")
    }
}
