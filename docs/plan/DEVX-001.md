# DEVX-001: Implementation plan
Status: PLAN_APPROVED

## Overview
The browser already computes everything the inspector needs and then discards it one call later.
This ticket adds a **trace**: a bounded, in-memory, per-origin record written as discovery happens,
plus a debug-only panel that reads it. The trace is observational — installing it changes no
browsing decision — and the panel exists only in the `debug` build type, which is asserted against
compiled release output rather than against a runtime flag.

Three structural commitments carry the ticket:

1. **All pure logic lives in `src/main/java`.** The trace model, the recorder, the value-bounding
   helper and the snapshot assembly are ordinary app-layer code covered by the normal JVM gate.
   Only the Compose panel and a one-line availability constant are variant-specific, because
   `testDebugUnitTest` is the only host-test task AGP 9.1 creates for this project (research §1) —
   anything that must be *tested* has to be reachable from the debug variant.
2. **Availability comes from the variant source set, never from `BuildConfig.DEBUG`.** `debugRelease`
   is `isDebuggable = true`, so `BuildConfig.DEBUG` is true there while its source set carries the
   release stub. A single `SITESKIN_INSPECTOR_AVAILABLE` constant declared beside the panel in each
   variant's own file makes recorder and panel agree by construction.
3. **Absence is verified against compiled bytecode.** `:app:compileReleaseKotlin` writes plain class
   files and is fast and incremental (research §2). A verification-group Gradle task walks that
   output, asserts the panel class is missing **and** the stub class is present, and is wired into
   `:app:check` and `scripts/pre-commit-check.sh`.

## Flow

### discovery
`ManifestDiscoveryCoordinator.onPageStarted` gains one obligation: emit exactly one
`ManifestTraceRecord` per call, into a `SiteSkinTraceSink` supplied by the caller and defaulting to
`SiteSkinTraceSink.None`. Records are emitted on every path, including the ones that produce no
chrome:

| Path | `TraceCacheState` | transport outcome | validation |
|---|---|---|---|
| non-HTTPS or unparseable page URL | `NOT_APPLICABLE` | `NOT_ELIGIBLE` | `NOT_RUN` |
| fresh cache entry | `FRESH_HIT` | `CACHED` | re-run against the observed origin |
| `304` against a stale entry | `REVALIDATED` | `NOT_MODIFIED` | re-run |
| `200` with a body | `REFETCHED` or `MISS` | `FETCHED` | run |
| transport failure with a stale entry | `STALE_REPLAYED` | `UNAVAILABLE` | re-run |
| transport failure with no entry | `MISS` | `UNAVAILABLE` | `NOT_RUN` |
| server answered and the answer was refused | `MISS` | `REJECTED` + reason + status | `NOT_RUN` |

The last row is the one that does not exist today: `ManifestFetchResult.Rejected` is a `data object`,
so a 404, a 500, a cross-origin redirect, a redirect-limit overrun and an oversized body are one
value. It becomes a `data class` carrying the final HTTP status (nullable — a redirect-limit overrun
has one, a malformed URL does not) and a closed `FetchRejection` reason.

### validation
`SiteSkinValidator` is unchanged. The coordinator's private `validate` currently maps
`SiteSkinValidationOutcome.Rejected` to `Unavailable(origin, generation)` and drops the rejecting
diagnostics; it will return both the outcome and a `ManifestValidationTrace` holding the schema
version (accepted only), the accept/reject result, and every diagnostic as a `code`/`pointer` pair.
`SiteSkinValidationOutcome.Accepted.diagnostics` — dropped by `candidateDisposition` today — is
recorded on the accepted path, which is what explains a rendered chrome that differs from the
manifest.

### normalization
No normalization changes. The inspector *reports* normalization that already happens silently:
`SiteSkinChromeModel`'s `take(5/5/20)` truncation, `accessibleLabel`'s length bound, and
`SiteSkinTheme.guardContainer`'s contrast correction. It reports these by comparing the trusted
configuration's declared counts against the chrome model's rendered counts, and by showing the
applied colour roles beside the branding values the manifest asked for.

### UI state
`BrowserScreen` assembles an `InspectorSnapshot` from the recorded transport/validation trace plus
browser state it already holds — consent decision, activation disposition, global SiteSkin
preference, brand-asset kind, dark-theme selection — and passes it to `SiteSkinInspectorHost`. The
host is the variant seam: in `debug` it renders a floating affordance and the panel; in `release`
and `debugRelease` it is an empty function.

## Data

### trust boundary
`ManifestTraceRecord` is **display data derived from untrusted input**, and is treated as such. It
never re-enters a decision: nothing reads it except the panel. Every string in it that originated
outside the browser — diagnostic pointers, schema version, response header values, site identity,
labels — passes through `inspectorValue`, which collapses every whitespace and control character to
a single space and truncates to `SiteSkinLimits.MAX_SUBTITLE_LENGTH` read from core.

The reason is concrete and specific to this surface: `SS-W-FIELD-UNKNOWN` fires on keys the browser
does not recognise, so the pointer contains **arbitrary website text**. A manifest with a top-level
key named `"x\nHTTP status: 200"` would otherwise render a forged field row inside the browser's own
diagnostic tool. Labels stay in browser-authored `strings.xml` copy and are never concatenated with a
value; the panel renders label and value as separate `Text` nodes in a row.

### storage / cache keys
- The recorder is a `Map<String, ManifestTraceRecord>` keyed by **full canonical origin**, the same
  key `ManifestCache` and `SiteConsentStore` use. It holds at most `MAX_TRACED_ORIGINS = 8` entries,
  evicting in insertion order.
- **No manifest bytes are retained.** The record holds counts, codes, pointers and bounded strings.
- Nothing is written to disk, logged, shared or transmitted. The recorder is process-lifetime only.
- `BrowsingDataCleaner` clears it alongside the manifest cache and stored consent.

## Security

### origin binding
The record's origin is the canonical `SiteOrigin` the coordinator already derived, displayed in full
canonical form — scheme, host, and non-default port — matching `HARDEN-002`'s rule for the consent
dialog, so what the developer reads is the exact persistence and lookup key. The inspector never
derives an origin of its own, never parses a URL, and never displays a registrable domain in place of
an origin.

### allow-lists
No new website-reachable capability. The panel renders a closed set of browser-authored field labels
against bounded values; there is no free-form rendering path, no image loading, no link, and no
intent. Diagnostic codes are rendered as the enum name from `DiagnosticCode`, not as website text.

### fallback on failure
Unchanged and load-bearing: `ADR-010` still ends every failure in regular browsing. The inspector
explains the fallback and cannot prevent, retry, override or force one. There is no re-validate
button, no manifest editor, and no consent control in the panel — `PRIV-001`'s settings screen
remains the only place a decision changes.

**The invariant that keeps this true is tested directly:** for a matrix of discovery inputs, the
`ManifestDiscoveryOutcome` and the resulting `CandidateDisposition` are asserted identical with a
recording sink installed and with `SiteSkinTraceSink.None`.

## File-by-file plan

### New: `app/src/main/java/app/webora/browser/inspector/SiteSkinTrace.kt`
The record model. Split across small types so no constructor exceeds detekt's 7-parameter limit:
`ManifestTransportTrace(manifestUrl, httpStatus, redirects, outcome, rejection, cacheState)`,
`ManifestValidationTrace(schemaVersion, result, diagnostics)`,
`ManifestTraceRecord(origin, generation, transport, validation)`, plus the closed enums
`TraceTransportOutcome`, `FetchRejection`, `TraceCacheState`, `TraceValidationResult` and the
`TraceDiagnostic(code, pointer)` pair.

### New: `app/src/main/java/app/webora/browser/inspector/SiteSkinTraceSink.kt`
`fun interface SiteSkinTraceSink { fun record(record: ManifestTraceRecord) }` with a `None`
implementation that discards. `None` is the default everywhere, so a caller that forgets to install a
recorder gets no behaviour change rather than a null check.

### New: `app/src/main/java/app/webora/browser/inspector/SiteSkinTraceRecorder.kt`
Bounded per-origin store implementing the sink. Backed by `mutableStateOf` so the panel recomposes
when a record lands; it stays an ordinary class with no Compose entry points, so JUnit can drive it
directly. Exposes `latest(origin)`, `origins()`, and `clear()`.

### New: `app/src/main/java/app/webora/browser/inspector/InspectorText.kt`
`inspectorValue(raw: String?): String` — the single bounding and single-lining point for every
untrusted value the panel shows, reading its limit from `SiteSkinLimits`.

### New: `app/src/main/java/app/webora/browser/inspector/InspectorSnapshot.kt`
Pure assembly of what the panel renders: the record, the consent decision, the activation
disposition, the global preference, the applied colour roles with the dark/light selection, the brand
asset kind, the active navigation id, and declared-versus-rendered counts for navigation, quick
actions and menu. Takes the trusted `SiteSkinConfiguration` and the browser-observed page URL, so it
can call `SiteSkinChromeModel.from` and `SiteSkinTheme.from` exactly as the chrome does rather than
guessing what they produced.

### New: `app/src/debug/java/app/webora/browser/inspector/SiteSkinInspectorHost.kt`
`const val SITESKIN_INSPECTOR_AVAILABLE = true` and the `@Composable SiteSkinInspectorHost` that
renders a `WeboraFloatingActionButton` affordance and, when opened, the panel.

### New: `app/src/debug/java/app/webora/browser/inspector/SiteSkinInspectorPanel.kt`
The panel itself — the class whose absence from release output is asserted. Uses `WeboraButton` and
`stringResource` only, per the `A11Y-001` conventions the scan will now cover.

### New: `app/src/debug/res/values/strings.xml`
Debug-only browser-authored field labels. Separate from `src/main/res` so no inspector copy ships.

### New: `app/src/release/java/app/webora/browser/inspector/SiteSkinInspectorHost.kt`
`const val SITESKIN_INSPECTOR_AVAILABLE = false` and an empty `@Composable SiteSkinInspectorHost`.
Shared with `debugRelease` via a `srcDir`, not copied — research §4.

### Modified: `app/build.gradle.kts`
- `sourceSets["debugRelease"].java.srcDir("src/release/java")`, because `initWith` copies build-type
  configuration and not source sets; without this the `debugRelease` variant has no declaration and
  does not compile.
- `assertInspectorAbsentFromReleaseVariants`: a `verification`-group task consuming the
  `compileReleaseKotlin` and `compileDebugReleaseKotlin` output directories as configuration-cache-safe
  `Provider`s, failing if the panel class file is present **or** if the stub class file is absent.
  The second half is not decoration: without it, renaming the panel makes the check pass vacuously —
  the same both-directions discipline `SpecCorpusTest` applies to the diagnostics registry.
- `tasks.named("check") { dependsOn(...) }`, mirroring `:siteskin-core`'s `assertNoAndroidDependencies`.
- The `Test` configuration block passes the debug and release variant source roots in addition to
  `src/main/java`, and declares them as inputs.

### Modified: `app/src/main/java/app/webora/browser/siteskin/OkHttpManifestSource.kt`
`ManifestFetchResult.Fetched` and `NotModified` gain `httpStatus` and `redirects`; `Rejected` becomes
a `data class(httpStatus: Int?, reason: FetchRejection)`. `Unavailable` stays a `data object` — an
`IOException` or timeout genuinely has no status, and keeping "no answer" distinct from "answer
refused" is most of the diagnostic value for a site owner behind a misconfigured CDN.

### Modified: `app/src/main/java/app/webora/browser/siteskin/ManifestDiscoveryCoordinator.kt`
Takes a `SiteSkinTraceSink` defaulting to `None`. Its private steps return a `Discovered` triple of
outcome, transport trace and validation trace; `onPageStarted` records once before invoking
`onOutcome`. Cancellation is unchanged: `ensureActive()` still precedes publication, so a superseded
navigation records nothing.

### Modified: `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`
Creates the sink through a `rememberTraceSink()` helper that returns a real recorder only when
`SITESKIN_INSPECTOR_AVAILABLE`, passes it to the coordinator, and calls `SiteSkinInspectorHost` with
the assembled snapshot. The delta is deliberately small — the file already carries three detekt
complexity suppressions, so snapshot assembly lives in `InspectorSnapshot.kt`, not here.

### Modified: `app/src/main/java/app/webora/browser/privacy/BrowsingDataCleaner.kt`
Gains a `clearTrace: () -> Unit` member, wired in the `android` factory, so clearing browsing data
drops the trace with the manifest cache and consent. Six constructor parameters, within detekt's
limit of seven.

### Modified: `app/src/test/java/app/webora/browser/browser/BrowserSurfaceConventionsTest.kt`
Reads a list of source roots rather than one, so a composable in a variant source set is inside the
`A11Y-001` gate. The coverage floor rises with the added roots — a scan that silently matches nothing
passes forever, which is the failure this test was written to prevent.

### Modified: `scripts/pre-commit-check.sh`, `docs/ROADMAP.md`, `CLAUDE.md`
The gate invokes the absence check unconditionally, for the reason the script already documents about
detekt. `CLAUDE.md` gains a `DEVX-001` architecture note.

## Tests
| Test | Asserts | Negative control |
|---|---|---|
| `SiteSkinTraceRecorderTest` | per-origin latest wins; bounded at 8 origins with insertion-order eviction; `clear()` empties; no byte array is reachable from a record | — |
| `InspectorTextTest` | newline, carriage return, tab and control characters collapse to a single space; length is bounded by the core limit; a forged pointer cannot produce a second line | remove the bound → the forged-pointer case fails |
| `OkHttpManifestSourceTest` (extended) | 404 and 500 yield `Rejected` with the status; a cross-origin redirect yields `CROSS_ORIGIN_REDIRECT` with no status confusion; redirect count is reported | — |
| `ManifestDiscoveryTraceTest` | one record per `onPageStarted`; rejection diagnostics survive; accepted warnings survive; each `TraceCacheState` is produced by its own path; a cancelled navigation records nothing | — |
| `SiteSkinTraceNeutralityTest` | across the discovery matrix, `ManifestDiscoveryOutcome` and `CandidateDisposition` are identical with a recording sink and with `None` | invert a branch in the coordinator → the equality fails |
| `InspectorSnapshotTest` | declared-versus-rendered counts expose truncation; the active id is absent when `NavMatcher` matches nothing; the applied colour roles are the guarded ones; dark selection follows the passed flag, not the manifest | — |
| `BrowserSurfaceConventionsTest` | the debug panel obeys the literal, accessible-name and touch-target rules | place a literal `Text("x")` in the debug source set → the scan fails |
| `assertInspectorAbsentFromReleaseVariants` (Gradle) | the panel class is absent from both release-variant compile outputs and the stub class is present | move the panel file to `src/main/java` → the task fails |

## Rollout / versioning
No schema, spec, fixture or diagnostic-registry change; `spec/` is untouched and
`SS-W-FIELD-DEPRECATED` stays unregistered. No new dependency, no new permission, no manifest field,
no persisted state. `docs/ROADMAP.md` ticks `DEVX-001`. `DEMO-001` is the first consumer: the
inspector is the tool that will explain a Bloom Flowers integration failure on a real origin.

## Open questions
None. One decision recorded rather than deferred: the inspector ships in the `debug` build type only.
`debugRelease` exists to exercise the release configuration against a local cleartext server, and
SiteSkin requires HTTPS, so discovery does not run there — adding developer tooling to it would
weaken the absence claim for no diagnostic gain.
