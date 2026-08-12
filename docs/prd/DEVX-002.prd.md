# DEVX-002: Screenshot review experience
Status: PRD_READY

## Context / Problem

`CI-001` made the hosted Pixel 6 journey possible and `CI-002` made its frames trustworthy. Neither
made them *reviewable*. A reviewer who wants to answer "does the Bloom Flowers integration still look
right?" currently has to:

1. open the run, find one artifact named `webora-screenshots-api33-<sha>`, and download it;
2. unzip a bundle that mixes three canonical PNGs with `androidTest-results/`, HTML test reports,
   `logcat.txt`, and Android test storage's own directory tree;
3. locate the PNGs at `artifacts/screenshots/01-home.png` — behind a directory named after the
   pipeline's staging area rather than after anything a reviewer cares about;
4. open three files one at a time to reconstruct a journey that is inherently sequential.

The job summary does not help: it prints `result.txt` (`test_status`, `png_count`) and tells the
reader to go find the artifact. Nothing states what the run was *of* — which commit, which run, how
many frames — in the place GitHub shows first.

This is archive archaeology for evidence that is supposed to be glanceable, and it taxes the exact
moment the evidence exists to serve: looking at a picture and deciding whether it is acceptable.

**The evidence-integrity rule from `CI-002` extends here, and this ticket is where it can be
broken.** `CI-002`'s question was what the harness may hide from the person looking at the picture.
A contact sheet is a *derived* image: it is assembled by code, and code that silently drops, reorders
or substitutes a frame produces a picture of a journey that did not happen — with the job green and
the reviewer none the wiser. That failure mode is the reason this ticket is P1 rather than cosmetic,
and the constraint it implies is stated in the acceptance criteria rather than left to taste.

## Goals

- One human-facing artifact whose entire contents are visual evidence a reviewer wants to look at.
- Canonical PNGs at that artifact's **root**, not behind a pipeline-implementation directory.
- One labelled `preview.png` contact sheet showing the complete journey in order, so the common case
  is a single image.
- Diagnostics — logcat, instrumentation output, raw connected-test additional output, result and
  readiness metadata — preserved in full, in a **separate** artifact.
- A job summary that states screenshot count, commit and run identity, and both artifact names.

## Non-goals

- Any external image host, preview SaaS, or third-party action added only to make images clickable.
  GitHub's artifact download is the transport; this ticket improves what is inside it.
- Changing which frames are captured, how many there are, or the journey the test drives. That is
  `CI-002`'s and `DEVX-003`'s territory.
- Publishing screenshots to a Release, a branch, a wiki, or a pull-request comment. The workflow is
  `workflow_dispatch`-only and stays that way.
- Retention, permissions or trigger changes.
- Any change to what may be dismissed on screen before a capture. `ScreenEvidencePolicy` is
  `CI-002`'s, and this ticket does not read, extend or relax it.

## User stories

- As a reviewer, I download one artifact, open `preview.png`, and can judge the whole journey without
  opening anything else.
- As a reviewer who spots something wrong in the contact sheet, I open the full-resolution PNG of
  that frame from the same folder, without a second download.
- As someone diagnosing a failed run, I still get logcat, instrumentation output and every readiness
  sample — in their own artifact, so they are not in the way when I only wanted the pictures.
- As someone reading the run page, the job summary tells me which commit and run produced the
  evidence, how many frames it contains, and what the two artifacts are called.
- As a reviewer, I can trust that the contact sheet shows every canonical frame the run captured —
  because a run that could not assemble a faithful sheet fails instead of publishing a partial one.

## Acceptance criteria

1. The workflow uploads exactly two artifacts on every completed run: a human-facing screenshots
   artifact and a diagnostics artifact, each named distinctly and both including the commit SHA.
2. `01-home.png`, `02-siteskin-consent.png` and `03-siteskin-integrated.png` are at the **root** of
   the screenshots artifact, at full capture resolution, with no intermediate directory.
3. The screenshots artifact contains `preview.png`: a single image showing every canonical frame in
   journey order, each labelled with its own filename, and no other content.
4. The screenshots artifact contains nothing that is not an image a reviewer would look at — no
   logcat, no instrumentation output, no test-runner reports, no staging directories.
5. The diagnostics artifact contains logcat, instrumentation output, the raw connected-test
   additional output tree (including `CI-002`'s `focus-*`, `interference-*` and `window-*` files),
   readiness samples, and the run/result metadata.
6. Contact-sheet assembly is **total or fatal**: if a canonical frame is missing, unreadable, or
   cannot be placed, the step fails and the run goes red. It never emits a sheet that silently omits,
   reorders, or substitutes a frame, and never labels a tile with anything other than the file it
   drew.
7. The label drawn on each tile is derived from the canonical filename, not from a list written
   separately — so a renamed or added frame cannot produce a sheet whose labels disagree with its
   images.
8. The job summary states the screenshot count, the commit SHA, the run id, and both artifact names.
9. Contact-sheet composition logic is exercised by a check that runs in the JVM gate, without an
   emulator and without a GitHub runner. A step that only ever executes inside the screenshot
   workflow is not covered by anything, and this repository has already been bitten by a source set
   the gate does not compile.
10. `docs/SCREENSHOTS.md` describes the two artifacts and their contents as shipped, replacing the
    single-artifact table.
11. No new external service, image-hosting dependency, or third-party action is introduced.
12. `bash scripts/pre-commit-check.sh` passes.

## NFR

- **Security/privacy:** screenshots are captured from a public reference integration on a throwaway
  emulator, so they carry no user data; that stays true because this ticket does not change what is
  captured. No artifact gains broader visibility — both remain run-scoped artifacts under the
  existing `contents: read` permission and seven-day retention. No credential, token or environment
  value may reach the summary or a label.
- **Reliability/fallback:** a diagnostics-only outcome must remain possible. When the journey fails
  before producing frames, the diagnostics artifact must still upload — that is the run a person most
  needs to read. The screenshots artifact may legitimately be absent in that case, and the summary
  must say so rather than pointing at an artifact that is not there.
- **Performance:** contact-sheet assembly runs on three ~1080×2400 PNGs and must not add a
  meaningful fraction of the 40-minute job budget; it also must not run inside the emulator step,
  where `CI-002` established that host contention is what starved System UI into an ANR.
- **Accessibility:** the summary is text and stays text — the artifact names and counts must be
  readable without opening an image. Tile labels are rendered legibly at the sheet's own scale; the
  contact sheet is a convenience over the full-resolution PNGs, never a replacement for them.

## Risks

- **A derived image that lies.** The sheet is assembled by code; a bug that drops or misorders a
  frame publishes a picture of a journey that did not happen. Criterion 6 makes assembly fatal rather
  than best-effort, and criterion 7 removes the second list that labels could drift from.
- **Coverage theatre.** Anything that runs only on a GitHub runner is verified by nothing on this
  machine. This session already found a compile error that survived a green gate because
  `scripts/pre-commit-check.sh` never compiles the `androidTest` source set. Criterion 9 exists so
  the same shape is not recreated in the screenshot pipeline.
- **The convenient dependency.** Reaching for ImageMagick, Pillow, or a marketplace action is the
  obvious way to compose a sheet and the easy way to add a dependency to a repository that has
  deliberately taken none in its reference site and none in its evidence path. The plan must state
  what it uses and why that is available without installation.
- **Splitting the artifact and losing something.** Moving files between two upload paths risks a
  diagnostic quietly ceasing to be uploaded. Criterion 5 enumerates what must survive the split,
  including the `CI-002` diagnostic files that arrive through Android test storage rather than the
  staging directory.

## Open questions

None. The scope, the two-artifact split, the contact sheet and the summary contents are fixed by
`docs/BACKLOG.md`'s `DEVX-002` entry and `docs/DEVELOPER_PLAN.md`'s Track A ordering. How the sheet
is composed, and where its logic is tested from, are implementation decisions for `/plan` after
`/researcher` maps what the runner already provides.
