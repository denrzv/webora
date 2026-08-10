# QA Report: DEVX-001
Status: QA_PASSED

## Scope
The SiteSkin Integration Inspector: a bounded per-origin trace of manifest discovery recorded as it
happens, and a debug-only panel that reads it. Verified here: that recording changes no browsing
decision, that the data a developer needs actually survives the pipeline, that untrusted text cannot
imitate the panel's own copy, and that none of it reaches a shipped variant.

Method note, and the reason one scenario below is a Gradle task rather than a test: AGP 9.1 creates
`testDebugUnitTest` and no other host-test task, so no JUnit run can ever execute against a release
variant. Enabling host tests for `release` was attempted and fails inside AGP with a
`NullPointerException` at `VariantManager.createTestComponents`. The absence claim is therefore
asserted against compiled bytecode.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Recording changes no discovery outcome and no activation disposition | `SiteSkinTraceNeutralityTest` — 15 scenarios run twice, recording and discarding | PASS. Negative control: a `discover` branch taken only when a sink was installed failed the case. |
| 2 | The matrix that neutrality is proved over is not vacuous | `SiteSkinTraceNeutralityTest` — size floor, and coverage of Activate / Ask / Ignore | PASS |
| 3 | A rejection's diagnostics survive, which they did not before | `ManifestDiscoveryTraceTest` | PASS — `SS-E-SCHEMA-INVALID` reaches the record |
| 4 | An accepted manifest's warnings survive | `ManifestDiscoveryTraceTest` | PASS — `SS-W-FIELD-UNKNOWN` reaches the record with its pointer |
| 5 | A refused response reports its HTTP status and the reason | `ManifestDiscoveryTraceTest`, `OkHttpManifestSourceTest` | PASS — 404/500 statuses, cross-origin redirect and redirect limit told apart |
| 6 | Every `NET-002` cache path names itself | `ManifestDiscoveryTraceTest` | PASS — MISS, FRESH_HIT, REVALIDATED, REFETCHED, STALE_REPLAYED |
| 7 | Exactly one record per navigation | `ManifestDiscoveryTraceTest` | PASS |
| 8 | A superseded navigation records nothing | `ManifestDiscoveryTraceTest` | PASS — the record is written after `ensureActive()` |
| 9 | A forged diagnostic pointer cannot draw a row in the panel | `InspectorTextTest` | PASS. Negative control: removing the bound failed all seven cases. |
| 10 | Separators a regex misses are still collapsed | `InspectorTextTest` | PASS — tab, CR, LF, and runs |
| 11 | A bidi override cannot reverse a value into something that reads like a label | `InspectorTextTest` | PASS |
| 12 | Untrusted values are bounded by the core limit, counting displayed characters | `InspectorTextTest` | PASS |
| 13 | Trace retention is bounded and evicts least-recently-recorded | `SiteSkinTraceRecorderTest` | PASS at 8 origins |
| 14 | A trace record retains no manifest bytes | `SiteSkinTraceRecorderTest` — reflective field scan | PASS |
| 15 | Clear browsing data drops the trace with the manifest cache and consent | `BrowsingDataCleanerTest` | PASS, including the partial-failure path |
| 16 | The panel reports the active navigation item, or its absence | `InspectorSnapshotTest` | PASS — no match yields no id, never the first item |
| 17 | The dark/light projection follows the browser flag, not the manifest | `InspectorSnapshotTest` | PASS |
| 18 | Six activation states are told apart | `InspectorSnapshotTest` | PASS — DISABLED, INTEGRATED, PENDING, UNAVAILABLE, AWAITING_CONSENT, REFUSED |
| 19 | The panel is absent from the `release` variant | `:app:assertInspectorAbsentFromRelease` | PASS. Negative control: a compilable panel class in `src/main` failed it by name. |
| 20 | The panel is absent from the `debugRelease` variant | `:app:assertInspectorAbsentFromDebugRelease` | PASS, same control |
| 21 | The absence check cannot pass vacuously | Same tasks, stub-presence half | PASS. Negative control: renaming the seam file failed both with "about to pass without proving anything". |
| 22 | The debug panel is inside the accessibility conventions gate | `BrowserSurfaceConventionsTest` over three source roots | PASS. Negative control: `Text("debug")` in the panel failed the literal rule. |
| 23 | A source root that stops contributing fails the scan | `BrowserSurfaceConventionsTest` | PASS. Negative control: a non-existent root failed at the `require`. |
| 24 | Every variant still builds | `:app:assembleDebug`, `:app:assembleDebugRelease`, `:app:compileReleaseKotlin` | PASS |
| 25 | No new logging, network, permission or dependency | Source grep for `android.util.Log`; `app/build.gradle.kts` diff | PASS — no logging call in the inspector, no dependency added |

`./gradlew test` — 151 app unit tests, 0 failures. `:siteskin-core:test`, `detekt`, `gitleaks` and
`shellcheck` green via `bash scripts/pre-commit-check.sh`.

## Edge cases

- **invalid manifest → regular browser mode.** Unchanged and proved unchanged. A rejected manifest
  still yields `ManifestDiscoveryOutcome.Unavailable` and `CandidateDisposition.Ignore`; scenario 1
  asserts the outcome and disposition are identical with and without recording. The only difference
  is that the browser can now say *why*, which is the ticket.
- **origin change / redirect.** Scenario 8: a navigation superseded before `ensureActive()` records
  nothing, so the panel never shows a trace for an origin the browser has left. Redirects are
  recorded as a count, and a cross-origin redirect is reported as `CROSS_ORIGIN_REDIRECT` distinct
  from the hop limit (scenario 5). The trace is keyed by full canonical origin, so it cannot be read
  across ports, schemes, hosts or subdomains.
- **offline with cached manifest.** Scenario 6 covers `STALE_REPLAYED` — transport unavailable with
  a stale entry still validates the cached bytes for the observed origin and now reports that this
  is what happened. `UNAVAILABLE` with no entry is distinct from `REJECTED`, which is the
  distinction a site owner behind a broken CDN needs.
- **oversized or malformed payload.** `OkHttpManifestSourceTest` covers an oversized body reported
  as `OVERSIZED` at status 200 rather than as an HTTP error, and a malformed URL as `MALFORMED_URL`.
  A body that parses but fails validation is scenario 3. No limit changed.
- **accessibility (TalkBack, font scale).** The panel is browser-owned UI and is now inside the
  `A11Y-001` gate (scenario 22): all copy from resources, `WeboraButton` and
  `WeboraFloatingActionButton` for controls, `FlowRow` rows so a long value wraps below its label
  instead of clipping, `verticalScroll` on the body, and `heading()` semantics on each section.
  `TASK-FIX-2` added the `safeDrawing` inset the affordance was missing. Not instrumented — the
  panel has no on-device test, consistent with `A11Y-001`'s rule that the JVM gate covers pure
  decisions and rendering is recorded as evidence. It is debug-only UI, so this is not a shipped
  accessibility surface.

## Result
Status: QA_PASSED
Notes: Five negative controls were run and recorded, one of which — moving the panel into
`src/main` — revealed a second, unplanned layer of the same guarantee: the panel does not compile
outside the debug variant at all, because its copy lives in `src/debug/res`. The control was redone
with a compilable probe class so that it actually exercised the check.

No instrumented test of the panel, deliberately. No release-variant JUnit run is possible in this
project, which is why scenarios 19–21 are Gradle tasks; both directions of those checks were
verified by control rather than assumed.
