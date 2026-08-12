# DEVX-002: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/DEVX-002.prd.md`
- Research: `docs/research/DEVX-002.md`
- Plan: `docs/plan/DEVX-002.md`

## Tasks

- [x] TASK-1: The contact-sheet composer, as a JVM module the gate can fail
  - New: `evidence-sheet/build.gradle.kts`
  - New: `evidence-sheet/src/main/kotlin/app/webora/evidence/ContactSheet.kt`
  - New: `evidence-sheet/src/test/kotlin/app/webora/evidence/ContactSheetTest.kt`
  - Modified: `settings.gradle.kts`
  - Acceptance: `:evidence-sheet` is a pure-JVM module shaped on `:siteskin-lint` — kotlin-jvm plus
    `application`, JDK 25 toolchain, `jvmTarget` 21, `testImplementation(libs.junit)` and **no other
    dependency**. `composeContactSheet(dir: Path): Int` discovers `*.png` in one directory excluding
    `preview.png`, sorts by filename, decodes each through `ImageIO`, draws one aspect-preserving
    tile per frame in a single left-to-right row at `TILE_WIDTH` 360 with a 48 px label band, writes
    `preview.png` into the same directory, and returns the tile count. It takes **no** parameter for
    a title, caption or label: a tile's label is derived from that tile's own `Path` inside one loop
    iteration, so no workflow, page or manifest text can reach the image. Missing directory, zero
    frames, and any file that fails to decode are **fatal** — thrown, never skipped. No `System.exit`
    and no printing in this file. `./gradlew test` picks the module up with no extra wiring, and
    root-applied detekt gates it.
  - Tests: `ContactSheetTest` — `composesOneTilePerFrameInFilenameOrder`;
    `labelsAreDrawnAndNotBlank` (ink pixels > 0 inside each label band);
    `labelComesFromTheFileItDraws`; `refusesADirectoryWithNoFrames`; `refusesAnUndecodablePng`;
    `excludesAnExistingPreviewFromItsOwnInput`; `preservesAspectRatio`. Fixtures are written by the
    test through `ImageIO`, so no binary is committed.
  - Negative control 1: remove the `preview.png` exclusion —
    `excludesAnExistingPreviewFromItsOwnInput` must fail while the rest pass.
  - Negative control 2: turn the undecodable-input throw into a `continue` —
    `refusesAnUndecodablePng` must fail while every happy-path test still passes, which is the point:
    they would mask it.
  - Negative control 3: draw labels with a zero-alpha colour — `labelsAreDrawnAndNotBlank` must fail
    while every dimension and count assertion still passes.
  - Result: 8 tests, 0 failures. `bash scripts/pre-commit-check.sh` OK. All three negative controls
    behaved:
    1. exclusion removed → `excludesAnExistingPreviewFromItsOwnInput` alone failed (1 of 8);
    2. undecodable frame skipped via `mapNotNull` → `refusesAnUndecodablePng` alone failed (1 of 8),
       with every happy-path test still green, which is the point: they would have masked it;
    3. zero-alpha label ink → **2 of 8** failed, not the 1 the plan predicted.
  - Note on control 3: `labelComesFromTheFileItDraws` fails alongside `labelsAreDrawnAndNotBlank`
    because it also measures ink — it compares label bands between two runs, and with no ink drawn
    both bands read zero, so the "these must differ" assertion collapses. That is correct behaviour
    for both tests rather than a flaw in either, but the plan's "exactly one will fail" was wrong and
    is recorded here rather than quietly satisfied.
  - Deviation: `refusesAMissingDirectory` was added beyond the planned list. `Files.isDirectory`
    returns false for both an absent path and a regular file, and the empty-directory test does not
    reach that branch, so without it the "not a directory" refusal had no coverage.
  - Deviation: the test's marker map was renamed `MARKERS` → `markers`. Detekt's `VariableNaming`
    fails a non-const private `val` in PascalCase, and `warningsAsErrors` makes that fatal — the
    module is gated by the root detekt configuration exactly as intended.
  - Gate: `bash scripts/pre-commit-check.sh`

- [x] TASK-2: The CLI wrapper, and the count contract the workflow checks
  - New: `evidence-sheet/src/main/kotlin/app/webora/evidence/Main.kt`
  - New: `evidence-sheet/src/test/kotlin/app/webora/evidence/MainTest.kt`
  - Acceptance: `main` takes exactly one argument, the directory. It prints `tiles=N` on stdout and
    exits 0 on success; on a usage error or any failure from `composeContactSheet` it writes the
    reason to stderr and exits non-zero, printing no `tiles=` line. `tiles=N` is the contract
    `TASK-4` compares against `png_count`, so its format is asserted rather than assumed. The
    argument parsing and exit mapping live here and nowhere else; `ContactSheet.kt` stays free of
    process concerns.
  - Tests: `MainTest` — the success path prints exactly one `tiles=` line matching the frame count;
    zero arguments and two arguments are both usage errors; a failing compose prints no `tiles=`
    line. Driven by invoking the entry point with a redirected stdout/stderr rather than by spawning
    a process.
  - Result: 12 tests across the module, 0 failures. `bash scripts/pre-commit-check.sh` OK.
  - Result: end-to-end smoke test against three synthetic 1080×2400 frames —
    `./gradlew :evidence-sheet:run --args="<dir>"` printed exactly `tiles=3` and wrote a 1144×880
    `preview.png`. Inspected visually: three tiles in filename order, each captioned with its own
    name, aspect preserved, no distortion. The geometry the plan fixed produces an image that opens
    at a readable size, which was the point of choosing it rather than tiling at full resolution.
  - Note: `main` is a two-line shell over `runContactSheetCommand(args, out, err)`, which returns the
    exit code instead of calling `exitProcess`. That is what makes the contract testable in-process;
    spawning a JVM would have tested the Gradle `application` wiring and made the stdout assertion
    hostage to whatever else a launcher prints.
  - Note: a failed run prints **no** `tiles=` line rather than `tiles=0`. An absent count and a real
    count must not look alike to the shell comparing them, and `tiles=0` on a run that captured zero
    frames would agree with `png_count=0` and pass a check that should never have been reached.
  - Deviation: no negative control. Nothing here is a security control — the integrity rules live in
    `composeContactSheet`, and `TASK-1` carries their three controls. The tests here are ordinary
    assertions on a CLI contract, not protections that could be silently reverted.
  - Gate: `bash scripts/pre-commit-check.sh`

- [x] TASK-3: Split the staging directories in the emulator script
  - Modified: `scripts/android-screenshot-ci.sh`
  - Acceptance: canonical PNGs are copied to **`review/`** at the repo root instead of
    `artifacts/screenshots/`; `png_count` counts `review/*.png`; `mkdir -p artifacts/screenshots` is
    gone and `artifacts/` carries only diagnostics. Two disjoint directories rather than one
    directory with a negated glob, so the split cannot be broken by a YAML typo. The `CI-002`
    responsibilities in this script are untouched: the prebuilt-APK precondition, the readiness gate,
    the logcat capture, the zero-screenshot diagnosis and `exit "$test_status"` all keep their
    current behaviour and their comments.
  - Tests: `shellcheck` via the gate; `bash -n`; sourcing the script still defines its functions and
    runs nothing (the existing property at lines 88-92). Verified by inspection that no path outside
    `review/` and `artifacts/` changes, since the emulator half cannot run here.
  - Result: `bash -n` and `shellcheck` clean; sourcing the file printed nothing, defined
    `require_prebuilt_apks`, and exposed `REVIEW_DIR=review` — so the "sourcing runs nothing"
    property survives. `bash scripts/pre-commit-check.sh` OK.
  - Result: repository-wide grep for `artifacts/screenshots` leaves exactly two live references, and
    both are owned by later tasks in this ticket — `.github/workflows/android-screenshots.yml:29`
    (`TASK-4`) and `docs/SCREENSHOTS.md:85` (`TASK-5`). Recorded so the intermediate state is a known
    handover rather than something to rediscover: **between this commit and `TASK-4` the workflow
    still creates `artifacts/screenshots` and uploads one artifact**, so the pipeline is briefly
    inconsistent on the branch. It is coherent again at `TASK-4`.
  - Note: `REVIEW_DIR` is a constant rather than a literal repeated at the three sites that use it,
    because the whole integrity argument for this split is that the two directories are disjoint. One
    spelling is one place for that to be true.
  - Deviation: none.
  - Gate: `bash scripts/pre-commit-check.sh`

- [x] TASK-4: Two artifacts, the compose step, and a summary that names them
  - Modified: `.github/workflows/android-screenshots.yml`
  - Acceptance: a new step runs the composer **after the emulator step and outside it** with
    `if: always()`, then fails the run if the composer's `tiles=` disagrees with `png_count=` in
    `artifacts/result.txt`. The single upload becomes two: `webora-screenshots-${{ github.sha }}`
    from `review/` with `if-no-files-found: warn`, and
    `webora-screenshot-diagnostics-${{ github.sha }}` from `artifacts/`,
    `app/build/outputs/androidTest-results/`,
    `app/build/outputs/connected_android_test_additional_output/` and
    `app/build/reports/androidTests/` with `if-no-files-found: error`. Both retain 7 days. The
    asymmetry is deliberate and commented: a run that dies before capturing anything must still
    publish logcat. The summary states screenshot count, `GITHUB_SHA`, `GITHUB_RUN_ID` and both
    artifact names, and says plainly when no screenshots artifact was produced rather than naming one
    that is not there. No secret or environment value beyond SHA and run id is interpolated.
  - Tests: **none possible here** — this file runs only on a GitHub runner. `actionlint` if the gate
    provides it, otherwise YAML parse plus review. Recorded as reviewed-not-enforced, not as passing.
  - Result: `bash scripts/pre-commit-check.sh` OK.
  - Result: **more was verifiable than the task predicted.** The YAML parses, all 11 steps enumerate,
    every embedded `run:` block passes `bash -n`, and the two uploads carry the intended asymmetry
    (`warn` for screenshots, `error` for diagnostics). The task's "none possible here" was written
    about the file; the *logic inside it* is shell and can be extracted and driven directly.
  - Result: the compose step's comparison was exercised against four scenarios with a stubbed
    `gradlew` whose reported tile count is controlled:
    | Scenario | Behaviour |
    |---|---|
    | `tiles=3`, `png_count=3` | exit 0, prints the comparison |
    | `tiles=2`, `png_count=3` | **exit 1** — refuses to publish a sheet that does not account for every frame |
    | `png_count=0` | exit 0, nothing to compose |
    | no `result.txt` at all | exit 0, says the emulator step ended before collecting |
    The failing case is the one that matters: a composer bug that dropped a tile cannot publish.
  - Result: both summary branches were rendered to a file with `GITHUB_SHA`/`GITHUB_RUN_ID` set. The
    populated branch states commit, run, count and both artifact names; the empty branch says no
    screenshots artifact was produced and points at the diagnostics one, rather than naming an
    artifact that does not exist.
  - Note: `./gradlew … | sed` masks Gradle's exit status, which looks like a defect and is not one —
    a Gradle failure leaves `tiles` empty, and the empty-vs-`png_count` comparison then fails the
    step. The run goes red either way, so no second `PIPESTATUS` check is needed.
  - Deviation: none. What could not be verified is what genuinely needs a runner — that
    `actions/upload-artifact` produces the two bundles with the intended contents, and that the
    emulator step's outputs land where the paths expect. `/qa` records that.
  - Gate: `bash scripts/pre-commit-check.sh`

- [ ] TASK-5: Document the two artifacts as shipped
  - Modified: `docs/SCREENSHOTS.md`
  - Acceptance: the single-artifact table becomes two tables, one per artifact, naming
    `preview.png`, the three canonical PNGs at the artifact root, and every diagnostic including the
    `CI-002` `focus-*` / `interference-*` / `window-*` files and where they arrive from. The "Run it"
    numbered steps reflect downloading the screenshots artifact and opening one image. Nothing claims
    the workflow YAML is gate-verified.
  - Tests: `bash scripts/pre-commit-check.sh` (whitespace/EOF hooks); no source change.
  - Gate: `bash scripts/pre-commit-check.sh`

- [ ] TASK-6: Record the decision in the project's own documentation
  - Modified: `CLAUDE.md`, `docs/ROADMAP.md`
  - Acceptance: `CLAUDE.md` gains a `DEVX-002` section in the same register as `CI-002`'s, stating
    the label rule (a tile's caption derives only from its own filename, and the composer has no
    parameter through which page or manifest text could arrive), the two-artifact split and the
    `if-no-files-found` asymmetry behind it, and why composition is a JVM module rather than a shell
    step or an instrumented test — with the `androidTest` compile error this session found as the
    concrete reason. `docs/ROADMAP.md` ticks `DEVX-002`. No source, test, resource or build file
    changes in this task.
  - Tests: `bash scripts/pre-commit-check.sh`.
  - Gate: `bash scripts/pre-commit-check.sh`
