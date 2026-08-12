# QA Report: DEVX-002
Status: QA_PASSED

## Scope

The screenshot **review experience**: two artifacts instead of one, canonical frames flattened to the
screenshots artifact's root, one labelled `preview.png` contact sheet, and a job summary that states
count, commit, run id and both artifact names.

Nothing in the browser changed. No `:app`, `:siteskin-core` or `:siteskin-lint` source is touched, no
manifest is parsed, and `ScreenEvidencePolicy` — `CI-002`'s capture guard — is neither read nor
modified. The layering is deliberate: `CI-002` decides whether a frame may exist, `DEVX-002` decides
what happens to it afterwards.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Three frames compose in journey order | `ContactSheetTest.composesOneTilePerFrameInFilenameOrder` — inputs supplied out of order, tiles traced by marker colour | PASS — sorted, not argument-ordered |
| 2 | Captions are actually drawn, not just written | `labelsAreDrawnAndNotBlank` — ink-pixel count inside each label band | PASS — a font-less host would write a valid PNG with invisible labels; only pixel counting separates the two |
| 3 | A caption belongs to the frame it captions | `labelComesFromTheFileItDraws` — identical pixels, different filename | PASS — only the renamed tile's band changes |
| 4 | A caption cannot invade its neighbour | `labelsAreClippedToTheirOwnTile` — 123-character filename, gutter ink asserted zero | PASS |
| 5 | An unreadable frame is fatal, never skipped | `refusesAnUndecodablePng` | PASS — throws, no sheet written |
| 6 | A refusal leaves no stale sheet | `aFailedCompositionLeavesNoStaleSheet` — compose, corrupt a frame, recompose | PASS after `TASK-FIX-2`; **reproduced as a real defect first** |
| 7 | The sheet never becomes its own input | `excludesAnExistingPreviewFromItsOwnInput` — composed twice | PASS — 2 tiles both times |
| 8 | Empty and missing directories refuse | `refusesADirectoryWithNoFrames`, `refusesAMissingDirectoryAndNamesTheResolvedPath` | PASS |
| 9 | Path failures name where they looked | `namesTheResolvedPathWhenARelativeDirectoryIsEmpty` | PASS — absolute in the message |
| 10 | Evidence is not stretched | `preservesAspectRatio` — 1080×2400 input | PASS |
| 11 | `tiles=N` is exactly one line, and absent on failure | `MainTest` ×4 | PASS |
| 12 | Emulator script stages to two disjoint directories | `shellcheck`, `bash -n`, sourcing defines functions and runs nothing | PASS |
| 13 | Workflow compose step: counts agree | extracted `run:` block, stubbed `gradlew` printing `tiles=3`, `png_count=3` | PASS — exit 0 |
| 14 | Workflow compose step: **counts disagree** | same harness, `tiles=2` vs `png_count=3` | PASS — exit 1, "Refusing to publish a sheet that does not account for every frame" |
| 15 | Workflow compose step: no frames / no `result.txt` | same harness | PASS — exit 0 with a stated reason, so the journey's own failure stays the visible one |
| 16 | Summary, populated | extracted `run:` block with `GITHUB_SHA` / `GITHUB_RUN_ID` set | PASS — commit, run, `Screenshots captured: **3**`, both artifact names |
| 17 | Summary, nothing captured | same harness, no `result.txt` | PASS — says no screenshots artifact was produced rather than naming one that is not there |
| 18 | **Hosted end-to-end** | run **9** `31570493622` @ `c3ae985f` | PASS — `composed tiles=3 against png_count=3`; `webora-screenshots-c3ae985f…` 273 KB; `webora-screenshot-diagnostics-c3ae985f…` 1.04 MB |
| 19 | **Hosted failure path** | run **8** `31568159235` @ `ca981f91` | PASS *as a guard* — composer resolved the wrong directory, the count check refused to publish, run went red. `TASK-FIX-1` |

Gate: `bash scripts/pre-commit-check.sh` OK. Module tests: **15**, 0 failures
(`ContactSheetTest` 11, `MainTest` 4). Negative controls: 6, each isolating its target test —
preview exclusion, undecodable skip, blank labels, working directory, stale sheet, label clip.

## Edge cases

- **invalid manifest → regular browser mode** — N/A. No manifest is parsed, fetched or validated by
  anything in this ticket; `:siteskin-core` and the discovery pipeline are untouched. The frames
  *depict* manifest-driven UI, which is the reason for the one rule that does apply: a tile's caption
  derives only from that tile's filename, so nothing manifest-driven can caption Webora's evidence.
- **origin change / redirect** — N/A. No network request is made by the composer or the workflow
  steps this ticket adds. The journey's own navigation is `CI-001`/`CI-002` behaviour and unchanged.
- **offline with cached manifest** — N/A — no logic change. The composer reads local files only.
- **oversized or malformed payload** — **Applicable, and covered.** The composer's "payload" is a
  directory of PNGs. Malformed is scenario 5 (fatal, never skipped) and scenario 6 (no stale sheet
  survives). Oversized is bounded by what the device produces: three ~1080×2400 frames, tiled at 360 px
  wide, and composition ran in 6 s on the hosted runner (`06:44:49`–`06:44:55`). There is no
  attacker-controlled size input — filenames and frame count come from the capturing test.
- **accessibility (TalkBack, font scale)** — N/A for the app; **partially applicable to the artifact**.
  No Compose surface, string resource or semantics node changes, so `A11Y-001`'s gate is unaffected
  and `BrowserSurfaceConventionsTest` is untouched. For the evidence itself: the job summary carries
  the count, commit, run id and artifact names as **text**, so a reviewer never has to open an image
  to learn what a run produced, and full-resolution frames sit beside `preview.png` rather than being
  replaced by it. The contact sheet is a convenience over the frames, never a substitute.

## Result
Status: QA_PASSED

Notes:

- **One claim is not verified and must not be read as verified.** Artifact *download* is blocked for
  this session — the GitHub API returns `GitHub access is not enabled for this session`, and the MCP
  tools expose no download method — so **the pixels of run 9's frames have not been seen**. What is
  established is that `ScreenEvidencePolicy` permitted all three captures, which it does only when
  Webora owns the focused window, and that no `Refusing to capture` appears in the log. Whether the
  frames are visually free of the `System UI isn't responding` overlay is for the owner to confirm by
  opening `preview.png`.
- The `System UI` frames that prompted this ticket came from run **5** (`328bd08d`, 12:30 UTC), which
  predates every commit of `CI-002`. That is a stale-artifact reading, not a live defect, and it is
  now called out in `docs/SCREENSHOTS.md` because splitting the artifacts makes the wrong one easier
  to open too.
- `actions/upload-artifact`'s behaviour cannot be exercised locally; run 9 supplies it. The two
  artifact sizes — 273 KB reviewable, 1.04 MB diagnostics, against run 7's single 2.1 MB bundle — are
  the observable proof the split does what it claims.
- The branch carries another session's unmerged `CI-002` commits beneath this ticket's, so any pull
  request opened from it spans both tickets.
