# Review: DEVX-002
Date: 2026-08-12
Status: CLOSED

## Summary

Splits the screenshot evidence into a human-facing artifact (three frames flattened to the root plus
one `preview.png`) and a diagnostics artifact, composed by a new pure-JVM `:evidence-sheet` module.

Verified on hosted run **9** (`31570493622`, `c3ae985f`): `composed tiles=3 against png_count=3`,
`webora-screenshots-c3ae985f…` at 273 KB and `webora-screenshot-diagnostics-c3ae985f…` at 1.04 MB.
Run **8** failed first, on a real defect the ticket's own count guard caught rather than published.

One finding with integrity weight, one cosmetic, one nit. None blocks the ticket's claims as
documented, but `FINDING-1` weakens one of them and is cheap to close.

## Architecture

| Concern | Assessment |
|---|---|
| Module placement | Correct, and load-bearing. A composer in `android-screenshot-ci.sh` or `androidTest` is verified by nothing locally — the gate never compiles `androidTest`, proven by a compile error that survived a green gate earlier today. As a JVM module it is in `./gradlew test` and root-applied detekt with no wiring. |
| Dependencies | None added. `javax.imageio` + `java.awt` are JDK-standard and were verified headless before being planned around, not assumed. |
| Separation | `ContactSheet.kt` has no process concerns; `Main.kt` owns argument parsing and exit codes and returns its status rather than calling `exitProcess`, which is what makes the `tiles=` contract testable in-process. |
| Staging split | Two disjoint root directories rather than one directory with a negated upload glob. A YAML typo cannot duplicate frames into diagnostics or leak diagnostics into the review bundle. |
| Composition placement | Outside the emulator step, per `CI-002`'s contention finding. Confirmed in run 9's step ordering. |
| Coupling to `CI-002` | None. `ScreenEvidencePolicy` is neither read nor modified. The tickets stay layered: one decides whether a frame may exist, the other what happens to it. |

## Security

| Property | Assessment |
|---|---|
| Label provenance | Enforced structurally. `composeContactSheet(dir)` has no parameter for a title, caption or label, so there is no argument through which workflow, page or manifest text could reach the image. The caption is derived from the tile's own `Path` inside the drawing loop. |
| Ordering | Single sort on filename; journey order by construction from the `01-`/`02-`/`03-` prefixes. No second list to drift. |
| Totality | An unreadable frame throws rather than being skipped, with a negative control proving the happy-path tests would otherwise mask it. |
| Arithmetic check | `tiles=` vs `png_count` compared in the workflow; exercised against four scenarios locally, including the 2-vs-3 case that must exit 1. |
| Secrets | Only `GITHUB_SHA`, `GITHUB_RUN_ID`, filenames and counts reach the summary. Nothing is interpolated into the image at all. |
| Artifact scope | Unchanged: run-scoped, `contents: read`, 7-day retention, `workflow_dispatch` only. |
| Stale-sheet window | Was `FINDING-1`, now closed by `TASK-FIX-2`: the output is deleted before composing, so after any call the sheet is either current or absent. Negative control confirmed. |

## Findings

### FINDING-1 · Low (integrity) · stale sheet survives a failed compose
**File:** `evidence-sheet/src/main/kotlin/app/webora/evidence/ContactSheet.kt:47`

Reproduced, not theorised. Composing three valid frames, then corrupting one and re-composing:

```
tiles=3
sheet written: 33383 bytes
Not a readable image: …/stale/02-siteskin-consent.png
preview.png still present? YES-STALE
```

The throw is correct; what is missing is that the *previous* sheet outlives it. `Upload screenshots
for review` runs with `if: always()`, so on a red run the bundle would carry current frames beside a
sheet describing an earlier composition — a picture of a journey that did not happen, which is the
exact failure this ticket is written against.

**Not reachable in CI today**, because each run starts on a fresh runner with an empty `review/`. It
is reachable for anyone running the composer twice locally, and the invariant is worth having
unconditionally rather than resting on a property of the environment.

Fix: delete the output before composing, so after any call `preview.png` is either the sheet for the
current frames or absent.

```kotlin
fun composeContactSheet(directory: Path): Int {
    val frames = discoverFrames(directory)
    // Any earlier sheet is void the moment we begin: if composition fails from here on, no
    // preview.png must survive to be uploaded beside frames it does not describe.
    Files.deleteIfExists(directory.resolve(PREVIEW_FILE_NAME))
    …
```

### FINDING-2 · Low (cosmetic) · a long filename overruns its tile
**File:** `evidence-sheet/src/main/kotlin/app/webora/evidence/ContactSheet.kt:118`

`g.drawString(tile.label, left, labelBaseline)` has no clip, so a caption wider than `TILE_WIDTH`
runs into the neighbouring tile's label. Today's names (`03-siteskin-integrated.png`, ~250 px at 22 pt
against a 360 px tile) fit, so this is latent rather than live — but the caption is the one thing on
the sheet making a claim about which frame is which, and two overlapping claims are worse than a
truncated one.

Fix: clip each label to its own tile column before drawing.

### FINDING-3 · Nit · redundant cast
**File:** `evidence-sheet/src/test/kotlin/app/webora/evidence/ContactSheetTest.kt:175`

`ImageIO.write(image, "png", path.toFile() as File)` — `toFile()` already returns `File`.

## Not findings

- **`Main.kt` catches only `ContactSheetFailure`.** An `IOException` from a full disk propagates as a
  stack trace with a non-zero exit and no `tiles=` line. That is the correct outcome: the workflow's
  comparison fails on the empty count, and an unexpected failure should look unexpected rather than
  be flattened into the same message as a refusal.
- **`./gradlew … | sed` masks Gradle's exit status.** Deliberate and safe — a Gradle failure yields an
  empty `tiles`, which never equals a non-zero `png_count`, so the step still exits 1. Run 8 is the
  worked example. A `PIPESTATUS` check would add a second way to say the same thing.
- **The compose step exits 0 when `result.txt` is absent or `png_count=0`.** Not a swallowed error:
  the journey has already failed and coloured the run red, and forcing a second failure here would
  only obscure the first. The summary says no screenshots artifact was produced.
- **Tiles of differing heights would leave a gap above the label row.** All frames come from one
  device profile, so heights are uniform; a per-tile label baseline would be the fix if that ever
  changes. Not worth pre-building.
- **`preview.png` sits in the same directory it reads from.** Excluded from discovery with a negative
  control, and keeping the sheet beside its frames is the reason the artifact needs no subdirectory.
- **No `actionlint`.** The gate does not provide it. The workflow's shell was instead extracted and
  driven directly against four scenarios, which is stronger than a lint pass for the logic that
  matters and weaker for YAML schema. Recorded, not claimed as equivalent.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `ContactSheet.kt` | 9 in `ContactSheetTest` | Order, label ink, label provenance, empty dir, missing dir, relative-path message, undecodable frame, self-exclusion, aspect ratio |
| `Main.kt` | 4 in `MainTest` | `tiles=N` format, no count on failure, zero-arg and two-arg usage errors |
| `android-screenshot-ci.sh` | gate `shellcheck`, `bash -n`, sourcing property | Emulator half unreachable locally |
| `android-screenshots.yml` | 4 extracted scenarios + 2 summary branches | Upload behaviour needs a runner; supplied by run 9 |

Negative controls: 3 in `TASK-1` (preview exclusion, undecodable skip, blank labels) and 1 in
`TASK-FIX-1` (working directory). All reproduced their target failure and were restored.

## Verdict

**Accepted.** All three findings were closed by `TASK-FIX-2`, each with a negative control. `FINDING-2` and `FINDING-3` fold into the same commit. The
ticket's documented claims hold as verified on run 9; `FINDING-1` closes the one gap between the
claim "the composer never publishes a sheet that does not account for every frame" and what the
filesystem is left holding when it refuses.
