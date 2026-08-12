# DEVX-002: Plan
Status: PLAN_APPROVED

Input: [`../research/DEVX-002.md`](../research/DEVX-002.md) (`RESEARCH_READY`).

## Flow

```
emulator step (unchanged contention profile)
  scripts/android-screenshot-ci.sh
    ├─ require prebuilt APKs        (CI-002, untouched)
    ├─ readiness gate               (CI-002, untouched)
    ├─ connectedDebugAndroidTest → captures via Android test storage
    ├─ copy canonical PNGs ──────────────────────────────► review/          ← human-facing staging
    └─ logcat / instrumentation / result ────────────────► artifacts/       ← diagnostics staging

workflow, AFTER the emulator step (never inside it)
  ./gradlew :evidence-sheet:run --args="review"
    └─ review/preview.png, and "tiles=N" on stdout
  verify tiles == png_count
  upload  review/                                   → webora-screenshots-<sha>
  upload  artifacts/ + androidTest-results/
          + connected_android_test_additional_output/
          + reports/androidTests/                   → webora-screenshot-diagnostics-<sha>
  summary: count, commit, run id, both artifact names
```

## Trust boundary

No origin is contacted, no manifest is parsed, and `SiteSkinConfiguration` is never constructed. This
ticket sits one layer above the manifest pipeline, so the boundary it owns is `CI-002`'s — *what the
harness may show or hide from the person looking at the picture* — narrowed to one rule:

> **A tile's label is harness-authored and derives only from that tile's own filename.** No text is
> read out of the screenshot, out of the page under test, or out of the manifest, and none is passed
> in from the workflow. The frames depict manifest-driven UI; nothing manifest-driven may become the
> caption that tells a reviewer what they are looking at.

`ContactSheet` therefore takes a directory and nothing else. It has no parameter for a title, a
caption, a legend or a per-tile label, so there is no argument through which workflow, page or
manifest text could reach the image. Adding one later is the violation, not merely a smell — the
label is the only thing on the sheet that makes a claim about what a frame *is*.

Two consequences the implementation must preserve:

- **Order is filename order, and journey order is filename order by construction.** The `01-`/`02-`/
  `03-` prefixes come from `LiveSiteScreenshotTest.kt`. Sorting the discovered files and labelling
  each tile with its own name means there is no second list to drift — criterion 7 — and a renamed or
  added frame changes both the position and the label together.
- **`ScreenEvidencePolicy` is not read, extended or relaxed.** `CI-002` decides whether a frame was
  allowed to exist; `DEVX-002` only decides what happens to it afterwards.

Secrets: only `GITHUB_SHA`, `GITHUB_RUN_ID`, filenames and counts reach the summary. Nothing is
interpolated into the image at all.

## Security / integrity

The one failure mode worth engineering against is a **derived image that lies** — a sheet that
publishes a journey that did not happen, on a green run.

| Rule | Mechanism |
|---|---|
| Never silently omit a frame | Fatal on an unreadable or undecodable input; the composer prints `tiles=N` and the script fails the run if `N` disagrees with `png_count` |
| Never invent a frame | Inputs are discovered from one directory by glob; there is no synthesis path |
| Never reorder | Single deterministic sort, asserted |
| Never mislabel | Label is derived from the same `Path` the tile is drawn from, inside one loop iteration |
| Never include itself | `preview.png` is excluded from discovery, with a negative control |
| Never publish blank labels | The test counts ink pixels inside the label band — a font-less host writes a valid PNG with invisible text, and only pixel counting distinguishes that from success |

"A canonical frame is missing" is caught twice over and neither check is a hardcoded list: the
journey itself goes red (`exit "$test_status"` already), and `tiles != png_count` fails the compose
step. A two-tile sheet can therefore only appear on an already-red run, and its labels (`01-…`,
`03-…`) show the gap.

## Files

### New module — `:evidence-sheet`

Shaped on `:siteskin-lint`: pure JVM, `application` plugin, JDK 25 toolchain with `jvmTarget` 21.
Package `app.webora.evidence` — Webora's namespace, not `dev.siteskin`, because this is harness
tooling and not the protocol.

| File | Contents |
|---|---|
| `settings.gradle.kts` | `include(":evidence-sheet")` |
| `evidence-sheet/build.gradle.kts` | kotlin-jvm + application, `mainClass = app.webora.evidence.MainKt`, `testImplementation(libs.junit)`. **No other dependency** |
| `.../main/kotlin/app/webora/evidence/ContactSheet.kt` | `composeContactSheet(dir: Path): Int` — discover, sort, decode, draw, write, return tile count. Pure apart from the filesystem; no `System.exit`, no printing |
| `.../main/kotlin/app/webora/evidence/Main.kt` | Argument handling, `tiles=N` on stdout, exit codes. Thin |
| `.../test/kotlin/app/webora/evidence/ContactSheetTest.kt` | The gate (below) |

Composition is `javax.imageio` + `java.awt.Graphics2D`, verified headless on this toolchain in
research (`PNG writers=true`, `ink_pixels=980`). No third-party image dependency, so nothing new to
install on the runner and nothing unversioned to depend on.

**Geometry** — fixed constants, one row, left to right:

| Constant | Value | Why |
|---|---|---|
| `TILE_WIDTH` | 360 px | 1080 ÷ 3; a 3-frame sheet lands near 1144 px wide, which opens comfortably |
| tile height | derived per frame, aspect-preserving | never distort evidence |
| `LABEL_BAND` | 48 px | fits 22 px text with padding |
| `LABEL_FONT` | `SANS_SERIF` plain 22 | logical font, no bundled asset |
| `PADDING` | 16 px | gutter and outer margin |

One row rather than a grid: with three frames a grid buys nothing, and left-to-right is an
unambiguous reading order for a sequence. If the journey ever grows past what a row carries legibly,
that is a deliberate later change, not something to pre-build.

Rendering uses `RenderingHints.VALUE_INTERPOLATION_BILINEAR` for the downscale and
`VALUE_TEXT_ANTIALIAS_ON` for labels.

### Changed — `scripts/android-screenshot-ci.sh`

- Stage canonical PNGs into **`review/`** at the repo root instead of `artifacts/screenshots/`.
  Research's alternative — upload `artifacts/` to diagnostics with the screenshots subdirectory
  negated — puts the correctness of the split inside a YAML glob. Two disjoint directories cannot
  duplicate or leak into each other by a typo.
- `png_count` counts `review/*.png`.
- `artifacts/` keeps every diagnostic exactly as today. `mkdir -p artifacts/screenshots` goes away.
- The prebuilt-APK precondition, the readiness gate, the logcat capture and `exit "$test_status"` are
  untouched. This script's `CI-002` responsibilities do not move.

### Changed — `.github/workflows/android-screenshots.yml`

- Step 1 keeps writing `artifacts/run.txt`; drop `mkdir -p artifacts/screenshots`.
- **New step, `if: always()`, after the emulator step and outside it:** run the composer, then
  compare `tiles=` against `png_count=` from `artifacts/result.txt` and fail on disagreement. Outside
  the emulator step is a requirement, not tidiness — `CI-002` established that host contention during
  emulator life is what starved System UI into an ANR.
- Replace the single upload with two:

| | screenshots | diagnostics |
|---|---|---|
| name | `webora-screenshots-${{ github.sha }}` | `webora-screenshot-diagnostics-${{ github.sha }}` |
| path | `review/` | `artifacts/`, `androidTest-results/`, `connected_android_test_additional_output/`, `reports/androidTests/` |
| `if-no-files-found` | `warn` | `error` |
| retention | 7 days | 7 days |

The asymmetry is the reliability contract: a run that dies before capturing anything must still
publish logcat, and must not fail the upload because there were no pictures.

- Summary states screenshot count, `GITHUB_SHA`, `GITHUB_RUN_ID`, and both artifact names — and says
  plainly when no screenshots artifact was produced rather than naming one that is not there.

### Changed — docs

- `docs/SCREENSHOTS.md` — the single-artifact table becomes two, with `preview.png` described and the
  `CI-002` diagnostics kept where they now live.
- `docs/ROADMAP.md` — tick `DEVX-002` at `/validate`.
- `CLAUDE.md` — a `DEVX-002` paragraph in the same register as the `CI-002` one: the label rule, the
  two-artifact split, and why the composer is a JVM module rather than a shell step.

## Tests

All JVM, all in `./gradlew test`, so `scripts/pre-commit-check.sh` and CI's `android` job both carry
them with no extra wiring — criterion 9. Fixtures are small PNGs written by the test itself through
`ImageIO`, so nothing binary is committed.

| Test | Asserts |
|---|---|
| `composesOneTilePerFrameInFilenameOrder` | 3 inputs → `tiles=3`; deterministic sheet width; tile order matches sorted filenames |
| `labelsAreDrawnAndNotBlank` | ink pixels > 0 inside each label band — the font-less-host guard |
| `labelComesFromTheFileItDraws` | renaming an input changes the label band that changed, and only it |
| `refusesADirectoryWithNoFrames` | throws; no `preview.png` written |
| `refusesAnUndecodablePng` | a truncated/garbage `.png` is fatal, not skipped |
| `excludesAnExistingPreviewFromItsOwnInput` | re-running twice yields the same tile count |
| `preservesAspectRatio` | a 1080×2400 input is not distorted |

**Negative controls** (revert, confirm red, restore — recorded in the tasklist):

1. Remove the `preview.png` exclusion → `excludesAnExistingPreviewFromItsOwnInput` must fail.
2. Make an undecodable input a `continue` instead of a throw → `refusesAnUndecodablePng` must fail
   while the happy-path tests still pass, which is the point: they would mask it.
3. Draw labels with a zero-alpha colour → `labelsAreDrawnAndNotBlank` must fail while every
   dimension and count assertion still passes.

**Not verifiable here, and stated as such:** the workflow YAML itself, the two-artifact split, and
the summary text run only on a GitHub runner. They are reviewed, not gate-enforced. `/qa` records
that; the composer — the part with logic worth testing — is deliberately the part the gate can reach.

## Out of scope

Frame selection, capture policy, `ScreenEvidencePolicy`, retention, triggers, permissions, the
inspector overlay (`DEVX-003`), and publishing evidence anywhere outside run artifacts.
