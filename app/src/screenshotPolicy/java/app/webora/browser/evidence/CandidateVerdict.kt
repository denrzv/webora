package app.webora.browser.evidence

/**
 * Whether the bitmap just taken may be kept as evidence.
 *
 * Two cases, not three. The capture loop already has a deadline, and that deadline owns the decision
 * to give up; a `Reject` here would be a second place to end the run and the two could disagree.
 */
sealed interface CandidateVerdict {

    /** Webora owned the screen and the page had rendered, at the moment this frame was taken. */
    data object Accept : CandidateVerdict

    /** Not this frame. The caller polls again until its deadline, which this never extends. */
    data class Retry(val reason: String) : CandidateVerdict
}

/**
 * Decides whether a captured frame is evidence, from what was true when it was captured.
 *
 * **This exists because `CI-003` validated one property and saved a frame chosen by the other.**
 * `requireAppOwnsScreen` established ownership before polling began; `captureWhenRendered` then
 * re-screenshotted for up to twenty seconds and returned whichever bitmap satisfied the *content*
 * check. On hosted run 14 a System UI ANR dialog arrived during those seconds, and the run went
 * green with the dialog in the frame — the exact failure `CI-002` was built to make impossible.
 *
 * Worse than a contaminated picture, the obstruction *was* the measurement. The page region measured
 * `differing=0.0023` against a white modal on three consecutive attempts — blank — and then `0.3048`
 * once the dialog appeared. The dialog's window is `1024×514`, which is 27.4% of the `1080×1778`
 * region; a genuinely rendered page had measured `0.7530`. `RenderedContentPolicy` asks "is this
 * region non-uniform?", which is the right question for liveness, and a dialog is gloriously
 * non-uniform. The policy is not wrong; it was being asked about a region something else owned.
 *
 * So [Accept] requires **both** properties of the same frame. [ContentVerdict.Rendered] appears in
 * exactly one accepting row of the table below, and it is the row where Webora also owns the screen.
 * That is the defect, expressed as data.
 *
 * | focus | content | verdict |
 * |---|---|---|
 * | `OwnedByApp` | `Rendered` | [Accept] |
 * | `OwnedByApp` | `Blank` | [Retry] — the page has not rendered |
 * | `DismissableSystemAnr` | any | [Retry] — a dialog owned the screen when the frame was taken |
 * | `Blocked` | any | [Retry] — carrying the blocking reason through |
 *
 * **A dismissable ANR is a [Retry] here and never a dismissal.** Clearing an obstruction is
 * `requireAppOwnsScreen`'s job, with its own budget and its own records. A capture loop that pressed
 * buttons would be a second dismissal path, unbudgeted, running while the harness is supposed to be
 * observing.
 *
 * The verdict **composes** [ScreenEvidencePolicy.focusVerdict] and [RenderedContentPolicy.verdict]
 * rather than replacing either, so both keep their contracts and both fixture suites stay unedited.
 * It is pure and lives in `src/screenshotPolicy/java` for the reason `CI-002` and `CI-003` put their
 * decisions there: `./gradlew test` compiles it, and a checkout with no `/dev/kvm` can still prove
 * that a frame under a system dialog is not evidence.
 */
fun candidateVerdict(focus: FocusVerdict, content: ContentVerdict): CandidateVerdict = when (focus) {
    is FocusVerdict.OwnedByApp -> when (content) {
        is ContentVerdict.Rendered -> CandidateVerdict.Accept
        is ContentVerdict.Blank -> CandidateVerdict.Retry(
            "the page region has not rendered: differing=${content.differingFraction}",
        )
    }

    is FocusVerdict.DismissableSystemAnr -> CandidateVerdict.Retry(
        "${focus.title} owned the screen when the frame was taken",
    )

    is FocusVerdict.Blocked -> CandidateVerdict.Retry(
        "Webora did not own the screen when the frame was taken: ${focus.reason}",
    )
}
