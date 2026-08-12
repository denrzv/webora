# DEVX-002: Research
Status: RESEARCH_READY

Input for `/plan`. Maps what the screenshot pipeline produces today, where the two-artifact split has
to cut, what can compose a contact sheet without adding a dependency, and where that logic has to
live so the JVM gate can fail it.

## 1. What exists today, exactly

### The producing surfaces

| File | Role |
|---|---|
| `.github/workflows/android-screenshots.yml` | `workflow_dispatch` only; builds both APKs, boots the emulator, runs the journey, uploads **one** artifact, writes the summary |
| `scripts/android-screenshot-ci.sh` (92 lines) | Runs **inside** the emulator step. Installs and runs; never builds. Collects PNGs, writes `result.txt` |
| `scripts/android-emulator-ready.sh` | `CI-002`'s four-condition readiness gate; writes `artifacts/readiness.txt` |
| `app/src/androidTest/.../visual/LiveSiteScreenshotTest.kt` | Drives the journey; names the three canonical frames |
| `app/src/screenshotPolicy/java/.../ScreenEvidencePolicy` | `CI-002`'s focus classifier. **Out of scope — this ticket does not read it** |

### The canonical frame names, and their single source

`LiveSiteScreenshotTest.kt` calls `captureDeviceScreenshot(...)` with the literals `01-home.png`
(line 31), `02-siteskin-consent.png` (line 40), `03-siteskin-integrated.png` (line 47). Nothing else
in the repository enumerates them — `docs/SCREENSHOTS.md` describes them in prose, and the shell
script globs `*.png` rather than naming them.

That matters for acceptance criterion 7. The lexical order of these filenames **is** journey order,
by construction of the `01-`/`02-`/`03-` prefixes. A contact sheet that sorts its inputs and labels
each tile with its own filename therefore needs no second list, and cannot drift from one.

### Where files actually land

Two different mechanisms, and the split has to respect both:

```
artifacts/                                   ← staging dir, created by the workflow + script
  run.txt                                    commit= / run_id=          (workflow step 1)
  prebuilt-apks.txt                          APK presence proof          (script)
  readiness.txt                              every readiness sample      (android-emulator-ready.sh)
  instrumentation.txt                        connected-test stdout       (script, tee)
  logcat.txt                                 adb logcat -d               (script)
  result.txt                                 test_status= / png_count=   (script)
  screenshots/*.png                          ← copied out of the tree below

app/build/outputs/
  connected_android_test_additional_output/  ← Android test storage; CI-002's focus-*, interference-*,
                                               window-* diagnostics arrive here, NOT in artifacts/
  androidTest-results/                       runner XML
  reports/androidTests/                      runner HTML
```

The single upload step takes all four roots at once (`android-screenshots.yml:95-99`) under
`webora-screenshots-api33-${{ github.sha }}`, `if-no-files-found: error`, 7-day retention.

**So the reviewer's three PNGs sit at `artifacts/screenshots/` inside a ZIP that also carries an HTML
test report tree.** That is the whole complaint in one path.

### The summary today

`android-screenshots.yml:103-120` prints a fixed origin/device blurb, tells the reader to download
`webora-screenshots-api33-*`, and `cat`s `result.txt` if it exists. It never states the commit, the
run id, or a frame count outside that embedded file.

## 2. Where the split cuts

Criterion 4 (nothing in the screenshots artifact but images) and criterion 5 (enumerated diagnostics
survive) decide this mechanically:

| Path | Artifact |
|---|---|
| the three canonical PNGs, flattened to root | **screenshots** |
| `preview.png` | **screenshots** (new) |
| `artifacts/run.txt`, `prebuilt-apks.txt`, `readiness.txt`, `instrumentation.txt`, `logcat.txt`, `result.txt` | diagnostics |
| `connected_android_test_additional_output/` (whole tree, incl. `focus-*`, `interference-*`, `window-*`) | diagnostics |
| `androidTest-results/`, `reports/androidTests/` | diagnostics |

**The trap this creates.** `artifacts/screenshots/` is currently *inside* the staging directory that
otherwise becomes the diagnostics artifact. Uploading `artifacts/` wholesale to diagnostics and
`artifacts/screenshots/*` to screenshots duplicates the PNGs into both. Either exclude the
subdirectory from the diagnostics path, or stage the human-facing set somewhere else entirely. The
second is easier to read and harder to get subtly wrong.

**The reliability constraint is a `path:`/`if:` question, not a nice-to-have.** NFR says a
diagnostics-only outcome must remain possible. Today's single upload has `if-no-files-found: error`,
which is right for diagnostics and *wrong* for screenshots: a run that dies before capturing frames
must still publish logcat. So the two upload steps need different `if-no-files-found` settings, and
the summary must not name an artifact that was not produced.

## 3. Composing the contact sheet without adding a dependency

### The finding: the JDK already does this, headless

Verified in this container on the project's JDK 25 toolchain, not assumed:

```
headless=true
PNG writers=true                       javax.imageio ships a PNG reader and writer (required standard plugin)
font=SansSerif.plain  width("01-home.png")=159  height=29
written=true bytes=2820                ImageIO.write(img, "png", file)
roundtrip=400x200                      ImageIO.read back
ink_pixels=980                         non-background pixels after drawString — glyphs really rendered
```

`javax.imageio` + `java.awt.Graphics2D` compose, label and write a PNG with **zero** third-party
dependencies, in a headless JVM, on the toolchain the build already resolves.

That last line is the important one and it is the basis of a test assertion, not a curiosity: an
environment with no usable font would still write a perfectly valid PNG with blank labels. Counting
ink pixels is what distinguishes "wrote a file" from "drew the text", and it is cheap.

### Alternatives, and why they lose

| Option | Why not |
|---|---|
| ImageMagick (`montage`) on the runner | An unversioned dependency on whatever the `ubuntu-latest` image happens to ship. It cannot be exercised by `scripts/pre-commit-check.sh`, so criterion 9 fails by construction — the exact "runs only on a runner, verified by nothing" shape this session already hit with `androidTest`. |
| Python + Pillow | Needs an install step; same coverage problem; adds a second language to the evidence path for one image. |
| A marketplace action | Explicit PRD non-goal, and an external dependency in the evidence path. |
| A pure-Kotlin PNG encoder | Reimplements what the JDK already ships, correctly, including CRCs and zlib framing. |

### Where the logic has to live, and why that is the crux

Criterion 9 asks for a check that runs **in the JVM gate**. The repository has three worked examples
of this exact question and one live counter-example:

- `:siteskin-lint` — a pure-JVM module with the `application` plugin, `dev.siteskin.lint`, three test
  classes. `scripts/pre-commit-check.sh` runs `./gradlew test`, and CI's `android` job runs
  `./gradlew test` too, so **a new JVM module's tests are picked up by both with no wiring**. Detekt
  is applied to all subprojects from the root build, so it is gated as well.
- `scripts/android-emulator-ready-selftest.sh` — shell logic factored into a pure function
  (`readiness_verdict`) so a machine with no `/dev/kvm` can still test the decision.
- `src/screenshotPolicy/java` — shared into `test` *and* `androidTest` precisely so the decision is
  reachable from `./gradlew test`, with `CLAUDE.md` recording that `androidTest`-only would put it
  "where `./gradlew test` cannot reach it".
- **The counter-example, found in this session:** adding a parameter to `SiteSkinConsentDialog` broke
  `BrowserFontScaleTest.kt:57` and the gate stayed green, because `scripts/pre-commit-check.sh` never
  compiles the `androidTest` source set. A compile error there cannot fail the gate.

So: a new pure-JVM Gradle module following `:siteskin-lint`'s shape satisfies criterion 9 for free,
and putting the composition in the shell script or the instrumented test does not.

A module also gets the invocation for free — the workflow already runs Gradle for the APK build, so
composing after the emulator step is one `./gradlew` call in a step that is not inside the emulator
step, satisfying the NFR that this work must not contend with a booting `system_server`.

### Scale and memory

Three Pixel 6 frames at 1080×2400 are ~2.6 M pixels each, ~31 MB as `TYPE_INT_RGB` in memory for all
three plus a sheet. Trivial for the runner, but the sheet should be drawn at a reduced tile width
(`Graphics2D.drawImage` with bilinear interpolation) or the output is a 3240×2400 PNG nobody wants to
open. Tile width is a composition parameter the plan should fix, along with label band height.

## 4. Trust boundary

This ticket is one layer above the manifest pipeline: no origin is contacted, no manifest is parsed,
and `SiteSkinConfiguration` is not touched. The analogous boundary is `CI-002`'s — **what the harness
may show or hide from the person looking at the picture** — and it has one concrete rule here:

> **Tile labels are harness-authored and derive only from the canonical filename.** No text is read
> out of the screenshot, out of the page, or out of the manifest, and none is passed in from the
> workflow. The frames depict manifest-driven UI; nothing manifest-driven may become the *label* that
> tells a reviewer what they are looking at.

The failure this forbids is small and plausible: a label sourced from the page title or a manifest
field would let the site under test caption Webora's own evidence.

Nothing else in the manifest-controlled surface is reachable from this ticket. `ScreenEvidencePolicy`
stays untouched; `DEVX-002` changes what happens to a frame *after* `CI-002` has decided it was
allowed to exist.

Secrets: the summary and the labels must carry only the commit SHA, the run id, filenames and counts.
`GITHUB_SHA` and `GITHUB_RUN_ID` are not secrets; no other environment value should be interpolated
into either.

## 5. Risks the plan must answer

1. **The sheet that lies.** Best-effort composition — skip a missing frame, carry on — publishes a
   picture of a journey that did not happen. Assembly must be fatal on a missing, unreadable or
   unplaceable frame (criterion 6), and the test for that is a negative one: give the composer two
   frames when it expects three and assert it refuses.
2. **Blank labels on a font-less host.** A valid PNG with invisible text passes any "did it write a
   file" assertion. Guard: assert ink pixels in the label band, as measured above.
3. **Duplicating the PNGs into both artifacts** while splitting the upload paths (§2).
4. **Losing a `CI-002` diagnostic** in the split — those files arrive through Android test storage in
   a different tree from everything else, so they are the easiest to drop.
5. **Breaking the diagnostics-only path** by giving the screenshots upload `if-no-files-found: error`
   on a run that failed before capture.
6. **Composition inside the emulator step**, re-creating the contention `CI-002` removed.
7. **A new module escaping the gates.** Root-applied detekt and `./gradlew test` cover it, but the
   module must not silently opt out — check `settings.gradle.kts`, the toolchain/jvmTarget pair (25
   toolchain, 21 target) and that its tests actually run in `scripts/pre-commit-check.sh` output.

## 6. Open questions for `/plan`

- Module name and package for the composer (`:siteskin-lint` sets the shape; this is not SiteSkin
  protocol, so `dev.siteskin.*` is probably wrong).
- Sheet geometry: tile width, columns for three frames, label band height and font size.
- Whether the composer discovers frames by glob or is handed an explicit input directory — glob keeps
  criterion 7's "no second list" property; an explicit count is what makes criterion 6 enforceable.
  Both are satisfiable: glob the directory, then require the discovered set to be non-empty and every
  discovered file to be readable and placed.
- Whether `docs/SCREENSHOTS.md`'s table becomes two tables or one table with an artifact column.
