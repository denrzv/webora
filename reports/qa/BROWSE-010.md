# QA Report: BROWSE-010
Status: QA_PASSED

Issue: [#106](https://github.com/denrzv/webora/issues/106)

## Scope

The mount-time decision only: whether a retained renderer is issued its tab's committed URL when its
Compose host is (re)created. `BrowserSession`, `BrowserState`, `RendererOwnership`'s router,
`BrowserBack`, `applyWebViewHardening` and every `destroy()` call site are untouched and were
verified only as *unchanged*.

Verified on `0d0f0a6`, branch `claude/bloom-flowers-reference-y5ybs7`, based on `origin/main`
`8e83ac7`.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Full pre-commit gate | `bash scripts/pre-commit-check.sh` | PASS — gitleaks, shellcheck, 23 readiness checks, `:siteskin-core:test` with no Android SDK, unit tests, inspector-absence, detekt |
| 2 | App unit suite | `./gradlew :app:testDebugUnitTest` | PASS — 377 run, 0 failed |
| 3 | Instrumented sources compile | `./gradlew :app:compileDebugAndroidTestKotlin` | PASS — run explicitly; the gate never compiles these |
| 4 | A fresh renderer loads | `RendererMountActionTest` | PASS — the behaviour `existing == null` provided, preserved after its removal |
| 5 | Home → different address loads | `RendererMountActionTest` | PASS — the reported defect |
| 6 | Home → the *same* address loads | `RendererMountActionTest` | PASS — the tab is waiting; it gets a real page, not a fabricated completion |
| 7 | Tab switch to a loaded page is silent | `RendererMountActionTest` | PASS — no reload |
| 8 | Tab switch after in-page link navigation is silent | `RendererMountActionTest` | PASS — the record is maintained from reports, not only requests |
| 9 | Tab switch to a failed tab is silent | `RendererMountActionTest` | PASS — failure does not move the record |
| 10 | Re-evaluating after a load is silent | `RendererMountActionTest` | PASS — the oscillation guard |
| 11 | A tab with no committed URL requests nothing | `RendererMountActionTest` | PASS |
| 12 | A destroyed renderer forgets its page | `RendererMountActionTest` | PASS |
| 13 | The host asks the decision, not `existing == null` | `RendererMountContractTest` | PASS, with counter-example |
| 14 | A failed navigation does not write the record | `RendererMountContractTest` | PASS |
| 15 | All three reporting callbacks write the record | `RendererMountContractTest` | PASS |
| 16 | No page-authored type reaches the decision | `RendererMountContractTest` | PASS — executable lines only |
| 17 | `BROWSE-009` renderer ownership intact | `browser/RendererHostContractTest`, `RendererOwnershipTest` | PASS — `EMITTED_EVENTS` back at 4 after `TASK-FIX-1` |
| 18 | `main`'s pre-existing red repaired | `ExpressiveBloomJourneyContractTest` | PASS — confirmed failing first on a clean worktree of `origin/main` |
| 19 | Negative control: restore `if (existing == null)` | edit, run, restore | FAILED as required — `the host asks the decision…` |
| 20 | Negative control: drop `observed()` from `onPageChanged` | edit, run, restore | FAILED as required — `every reporting callback records…`. **Did not** fail the pure in-page case, contrary to the plan's prediction; see notes |
| 21 | Negative control: write the record on `MainFrameFailed` | edit, run, restore | FAILED as required — `a failed navigation does not move the hosted url` |
| 22 | Negative control: rename the repaired showcase marker's helper | edit, run, restore | FAILED as required — the marker is load-bearing, not a coincidental spelling |
| 23 | Page → Home → new address on a device | manual | **Not executed** — no emulator in this checkout |
| 24 | `TabRendererIsolationTest` on a device | instrumented | Compiles, unedited; **not executed** — no emulator |

## Edge cases

- **invalid manifest → regular browser mode** — N/A. The mount decision runs before and independently
  of discovery; an invalid manifest changes a tab's `BrowserMode`, never its `displayedUrl`, which is
  the only tab value this reads. Discovery still follows the resulting `PageStarted` exactly as for
  any other navigation.
- **origin change / redirect** — Covered, and it is why the record is written from *reports* as well
  as requests. A redirect makes the renderer report a URL the browser never asked for; `observed()`
  records it, so the next mount compares against where the renderer actually is. Without that half a
  redirected page reloads on every tab switch.
- **offline with cached manifest** — N/A. No network, cache or manifest path is touched. The one new
  behaviour is issuing a URL the browser had already decided on.
- **oversized or malformed payload** — N/A. No remote payload enters this decision. `target` is
  `AddressResolver`'s bounded output and `hosted` is a URL the browser wrote.
- **accessibility (TalkBack, font scale)** — Partially verified, and materially improved. `A11Y-001`'s
  `browserAnnouncement` derives a polite completion announcement from `isLoading`; a flag that never
  cleared made that announcement permanently wrong for the affected tab. Terminating it is what makes
  it honest. No UI, string, target or semantics node changed, so the conventions scan is unaffected —
  verified by the full gate. **Not verified on a device:** the actual announcement.
- **mid-load tab switch** — Behaviour change, deliberate and recorded. A tab switched away from while
  loading and back has `isLoading == true`, so the mount re-issues the load rather than joining the
  one in flight. Accepted in review: the browser cannot distinguish "waiting with nothing in flight"
  — the terminal state this ticket removes — from "waiting with a live load" without reading
  `WebView.getProgress()`, which is the framework dependency the decision exists to avoid. An honest
  extra request beats the fabricated completion the first implementation used.
- **Back after a Home round trip** — Deliberately unchanged, and filed as `BROWSE-012`. `loadUrl`
  appends, so the renderer still holds the pre-Home entries and Back from the new page can reach one
  the tab's state has forgotten. PRD criterion 6 required an explicit decision, not a change.

## Result

Status: QA_PASSED

Notes:

- Every locally available gate is green, and green here means more than it did on the base commit:
  `origin/main` at `8e83ac7` failed `ExpressiveBloomJourneyContractTest` on a pristine worktree
  before any change of this ticket's. That repair was carried here on an explicit decision and has
  its own negative control.
- **The defect itself is not verified on a device.** Scenarios 23–24 are Unknown. The decision is
  proven in the JVM gate; that the framework then issues the load, that the page paints, and that
  `TabRendererIsolationTest` still passes are instrumented facts this checkout cannot produce.
  `NET-004` is the standing reason to say so rather than to imply a run happened — the same posture
  `CI-002` through `CI-005` each recorded.
- Scenario 20 is kept because the prediction was wrong and the outcome was not: the pure test proves
  the decision handles an in-page URL, the source scan proves the wiring producing one still exists,
  and neither layer covers that row alone.
