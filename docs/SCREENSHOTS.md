# Android screenshots from GitHub Actions

Webora can exercise the deployed Bloom Flowers integration on a GitHub-hosted Pixel 6 emulator and
return full-device screenshots without a local Android phone.

## Run it

1. Open the Webora repository on GitHub.
2. Select **Actions** → **Android screenshots**.
3. Select **Run workflow**, choose the branch containing the commit to inspect, and confirm.
4. Open the resulting **Bloom Flowers on Pixel 6 API 33** job. A cold emulator normally takes
   several minutes; the job has a 40-minute timeout.
5. Under **Artifacts**, download `webora-screenshots-api33-<commit-sha>`.

The ZIP contains:

| File | Expected evidence |
|---|---|
| `screenshots/01-home.png` | Fresh Webora Home after onboarding, including Bloom Flowers. |
| `screenshots/02-siteskin-consent.png` | Exact `https://denrzv.github.io` consent identity and the attributed manifest preview. |
| `screenshots/03-siteskin-integrated.png` | Live page inside native SiteSkin chrome, with browser-owned security identity and navigation. |
| `instrumentation.txt` | Focused connected-test output. |
| `logcat.txt` | Emulator logcat for diagnosing WebView, network, crash or ANR failures. |
| `result.txt` | Instrumentation exit status and number of PNGs collected. |
| `prebuilt-apks.txt` | The two APKs the emulator step found already built, with their sizes. |
| `readiness.txt` | Every readiness sample taken after boot, with its verdict and when it settled. |
| `…/diagnostics/focus-01-home.txt` etc. | The `mCurrentFocus` lines behind each successful capture. |
| `…/diagnostics/interference-*.txt` | Present only if a System UI dialog was cleared: what it was, and what was pressed. |
| `…/diagnostics/window-*.txt` | Present only on a refused capture: the whole `dumpsys window` output at that moment. |

The `diagnostics/` files are written through Android test storage, so they arrive under
`connected_android_test_additional_output/` rather than beside `screenshots/`.

The workflow runs only when manually requested. It does not consume private-repository Actions
minutes on every push, does not publish screenshots as a Release, and retains its artifact for seven
days.

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
  animation exit, PackageManager answering, something owning the display — and requires them on
  three consecutive samples under a deadline. Every sample lands in `readiness.txt`.
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

Screenshots are written through AndroidX `PlatformTestStorage`, so the Android Gradle Plugin copies
them from the device into its connected-test additional-output directory before test teardown removes
app-specific storage. The CI helper then copies the three PNGs into `artifacts/screenshots/`.

The job fails if no PNG is collected, even when Gradle happened to return success. It uploads
whatever diagnostics exist with `if: always()`, so a red job should still have an artifact unless
the runner failed before artifact upload itself.

## Why this uses the debug APK

The screenshot journey needs instrumentation and the SiteSkin Integration Inspector is useful for
diagnosis. It therefore installs the repository's pinned-debug-key `app.webora.browser.debug` build,
not the separately signed production release variant and not the cleartext-enabled `debugRelease`
local-testing variant. The user-visible SiteSkin, consent, HTTPS and origin-boundary paths remain the
production implementations.
