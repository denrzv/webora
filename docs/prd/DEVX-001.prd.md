# DEVX-001: SiteSkin Integration Inspector
Status: PRD_READY

## Context / Problem
A site owner integrating SiteSkin has two ways to find out why their manifest did not take effect,
and neither one answers the question inside the browser that made the decision.

`siteskin-lint` (`SPEC-003`) fetches `/.well-known/siteskin.json` over HTTPS and reports the
diagnostics that `SiteSkinValidator` produced. It is the right tool for CI, but it is a different
process on a different machine with a different network path. It cannot report cache state,
consent state, the applied colour projection, or which navigation item the browser considers active
on the page currently open.

The browser itself reports nothing, on purpose. `ADR-010` makes every failure path end in regular
browsing, so an integration that is rejected looks exactly like a site that never published a
manifest. That is correct for users and useless for developers: the pipeline computes a precise
answer at every stage and then discards it. Concretely, today:

- `OkHttpManifestSource` collapses every non-success HTTP response into `ManifestFetchResult.Rejected`,
  a `data object` that carries no status code. A 404, a 500, a cross-origin redirect, and an
  oversized body are indistinguishable to everything downstream.
- `ManifestDiscoveryCoordinator.validate` receives `SiteSkinValidationOutcome.Rejected` — which
  carries the exact list of rejecting diagnostics — and maps it to
  `ManifestDiscoveryOutcome.Unavailable(origin, generation)`, dropping the diagnostics entirely.
- The accepted path drops them too: `SiteSkinValidationOutcome.Accepted.diagnostics` holds the
  `SS-W-LIMIT-TRUNCATED`, `SS-W-CONTRAST-CORRECTED`, `SS-W-ICON-UNKNOWN`, `SS-W-FIELD-UNKNOWN` and
  `SS-E-ACTION-UNKNOWN` records that explain why the rendered chrome differs from what the manifest
  asked for, and `candidateDisposition` keeps only `validation.configuration`.
- `ManifestCache` knows whether a navigation was served fresh from memory, revalidated with a `304`,
  refetched, or replayed from stale bytes after a transport failure. None of that leaves the cache.
- `SiteSkinTheme.from(...)` silently corrects manifest colours that fail the WCAG guard, and
  `SiteSkinChromeModel.from(...)` silently truncates over-limit collections and labels. A developer
  sees the corrected result and has no way to learn that a correction happened.

So the failure mode this ticket addresses is not a missing screen. It is that the browser already
computes everything a developer needs and deliberately forgets it one call later.

The counterweight is that a developer tool is a place where the trust boundary erodes quietly. An
inspector displays untrusted, manifest-derived strings next to browser-authored labels, which is the
`ADR-006` impersonation problem moved into a new surface. It must also be genuinely absent from
release builds — a `BuildConfig.DEBUG` branch is a runtime claim about code that still ships.

## Goals
1. Give a developer, inside the browser that made the decision, the full record of one origin's
   manifest discovery: origin, manifest URL, HTTP status, redirects, cache state, schema version,
   accept/reject outcome, every diagnostic with its JSON pointer, the applied colour projection, the
   active navigation item, and the items that were dropped or truncated.
2. Carry that record through the existing pipeline without changing a single browsing decision. The
   inspector observes; it never becomes an input to activation, consent, caching, or navigation.
3. Make the inspector's absence from release builds a structural fact enforced by the build's
   variant source sets, and assert it from the JVM gate rather than asserting a runtime flag.
4. Keep manifest-derived text inside the inspector bounded and unable to imitate the inspector's own
   browser-authored field labels.
5. Hold the inspector to the same accessibility contract as the rest of the browser, by extending
   `A11Y-001`'s source scan to the variant source sets rather than leaving a debug-only screen
   outside the gate that exists to catch exactly that.

## Non-goals
- A release-build or user-facing diagnostics UI. Users get `ADR-010` fallback, not error reporting.
- Persisting, exporting, uploading, or logging the trace. No file, no `Log` call, no clipboard, no
  network. `PRIV-001`'s zero-telemetry rule has no developer-tooling exemption.
- Replacing or duplicating `siteskin-lint`. The inspector reports what this browser did on this
  device; the CLI stays the authority for CI and for a site owner without the app.
- Editing, overriding, reloading, or force-accepting a manifest from the inspector. A tool that can
  make a rejected manifest activate is a bypass of the validator, not a view of it.
- New diagnostic codes. The registry in `spec/diagnostics.json` is unchanged; the inspector displays
  what `:siteskin-core` already emits.
- Any change to `:siteskin-core`. The trace is an app-layer observation of a core-owned decision.
- Instrumented UI tests of the panel as a gate. Consistent with `A11Y-001`, instrumented evidence is
  recorded in QA and the JVM gate covers the pure decisions.

## User stories
- As a site owner whose manifest is not activating, I can open the inspector on my own site and read
  that the request returned `404`, or that it returned `200` and was rejected with
  `SS-E-ORIGIN-MISMATCH` at `/bottomNavigation/2/action/url`.
- As a site owner whose chrome renders but looks wrong, I can see that my primary colour was
  corrected by the contrast guard, that my sixth navigation item was truncated, and that one action
  type was dropped as unknown.
- As a site owner debugging navigation highlighting, I can see the current page URL, the match
  patterns considered, and which item — if any — `NavMatcher` made active.
- As a developer of Webora, I can tell whether a navigation was served from a fresh cache entry, a
  `304` revalidation, or a fresh fetch, without attaching a proxy.
- As a security reviewer, I can confirm that the inspector ships in no release artifact, that it
  cannot change what the browser does, and that a hostile manifest cannot forge its field labels.

## Acceptance criteria
1. A debug-only inspector panel shows, for the current origin: the canonical origin, the manifest
   URL, the HTTP status of the final response, the number of redirects followed, the cache state,
   the schema version, the accept/reject outcome, every diagnostic code with its pointer, the
   consent decision, the applied colour roles with their dark/light selection, the active navigation
   item id or its explicit absence, and the count of dropped or truncated items.
2. The panel is reachable only from the `debug` build type. The `release` and `debugRelease` variants
   compile a stub that renders nothing and contain no panel implementation class.
3. A JVM test asserts absence rather than assuming it: it runs in every variant, reads a
   per-build-type expectation from the build file, and fails if the panel class is loadable when the
   expectation is `false` or missing when it is `true`. A negative control confirms the test fails
   when the panel is moved into `src/main`.
4. Installing the trace changes no browsing decision: with the recorder enabled and disabled, the
   same discovery inputs produce identical `ManifestDiscoveryOutcome` and identical
   `CandidateDisposition` values, proven by test.
5. The trace records a rejection's diagnostics, which the pipeline discards today, and records the
   HTTP status for a non-success response, which the transport discards today.
6. Manifest-derived strings shown in the inspector are bounded to the same published core limits the
   visual chrome uses, are reduced to a single line, and are rendered in a value slot that is never
   concatenated into a browser-authored label. A negative control proves the test fails when the
   bound is removed.
7. The trace holds at most one record per origin for a bounded number of origins, retains no manifest
   bytes, and is cleared by `Clear browsing data` along with the manifest cache.
8. `BrowserSurfaceConventionsTest` scans the debug and release variant source roots in addition to
   `src/main/java`, and the inspector panel satisfies the existing literal, accessible-name and
   touch-target rules. A negative control proves the extended scan fails on a violation placed in the
   debug source set.
9. No new Android permission, no new dependency, no telemetry, and no logging call is introduced.
10. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: the inspector is read-only and observational. It cannot activate, re-validate,
  or override a manifest, cannot alter consent, and cannot widen an origin. Manifest text inside it
  is bounded and label-isolated so it cannot impersonate browser-authored copy. Nothing is persisted
  or transmitted.
- Reliability/fallback: an absent, failed, or cancelled discovery yields an explicit "no record"
  state rather than a blank or guessed one, matching how `BrowserStatusRegion` refuses to publish an
  empty semantics node.
- Performance: recording is a bounded in-memory write of small immutable values on the existing
  discovery coroutine. No manifest bytes are retained, no work is added to the main thread, and the
  release variant installs a sink that discards without allocating a record.
- Accessibility: the panel is browser-owned UI and is held to the `A11Y-001` contract — resources
  for all copy, `WeboraButton` for controls, and content that reflows at large font scale.

## Risks
- Threading transport and cache detail through the pipeline widens `ManifestFetchResult` and
  `ManifestDiscoveryOutcome`. Widened types invite a caller to branch on the new detail; the added
  fields must be observational only, and the equality-of-decisions test in criterion 4 is what keeps
  that honest.
- A variant-specific declaration that exists twice can drift, leaving the stub and the real host with
  different signatures or the release stub accidentally non-empty. The build must share one source
  directory between `release` and `debugRelease` rather than keeping a third copy, and the packaging
  test must read the stub, not trust it.
- `BuildConfig.DEBUG` is true for `debugRelease` because that build type is debuggable, so gating on
  it would enable the sink in a variant whose source set has no panel. The availability constant must
  come from the same variant-specific pair as the panel, so the two cannot disagree.
- Extending the conventions scan to variant source roots can regress the coverage floor or start
  scanning generated sources. The floor must be raised with the extension, and the scan must be given
  explicit source roots rather than a parent directory.

## Open questions
None. Scope follows `docs/ROADMAP.md`, `docs/BACKLOG.md` (`DEVX-001`), `docs/DEVELOPMENT_PLAN.md`
§60 row 17, `ADR-006`, `ADR-010`, `ADR-011`, and the `A11Y-001` conventions gate.
