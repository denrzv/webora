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

The workflow runs only when manually requested. It does not consume private-repository Actions
minutes on every push, does not publish screenshots as a Release, and retains its artifact for seven
days.

## What a failure means

This is deliberately a live integration test. It uses the compiled Home suggestion and Webora's
ordinary HTTPS manifest discovery; it does not substitute fixture JSON. A failure can therefore mean
the selected app commit regressed, the GitHub-hosted emulator failed, or `https://denrzv.github.io`
or its `/.well-known/siteskin.json` was temporarily unavailable. Check `instrumentation.txt` first,
then `logcat.txt`, and retry once if the evidence is clearly a transient network/emulator failure.

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
