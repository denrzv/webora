# DEVX-001: Research
Status: RESEARCH_READY

## Question
Three things the plan cannot decide without a map:

1. Where in the existing pipeline is the inspector's data computed, and where is it discarded?
2. What is the mechanism by which the inspector is *absent* from a release build, given that
   `./gradlew test` in this project can only ever execute the debug variant?
3. What does the inspector display that is website-controlled, and what bounds it?

## Origins involved
- **Serving origin** — the canonical HTTPS `SiteOrigin` from the committed main-frame page URL.
  `ManifestDiscoveryCoordinator.onPageStarted` parses it and drops anything whose scheme is not
  `https`. It is the only origin the inspector reports on, and the inspector reports the *canonical*
  form (scheme, host, non-default port), matching `HARDEN-002`'s rule for the consent dialog.
- **Manifest origin** — the same origin by construction. `OkHttpManifestSource` builds the request
  from `siteOrigin.canonical` plus `SiteSkinSchema.WELL_KNOWN_PATH` and follows at most
  `SiteSkinLimits.MAX_REDIRECTS` redirects, each compared for full canonical-origin equality in
  `redirectTarget`. A cross-origin redirect is `ManifestFetchResult.Rejected`.
- **Asset origin** — the logo, re-checked against the configuration's origin by `BrandAssetLoader`
  (`NET-003`). The inspector reports the *outcome* (decoded bitmap versus generated monogram); it
  never re-fetches, and it never renders a remote URL as an image source.
- No new origin is introduced. The inspector performs no network work of its own.

## Manifest-controlled surface
What a website can influence *inside the inspector*, if this ships as scoped:

| Value | Source | Already bounded by |
|---|---|---|
| `schemaVersion` | trusted configuration | schema pattern, `1.x` major only |
| site `id`, `name`, `shortName` | trusted configuration | `SecurityValidator` normalization |
| navigation/menu/quick-action `id` and `label` | trusted configuration | `SiteSkinLimits.MAX_LABEL_LENGTH` |
| `match` patterns | trusted configuration | schema `^/([^/].*)?$` |
| action `type`, `url`, `value` | trusted configuration | scheme allow-list, origin binding |
| branding colour strings | trusted configuration | `#rgb`/`#rrggbb` pattern, then the contrast guard |
| diagnostic JSON pointers | `ManifestDiagnostic.pointer` | derived from the document's own key paths |
| `ETag`, `Last-Modified`, `Cache-Control` | HTTP response headers | nothing today |

Two of those are sharper than they look.

**Diagnostic pointers are attacker-influenced.** `SiteSkinValidator` builds a pointer by rewriting a
parser path (`$.a.b[0]` → `/a/b/0`), and `SS-W-FIELD-UNKNOWN` fires on *unknown* keys — which means
the key itself is arbitrary website text. A manifest containing a top-level key named
`"x\nHTTP status: 200"` produces a pointer that, rendered naively, forges a second field row in the
inspector. This is the `ADR-006` impersonation problem relocated: the inspector's field labels are
browser-authored claims, and untrusted text must never be able to look like one.

**Response header values are completely unvalidated.** `ManifestCacheMetadata` stores whatever the
server sent. `cacheTtlMillis` interprets `Cache-Control` but never bounds its length, and `ETag` is
passed through verbatim. If the inspector shows them, they need the same bound and the same
single-line reduction as manifest text.

## Browser-owned remainder
- **The decision itself.** The inspector observes; it never feeds back. `candidateDisposition`,
  `ManifestCache` freshness, `SiteConsentStore`, and `NavMatcher` must produce identical results
  whether or not a recorder is installed. This is testable directly and is the ticket's central
  invariant.
- **Field labels and structural copy** in the panel, from `strings.xml`, never concatenated with a
  manifest value.
- **Availability.** Which build types contain the panel is a build-file fact, not a runtime flag.
- **Absence of a bypass.** No re-validate, no force-accept, no manifest override, no consent edit
  from the panel. `PRIV-001`'s settings screen remains the only place a decision is changed.
- **Zero egress.** No `Log`, no file, no clipboard, no network. `PRIV-001` has no tooling exemption.

## Relevant code

| Path | Why it matters |
|---|---|
| `app/.../siteskin/OkHttpManifestSource.kt` | `readResponse` collapses every non-success code into `ManifestFetchResult.Rejected`, a `data object` with no status. `fetchFollowingRedirects` counts redirects locally and discards the count. Both are inspector inputs that do not currently exist. |
| `app/.../siteskin/ManifestDiscoveryCoordinator.kt` | `validate()` receives `SiteSkinValidationOutcome.Rejected` — which carries the rejecting diagnostics — and returns `Unavailable(origin, generation)`, dropping them. `discover()` is the one place that knows which of fresh-cache / `304` / fetch / stale-replay happened. |
| `app/.../siteskin/ManifestCache.kt` | Holds `storedAtMillis`, `ttlMillis`, `ETag`, `Last-Modified` and the origin→active-key index. `isFresh` is the freshness answer the inspector reports. |
| `app/.../siteskin/SiteSkinRuntime.kt` | `candidateDisposition` keeps `validation.configuration` and drops `validation.diagnostics`, so accepted-with-warnings is indistinguishable from accepted-clean downstream. `CandidateDisposition.Ignore` is returned for five distinct reasons that the inspector needs to tell apart. |
| `app/.../browser/BrowserScreen.kt` | Owns `generation`, consent lookup, `siteSkinEnabled`, `brandAsset`, and the `SiteSkinTheme.from(...).scheme(isSystemInDarkTheme())` call. It is the only place with the whole picture, so it is where the trace is assembled and where the panel is hosted. |
| `app/.../siteskin/SiteSkinChromeModel.kt` | `take(5/5/20)` and `accessibleLabel` are the silent truncations a developer needs told about; `NavMatcher.activeItem(...)?.id` is the active-item answer. |
| `app/.../siteskin/SiteSkinTheme.kt` | `guardContainer` silently corrects a failing colour. The applied roles are what the inspector should show, beside the requested values. |
| `app/.../browser/BrowserAccessibility.kt` | `WeboraButton`, `MINIMUM_TOUCH_TARGET`. `internal`, so the debug source set can use them — the debug variant compiles `main` and `debug` into one module. |
| `app/src/test/.../browser/BrowserSurfaceConventionsTest.kt` | Scans `System.getProperty("webora.app.src")`, wired in `app/build.gradle.kts` to `src/main/java` only. A composable in `src/debug/java` is outside the gate today. |
| `app/build.gradle.kts` | Build types `debug`, `release`, `debugRelease`. `debugRelease` does `initWith(release)` — which copies build-type *configuration*, not source sets, so it has its own empty `src/debugRelease/` and would not see a stub placed in `src/release/java`. |
| `app/src/debug/` | Precedent for debug-only code: `BrowserRecoveryTestActivity` plus a debug `AndroidManifest.xml` merging it in. |
| `siteskin-core/build.gradle.kts` | Precedent for the enforcement shape this ticket needs: `assertNoAndroidDependencies` is a `verification`-group task wired into `check`, reading a configuration-cache-safe `Provider`. |

## Findings that change the plan

### 1. `./gradlew test` cannot assert release behaviour — measured, not assumed
`./gradlew :app:tasks --all` lists exactly one host-test task: `testDebugUnitTest`. AGP 9.1 creates
unit-test components only for the `testBuildType`. Forcing them on was probed directly:

```kotlin
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.hostTests.forEach { (_, host) -> host.enable = true }
    }
}
```

fails during configuration with `java.lang.NullPointerException` at
`VariantManager.createTestComponents(VariantManager.kt:574)`. The probe was reverted.

**Consequence:** a JUnit assertion of the form "the panel class is not loadable in the release
variant" can never execute, because no release JUnit run exists. Criterion 3 of the PRD must be met
by a mechanism that runs where the release variant *is* built.

### 2. There is a real bytecode-level assertion available, and it is fast
`:app:compileReleaseKotlin` writes plain class files to
`app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/`, verified present
on disk. It does not involve R8, dexing, or signing, and it is incremental — a re-run with no source
change completed in under a second. A `verification`-group Gradle task depending on that compilation
and walking its output for the inspector panel's class file is therefore both stronger than a source
scan (it asserts on compiled output, not on where a file lives) and cheap enough for the local gate.

The same applies to `:app:compileDebugReleaseKotlin`, which pins the `debugRelease` variant too.

A check of this shape has an obvious failure mode: renaming the panel makes it pass vacuously. It
must therefore assert **both** directions — the stub *is* in the release output and the panel is
*not* — the same both-directions discipline `SpecCorpusTest` applies to the diagnostics registry.

### 3. `BuildConfig.DEBUG` is the wrong gate here, for a specific reason
AGP derives `BuildConfig.DEBUG` from the build type's `isDebuggable`, and `debugRelease` sets
`isDebuggable = true`. Gating the recorder on `BuildConfig.DEBUG` would therefore enable it in
`debugRelease` — a variant whose source set contains no panel — producing a build that collects
trace data it can never display. The availability flag must come from the *same variant-specific
file pair* as the panel, so the two cannot disagree by construction.

### 4. `debugRelease` needs the release stub wired explicitly
`initWith` copies build-type configuration only. Without `sourceSets["debugRelease"].java.srcDir(...)`
pointing at the release stub directory, the `debugRelease` variant loses the declaration entirely and
fails to compile. Sharing one directory is preferable to a third copy: two stubs that can drift is
the risk the PRD already names.

### 5. The transport's redirect count is local, and `Rejected` is a `data object`
Surfacing the HTTP status means `ManifestFetchResult.Rejected` must carry data. Every construction
site and its tests change. `ManifestFetchResult.Unavailable` (IOException / timeout) legitimately has
no status and must stay distinguishable from "server answered, answer refused" — that distinction is
most of the diagnostic value for a site owner behind a misconfigured CDN.

## Prior art
- `ADR-006` browser-owned chrome — the impersonation rule the panel's label/value separation applies.
- `ADR-009` non-blocking discovery, `ADR-010` graceful fallback — the inspector explains a fallback,
  it does not prevent one.
- `ADR-011` first-use consent — the panel reports the decision and cannot change it.
- `SPEC-002` layer model, `spec/diagnostics.json` — the codes and dispositions the panel renders. The
  registry's `layerOrder` is why a rejection has diagnostics from exactly one layer.
- `SPEC-003` `siteskin-lint` — the same `SiteSkinValidator` seam, a different transport and process.
- `A11Y-001` — `BrowserSurfaceConventionsTest`, `WeboraButton`, the strings rule, and the precedent
  that a guarantee needs one enforcement point plus a test that fails when a call site bypasses it.
- `HARDEN-002` — canonical-origin display including scheme and non-default port.
- `PRIV-001` — zero telemetry; `BrowsingDataCleaner` is where in-memory SiteSkin state is dropped.
- `NET-002` — the cache contract whose states the panel names.

## Risks
- Widening `ManifestFetchResult` and `ManifestDiscoveryOutcome` invites a caller to branch on the new
  detail → the plan must keep the added fields observational and prove decision-equality with the
  recorder installed and absent.
- A variant-specific declaration duplicated per build type can drift → share one source directory
  between `release` and `debugRelease`, and have the packaging check read compiled output rather than
  trust the file's presence.
- A verification task that greps for a class name passes vacuously after a rename → assert the stub's
  presence in the same task.
- Extending the conventions scan to variant roots can silently scan nothing if a root is missing →
  the scan must take explicit roots and keep a raised coverage floor.
- Untrusted diagnostic pointers and response headers can forge inspector rows → bound and
  single-line every value, and keep values in a slot distinct from browser-authored labels.
- Retaining a record per origin is a retention decision → bound the map, retain no manifest bytes,
  and clear it with `Clear browsing data`.

## Open questions
None blocking. One decision recorded rather than deferred: the inspector ships in the `debug` build
type only, not in `debugRelease`. `debugRelease` exists to exercise the *release* configuration
against a local cleartext server, and SiteSkin requires HTTPS, so discovery does not run there
anyway — adding developer tooling to it would weaken the absence claim for no diagnostic gain.
