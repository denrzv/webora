# Android screenshots from GitHub Actions

Webora exercises the deployed Bloom Flowers integration on a GitHub-hosted Pixel 6 emulator and
returns a concise visual showcase without giving up the deeper live-site regression journey.

The hosted run has two layers:

1. `LiveSiteScreenshotTest` publishes six canonical screenshots for human review.
2. `LiveSiteNavigationSmokeTest` runs afterwards in the same emulator and exercises product
   navigation, browser Back/Forward, the native SiteSkin action bouquet and exact-origin teardown.
   It deliberately publishes no screenshots.

`LiveSiteHostedSuite` owns their order: showcase first, smoke second. A smoke failure therefore makes
the workflow red without turning the screenshot artifact into a transcript of every diagnostic step.

## Run it

1. Open the Webora repository on GitHub.
2. Select **Actions** → **Android screenshots**.
3. Select **Run workflow**, choose the branch containing the commit to inspect, and confirm.
4. Open **Bloom Flowers on Pixel 6 API 33**. A cold emulator normally takes several minutes; the job
   has a 40-minute timeout.
5. Read the job summary for the commit, run id and screenshot count.
6. Download `webora-screenshots-<commit-sha>` and open `preview.png` first.

## Canonical six-frame showcase

The screenshots artifact contains only the contact sheet plus these full-resolution frames:

| File | Expected evidence |
|---|---|
| `preview.png` | All six canonical frames in filename order, each captioned with its own filename. |
| `01-home.png` | Native Webora Home before entering the reference integration. |
| `02-siteskin-consent.png` | Explicit consent for the complete `https://denrzv.github.io` origin. |
| `03-bloom-storefront.png` | Live Bloom storefront inside integrated browser-owned identity/header/dock chrome. |
| `04-bloom-actions.png` | The central branded flower/action control opened, with Home, Catalog, Cart, Profile and Call visible as native SiteSkin actions. |
| `05-bloom-profile.png` | Profile selected from the action bouquet and the Bloom Account page rendered while integrated chrome remains active. |
| `06-google-regular.png` | Ordinary Google browsing under regular Webora security/navigation chrome, with every SiteSkin layer absent. |

The Google frame is deliberately judged only through browser-owned state. The test enters
`https://www.google.com/ncr`, requires Webora's secure `google.com` identity and regular navigation
shell, and requires integrated SiteSkin chrome to be absent. Google page text, layout, locale,
consent UI and other remote content never authorize the frame.

Every WebView frame still passes the existing full-device ownership and rendered-page guards. The
native SiteSkin action bouquet is excluded from the rendered-page pixel threshold, so opening the
browser-owned overlay cannot make a blank WebView pass.

## Deep navigation smoke coverage

After the six screenshots have been captured, `LiveSiteNavigationSmokeTest` starts from browser-owned
Home and reopens the deployed Bloom integration. Consent may already be persisted from the showcase;
the smoke accepts the dialog only when it is actually present.

The smoke then verifies, without writing screenshot files:

- the below-fold clickable `Happy Days` storefront link can be reached by bounded user-equivalent
  scrolling;
- the Happy Days product route opens under integrated chrome;
- displayed/enabled browser-owned Back restores the storefront;
- displayed/enabled browser-owned Forward restores the product route;
- the central brand control opens the native action bouquet and exposes Home, Catalog, Cart, Profile
  and Call;
- browser-owned navigation can return the active tab to native Webora Home;
- ordinary `example.com` browsing restores regular security/navigation chrome and removes all
  SiteSkin layers.

This split is intentional: the screenshots tell the product story, while the smoke test keeps the
more diagnostic history/mode-transition regression coverage that previously required extra frames.

## Evidence and diagnostics artifacts

`webora-screenshots-<commit-sha>` is for visual review. It contains only `preview.png` and the six
full-resolution frames.

`webora-screenshot-diagnostics-<commit-sha>` is for investigation. Important entries include:

| File | Contents |
|---|---|
| `artifacts/run.txt` | Commit and workflow run id. |
| `artifacts/instrumentation.txt` | Output for the complete hosted suite: showcase plus smoke. |
| `artifacts/logcat.txt` | Emulator logcat for WebView/network/crash/ANR diagnosis. |
| `artifacts/result.txt` | Instrumentation exit status and number of PNGs collected. |
| `artifacts/prebuilt-apks.txt` | Prebuilt app/test APK paths and sizes. |
| `artifacts/readiness.txt` | Every emulator readiness sample and the settle verdict. |
| `…/diagnostics/focus-*.txt` | Focus evidence behind each successful screenshot. |
| `…/diagnostics/rendered-*.txt` | Rendered-page measurements, written on success as well as refusal. |
| `…/diagnostics/interference-*.txt` | Recorded System UI ANR dismissal, when the narrow allow-list applies. |
| `…/diagnostics/window-*.txt` | Full window state for a refused capture. |
| `androidTest-results/`, `reports/androidTests/` | Android test runner XML/HTML, including smoke failures. |

Missing diagnostics fail artifact upload; missing screenshots only warn so that a run which dies before
its first frame can still publish the information needed to understand why.

## Contact-sheet integrity

The composer is total or it throws. It never silently skips an unreadable PNG, and an existing stale
`preview.png` is not treated as an input frame. The workflow compares the actual PNG count with the
number of tiles the composer reports and refuses publication when they disagree.

For the current canonical story a complete accepted run therefore reports:

```text
test_status=0
png_count=6
```

and the composer reports:

```text
tiles=6
```

A green six-frame contact sheet alone is not sufficient: `test_status=0` also means the separate
navigation smoke test completed successfully.

## Emulator and capture safety

Both APKs are built before the emulator launches. `scripts/android-screenshot-ci.sh` refuses to run
when either prebuilt APK is missing, preventing Gradle compilation from moving back into the live
emulator window and recreating the host-contention failure that originally produced System UI ANRs.

`sys.boot_completed=1` is not sufficient readiness. `scripts/android-emulator-ready.sh` also checks
boot animation, PackageManager, focused-window ownership and aggregate guest CPU quietness, requiring
three consecutive ready samples. Every sample is stored in `artifacts/readiness.txt`.

Before every frame, `ScreenEvidenceGuard` requires Webora to own the focused screen. Its only narrowly
allowed interference action is waiting through a System UI ANR; Webora crashes/ANRs, System UI crashes,
permission prompts and unknown foreground windows fail closed and preserve diagnostics instead of
being dismissed for the sake of a green screenshot.

Every WebView screenshot also requires measured page pixels in the renderer rectangle. Browser-owned
overlays inside that rectangle are excluded from the measurement so chrome cannot make an empty page
look rendered.

## What a failure means

This remains a live cross-repository integration test. A red run can mean the selected Webora commit
regressed, the deployed Bloom reference site changed, Google/example.com could not be reached, or the
hosted emulator itself failed. Read `artifacts/instrumentation.txt` first, then readiness/logcat and the
frame-specific diagnostics.

A failure after six screenshots may be a **smoke-test failure**, not a visual-capture failure. That is
expected under the split: the six showcase frames are retained for review, while the overall workflow
stays red because deeper product/history/handoff behavior did not satisfy its contract.

Artifacts are evidence for exactly the commit SHA in their name and are retained for seven days.
