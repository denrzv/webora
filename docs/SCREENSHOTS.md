# Android screenshots from GitHub Actions

Webora can exercise the deployed Bloom Flowers integration on a GitHub-hosted Pixel 6 emulator and
return full-device screenshots without a local Android phone.

## Run it

1. Open the Webora repository on GitHub.
2. Select **Actions** → **Android screenshots**.
3. Select **Run workflow**, choose the branch containing the commit to inspect, and confirm.
4. Open the resulting **Bloom Flowers on Pixel 6 API 33** job. A cold emulator normally takes
   several minutes; the job has a 40-minute timeout.
5. Read the job summary: it states the commit, the run id, how many screenshots were captured, and
   the names of both artifacts.
6. Under **Artifacts**, download `webora-screenshots-<commit-sha>` and open `preview.png`.

## Two artifacts, and which one you want

`DEVX-002` split the evidence in two. The one you normally want contains nothing but images.

**`webora-screenshots-<commit-sha>`** — for looking at:

| File | Expected evidence |
|---|---|
| `preview.png` | Every canonical frame in journey order on one sheet, each tile captioned with its own filename. Open this first. |
| `01-home.png` | Fresh Webora Home after onboarding, including Bloom Flowers. |
| `02-siteskin-consent.png` | Exact `https://denrzv.github.io` consent identity and the attributed manifest preview. |
| `03-siteskin-integrated.png` | Live page inside native SiteSkin chrome, with browser-owned security identity and navigation. |

The PNGs sit at the archive root at full capture resolution. `preview.png` is a convenience for
judging the journey at a glance, never a replacement for them — anything worth disputing should be
checked against the full-resolution frame.

**`webora-screenshot-diagnostics-<commit-sha>`** — for when something went wrong:

| File | Contents |
|---|---|
| `artifacts/run.txt` | The commit and run id the evidence belongs to. |
| `artifacts/instrumentation.txt` | Focused connected-test output. |
| `artifacts/logcat.txt` | Emulator logcat for diagnosing WebView, network, crash or ANR failures. |
| `artifacts/result.txt` | Instrumentation exit status and number of PNGs collected. |
| `artifacts/prebuilt-apks.txt` | The two APKs the emulator step found already built, with their sizes. |
| `artifacts/readiness.txt` | Every readiness sample taken after boot, with its verdict and when it settled. |
| `…/diagnostics/focus-01-home.txt` etc. | The `mCurrentFocus` lines behind each successful capture. |
| `…/diagnostics/interference-*.txt` | Present only if a System UI dialog was cleared: what it was, and what was pressed. |
| `…/diagnostics/window-*.txt` | Present only on a refused capture: the whole `dumpsys window` output at that moment. |
| `androidTest-results/`, `reports/androidTests/` | The test runner's own XML and HTML. |

The `diagnostics/` files are written through Android test storage, so they arrive under
`connected_android_test_additional_output/` rather than beside the other diagnostics.

The two upload steps are deliberately not symmetrical. Missing diagnostics fail the run
(`if-no-files-found: error`); missing screenshots only warn. A run that dies before capturing
anything is the run someone most needs to read, and it must not also fail on having no pictures to
publish.

**The contact sheet must account for every frame.** The composer is total or it throws — an
unreadable frame is never skipped — and the workflow then compares the number of tiles it drew
against the number of screenshots the run collected. A disagreement fails the run instead of
publishing the sheet, because a sheet one tile short still reads as a complete journey to whoever
opens it. Tile captions come only from the frame's own filename: nothing from the page under test or
its manifest can caption Webora's own evidence.

The workflow runs only when manually requested. It does not consume private-repository Actions
minutes on every push, does not publish screenshots as a Release, and retains both artifacts for
seven days.

## What the pipeline may clear off the screen, and what it never touches

A screenshot is supposed to show what a person would see. The first green run showed something
else: three frames covered by Android's `System UI isn't responding` dialog, with every semantic
assertion passing underneath. `CI-002` fixed the cause and then made the contaminated frame
impossible to publish.

- **The cause.** Both APKs are now built in an ordinary workflow step *before* the emulator starts.
  Previously the emulator booted and then sat through an 8½-minute Gradle build on the same 4-vCPU
  runner; ANR timers are wall-clock, so System UI was starved into one. `android-screenshot-ci.sh`
  refuses to run if either APK is missing, so that build cannot quietly move back inside.
- **Readiness.** `sys.boot_completed=1` means the boot broadcast fired, not that the device is worth
  photographing. `scripts/android-emulator-ready.sh` polls four conditions — boot broadcast, boot
  animation not running, PackageManager answering, something owning the display — and requires them
  on three consecutive samples under a deadline. Every sample lands in `readiness.txt`. This is not
  belt-and-braces: in run `31513527146` the first sample was ready, the next three read
  `mCurrentFocus=null`, and the device only settled 33 seconds in. One sample, or a short fixed
  `sleep`, would have started the journey while nothing owned the display.
- **The one dismissal.** Before each capture the test reads the focused window and refuses to
  photograph anything Webora does not own. Exactly one obstruction may be cleared:
  `Application Not Responding: com.android.systemui`, by pressing `Wait`, at most twice, and it is
  recorded in `diagnostics/interference-*.txt` when it happens.
- **What is never dismissed.** A Webora crash dialog, a Webora ANR, a System UI *crash*, an ANR in
  any other process, a permission prompt, or any window nobody could identify. Each of those fails
  the run with the observed window. The allow-list is one process name in
  `ScreenEvidencePolicy.DISMISSABLE_ANR_PROCESS`, and `ScreenEvidencePolicyTest` turns red if it is
  widened — a generic "close whatever is on top" loop would clear a Webora failure and photograph
  the screen behind it, which is precisely the outcome these screenshots exist to detect.

## What a failure means

This is deliberately a live integration test. It uses the compiled Home suggestion and Webora's
ordinary HTTPS manifest discovery; it does not substitute fixture JSON. A failure can therefore mean
the selected app commit regressed, the GitHub-hosted emulator failed, or `https://denrzv.github.io`
or its `/.well-known/siteskin.json` was temporarily unavailable. Check `instrumentation.txt` first,
then `logcat.txt`, and retry once if the evidence is clearly a transient network/emulator failure.

Since `CI-002` there are three more shapes of red, and each names itself:

| Failure | Where to look | What it means |
|---|---|---|
| `MISSING prebuilt APK` | `prebuilt-apks.txt` | The pre-build step regressed. Do not "fix" it by building inside the emulator step; that is the contention this ticket removed. |
| `The emulator never settled` | `readiness.txt` | Every sample and its verdict is there. A run of `package-manager-silent` is a slow runner; `no-focused-window` throughout is a device that never presented anything. |
| `Refusing to capture <frame>` | `diagnostics/window-<frame>.txt` | Something Webora does not own was on screen. The message names it. If it is a Webora crash or ANR, that is the product failing and the screenshot job is doing its job. |
| `Refusing to capture <frame>: the page region never rendered` | `diagnostics/rendered-<frame>.txt` | `CI-003`. The screen was Webora's, and the page area had nothing drawn in it. Every sample is recorded with its differing fraction, modal colour and elapsed time — a run of near-zero fractions is a page that never started drawing, a rising series is one that ran out of time. |

**`rendered-<frame>.txt` is written on success too**, carrying the fraction that passed and how long
it took. A passing check that records nothing cannot be told apart from one that barely passed for
the wrong reason, which is exactly how a blank frame survived run 10: browser-owned chrome inside the
measured region cleared the threshold and there was no measurement to notice it by.

Screenshots are written through AndroidX `PlatformTestStorage`, so the Android Gradle Plugin copies
them from the device into its connected-test additional-output directory before test teardown removes
app-specific storage. The CI helper then copies the three PNGs into `review/`, which becomes the
screenshots artifact. Diagnostics stage separately in `artifacts/`; the two directories share no
ancestor, so neither can leak into the other through an upload path expression.

The job fails if no PNG is collected, even when Gradle happened to return success. It uploads
whatever diagnostics exist with `if: always()`, so a red job should still have a diagnostics artifact
unless the runner failed before artifact upload itself.

**A stale artifact looks exactly like a current one.** Artifact names carry the commit SHA for
precisely this reason: `webora-screenshots-<sha>` is evidence about *that* commit and nothing else.
Before concluding that a defect is still present, check the SHA on the artifact you opened against
the commit you mean to judge — the archive keeps seven days of runs, and the older ones predate
whatever has been fixed since.

## Why this uses the debug APK

The screenshot journey needs instrumentation and the SiteSkin Integration Inspector is useful for
diagnosis. It therefore installs the repository's pinned-debug-key `app.webora.browser.debug` build,
not the separately signed production release variant and not the cleartext-enabled `debugRelease`
local-testing variant. The user-visible SiteSkin, consent, HTTPS and origin-boundary paths remain the
production implementations.
