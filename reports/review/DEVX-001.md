# Review: DEVX-001
Date: 2026-08-10
Status: RESOLVED

## Summary
The ticket adds a bounded per-origin trace of manifest discovery and a debug-only panel that reads
it. The trust story holds: discovery behaviour is unchanged and proved unchanged, the panel is
read-only, and its absence from the shipped variants is asserted against compiled bytecode rather
than a runtime flag.

Four findings, none of them structural. Two are in the panel itself — one puts manifest text where
the panel's own rule says only browser copy goes, and one leaves the debug affordance outside the
window insets. One is a documentation gap that `PLAY-001` would otherwise have to rediscover. One is
a trace record that nothing can ever look up.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | `:siteskin-core` untouched. The trace is an app-layer observation of a core-owned decision, and `FetchRejection` sits with the transport that knows the six cases apart rather than with the observer that displays them. Correct placement. |
| Observation vs decision | `SiteSkinTraceSink` with a discarding `None` default, threaded as a constructor parameter. No decision reads a record. `SiteSkinTraceNeutralityTest` proves it over fifteen scenarios and carries two guards against passing vacuously. |
| Variant seam | `SITESKIN_INSPECTOR_AVAILABLE` declared beside the panel in each variant's own file. The alternative — `BuildConfig.DEBUG` — is actively wrong here, because `debugRelease` is debuggable and compiles the release stub. The reasoning is recorded in both variant files, so the next person cannot "simplify" it back. |
| Complexity | `ManifestDiscoveryCoordinator` grew from 116 to ~270 lines but its branching is flat: one `when` over four transport results, each delegating to a named private step. No new detekt suppression; the file needed none. |
| `BrowserScreen` delta | Fourteen lines, and snapshot assembly stayed out of a file that already carries three complexity suppressions. |
| Recomputation | The snapshot calls `SiteSkinChromeModel.from` and `SiteSkinTheme.from` again rather than receiving their results. Recomputing is cheaper than a second copy of the answer, and a second copy is a second thing that can be stale. |

## Security

| Property | Assessment |
|---|---|
| Origin binding | The record's key is the canonical `SiteOrigin` the coordinator already derived — the same key `ManifestCache` and `SiteConsentStore` use. The panel derives no origin of its own and parses no URL. |
| Untrusted text | `inspectorValue` is the single bound: one line, `SiteSkinLimits.MAX_SUBTITLE_LENGTH` read from core, format characters stripped. A character walk rather than a regex, correctly — `\s` in `java.util.regex` matches neither `U+2028` nor `U+00A0`. |
| Label/value separation | The stated invariant, and violated once in the panel's own code. See FINDING-1. |
| No bypass | No re-validate, no override, no consent control, no retry. The panel cannot make a rejected manifest activate, and cannot change a stored decision. |
| Egress | No `Log` call, no file, no clipboard, no network, no persistence. `grep -rn "android.util.Log" app/src` returns nothing in the inspector. |
| Retention | Bounded at eight origins, no manifest bytes retained (asserted reflectively), cleared by `Clear browsing data`. |
| Release absence | Asserted against `compileReleaseKotlin` and `compileDebugReleaseKotlin` output, in both directions, with both directions verified by control. |
| New capability | No permission, no dependency, no manifest field, no diagnostic code. `spec/` untouched. |

## Findings

### FINDING-1 · Medium · label/value separation
**File:** `app/src/debug/java/app/webora/browser/inspector/SiteSkinInspectorPanel.kt:118`

The panel's rule is that labels are browser-authored and values are untrusted, and it is the reason
label and value are separate `Text` nodes. This row puts a manifest-controlled `id` in the label
slot.

`id` is constrained by the schema to `^[a-z0-9][a-z0-9_-]{0,63}$`, so nothing hostile fits through
it today. That is what makes it worth fixing rather than shrugging at: the invariant is enforceable
only while it has no exceptions, and an exception inside the tool that exists to make the trust
boundary legible is the worst place for the first one.

Current:
```kotlin
applied.navigation.forEach { item ->
    InspectorRow(inspectorValue(item.id), inspectorValue("${item.label} · ${item.actionType}"))
}
```

Fix: a browser-authored label from resources, with the id joined into the value.

### FINDING-2 · Medium · unreachable affordance
**File:** `app/src/debug/java/app/webora/browser/inspector/SiteSkinInspectorHost.kt:36`

The host's overlay `Box` fills the window with no inset handling, while every other browser surface
goes through `browserModifier`, which applies `WindowInsets.safeDrawing`. `MainActivity` calls
`enableEdgeToEdge()`, so the affordance sits under the gesture bar on a device with gesture
navigation — partly or wholly untappable, on the one surface a developer opens deliberately.

It also lands on top of SiteSkin bottom navigation, which is chrome the inspector exists to explain.

Fix: apply `windowInsetsPadding(WindowInsets.safeDrawing)` to the overlay.

### FINDING-3 · Low · documentation
**File:** `docs/privacy/DATA_SAFETY.md:29`

The implemented-local-data table is the artifact `PLAY-003` is filled in from. The developer trace
is per-origin state derived from browsing and held in memory, which is exactly the shape of the row
above it — and it is absent, because it never ships. That sentence belongs in the document rather
than in a reviewer's head.

### FINDING-4 · Low · unreachable record
**File:** `app/src/main/java/app/webora/browser/siteskin/ManifestDiscoveryCoordinator.kt:250`

`notEligible` records under `parsed?.canonical.orEmpty()`. When the page URL parses to no origin at
all, that is the empty string, and nothing can retrieve it: `BrowserState.observePage` puts the
browser in `Regular(null)`, so the panel looks up nothing and shows no record. The entry then
consumes one of the eight retention slots.

The parseable non-HTTPS case is the useful one and must stay — "SiteSkin requires HTTPS" is an
answer. The unparseable case should not be recorded at all.

## Not findings

- **`ManifestFetchResult.Fetched` is a `data class` holding a `ByteArray`**, so its `equals` is
  identity-based. Pre-existing, and the trace never compares fetch results — the neutrality test
  compares described decisions precisely because the types in play have no meaningful equality.
- **`traceVersion` is written from inside a coroutine.** That coroutine is launched on
  `rememberCoroutineScope()`, which dispatches on Main; only `source.fetch` leaves it, via its own
  `withContext(Dispatchers.IO)`.
- **The recorder is not backed by Compose state**, which looks like an omission. It is the reason
  the JVM gate can drive it directly. The version counter is the observation channel and is read as
  a `remember` key.
- **`SiteSkinChromeModel`'s 5/5/20 cap can never fire for a trusted configuration**, so
  `InspectorItemCount.diverged` should never be true. That is the point — it is a divergence
  indicator between core's normalization and the app's own cap. A separate test proves the flag can
  fire, so it is not decoration.
- **The colour field is named `trusted`, not `requested`.** Deliberate: core corrects failing colours
  during security validation, so the app layer never sees what the manifest wrote. Calling it
  `requested` would be a false claim by the tool whose job is answering that question.
- **The release stub ignores its parameter.** Its signature has to match the debug host exactly, and
  the caller already passes `null` there because the availability constant folds the assembly out.
- **The absence check reads Kotlin output rather than the final DEX or AAB.** R8 can remove classes
  and cannot add them, so the compile output is an upper bound on what ships. Checking the APK would
  require a release build in the local gate for no additional guarantee.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `InspectorText.kt` | `InspectorTextTest` (7) | Forged pointer, every separator class, bidi override, run collapsing, bound, bound-counts-displayed-characters, empty. Negative control recorded. |
| `SiteSkinTraceRecorder.kt` | `SiteSkinTraceRecorderTest` (7) | Latest-wins, absent origin, bounded LRU eviction, clear, version, no byte retention, discarding sink. |
| `OkHttpManifestSource.kt` | `OkHttpManifestSourceTest` (13) | 404/500 status, cross-origin redirect vs redirect limit, redirect count on success, oversize, non-HTTPS before request, plus the six pre-existing cases unchanged. |
| `ManifestDiscoveryCoordinator.kt` | `ManifestDiscoveryTraceTest` (7), `ManifestDiscoveryCoordinatorTest` (7) | Rejection diagnostics, accepted warnings, refused-response status, ineligible page, all five cache states, one-record-per-navigation, superseded navigation. |
| Neutrality | `SiteSkinTraceNeutralityTest` (3) | Fifteen scenarios twice; matrix size and disposition-coverage guards. Negative control recorded. |
| `InspectorSnapshot.kt` | `InspectorSnapshotTest` (10) | Already-bounded collection, divergence flag falsifiable, no-match active item, matched active item, core-corrected colour, dark selection, dark content colour, six activation states, brand asset, canonical origin. |
| `BrowsingDataCleaner.kt` | `BrowsingDataCleanerTest` (4) | Trace cleared with the manifest cache, ordering, partial failure. |
| Variant seam | `assertInspectorAbsentFromRelease{,DebugRelease}` | Panel absent and stub present, in both release variants. Both halves verified by control. |
| Conventions | `BrowserSurfaceConventionsTest` (5) | Widened to three source roots; per-root contribution asserted. Both controls recorded. |

Gaps, deliberate and consistent with `A11Y-001`: no instrumented test of the panel. The JVM gate
covers the pure decisions; rendering is recorded as QA evidence.

## Verdict
`PASS_WITH_NOTES`. All four findings landed as `TASK-FIX-1..4` before merge. FINDING-4 gained a
regression test; the other three are behavioural or documentary and are covered by the existing
suite plus the conventions scan, which now reaches the debug source set.
