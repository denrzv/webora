# QA Report: BROWSE-009
Status: QA_PASSED

## Scope

Renderer ownership per tab, and terminal failure for the paths that used to end in an indefinite
spinner. Three production layers — event routing (`RendererOwnership.kt`), Compose host identity
(`key(controller.tabId)` + `BrowserWebViewController.detachFromParent`), and TLS settlement
(`mainFrameTlsFailure`) — plus two review fixes.

**Not in scope, and asserted unchanged:** `BrowserSession`'s reducer, `BrowserSessionSnapshot`,
`AddressResolver`, `applyWebViewHardening`, `TransferPolicy`, `candidateDisposition`/`isCurrent`,
`SiteSkinTheme`, `ManifestCache`, `BrandAssetLoader`, and every `spec/` artifact.

**Environment.** Managed checkout, no emulator and no `/dev/kvm`. Everything below marked
*instrumented* is compiled and **not executed**; that is stated per row rather than folded into a
pass. `A11Y-001`'s rule stands: the gate is JVM-only and an instrumented assertion is never promoted
to a gate claim.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | A background tab's page change updates that tab, not the selected one | `RendererOwnershipTest` (JVM) | Pass |
| 2 | An event from the selected tab still updates the selected tab | `RendererOwnershipTest` (JVM) | Pass |
| 3 | A background tab's main-frame failure sets only its own `loadFailure` and clears only its own loading | `RendererOwnershipTest` (JVM) | Pass |
| 4 | An event for a closed tab changes nothing and authorizes no effect | `RendererOwnershipTest` (JVM) | Pass |
| 5 | A page start advances only its own generation and names itself in the discovery effect | `RendererOwnershipTest` (JVM) | Pass |
| 6 | A completed page records once per tab; two tabs on one URL record separately | `RendererOwnershipTest` (JVM) | Pass |
| 7 | Interleaved A-starts / select-B / A-fails / B-completes leaves each tab holding its own state | `RendererOwnershipTest` (JVM) | Pass |
| 8 | Closing a tab drops only that tab's bookkeeping | `RendererOwnershipTest` (JVM) | Pass |
| 9 | Negative control: router re-pointed at `session.activeId` | run and restored | 6 of 8 fail; only scenarios 2 and 8 survive |
| 10 | The WebView host is keyed by tab id, inside the `BROWSER_CONTENT_TAG` region | `RendererHostContractTest` (JVM) | Pass |
| 11 | Negative controls: un-keyed host; key outside the measured region | in-test counter-examples | Both rejected |
| 12 | Every emitted event carries an owner read once into a local | `RendererHostContractTest` (JVM) | Pass, all four event types |
| 13 | The router's code contains neither `updateActive` nor `activeId`; `applyRendererEvent` delegates and does not mutate | `RendererHostContractTest` (JVM) | Pass |
| 14 | Negative control: the regression the first assertion missed (`session = session.updateActive {…}` inside the renderer handler) | run against real source and restored | Fails as required |
| 15 | `detachFromParent` removes the view from its parent; `destroy` detaches first | `RendererHostContractTest` (JVM) | Pass |
| 16 | Negative controls: detach that only compares; destroy before detach; `removeView` deleted from the real source | in-test counter-examples + one run and restored | All rejected |
| 17 | A cancelled main-frame handshake settles the page exactly once, and the finish is not a completion | `HardenedWebViewClientTest` (JVM, MockK) | Pass |
| 18 | A handshake failure below the main frame cancels without replacing the page | `HardenedWebViewClientTest` (JVM) | Pass |
| 19 | `mainFrameTlsFailure` is total: null, blank, no observed main frame, already settled | `HardenedWebViewClientTest` (JVM) | Pass |
| 20 | `handler.proceed(` appears in no executable line under `src/main/java` | `HardenedWebViewClientTest` (JVM) | Pass |
| 21 | A subresource `onReceivedError` publishes no failure and does not suppress the page's completion | `HardenedWebViewClientTest` (JVM) | Pass — **test written by this ticket**, see notes |
| 22 | Negative controls: TLS rule unconditional; `isForMainFrame` filter dropped | both run and restored | Each fails only its own assertions |
| 23 | `BrowserSession`, `BrowserState`, `BrowserFailureStateTest` behaviour unchanged | existing suites, **unedited** | Pass |
| 24 | Whole app unit suite | `./gradlew :app:testDebugUnitTest` | 363 tests, 0 failures |
| 25 | Full gate | `bash scripts/pre-commit-check.sh` | Green (gitleaks, shellcheck, 23 readiness checks, core with no SDK, unit, inspector-absence, detekt) |
| 26 | Instrumented sources compile | `./gradlew :app:compileDebugAndroidTestKotlin` | Pass |
| 27 | Each tab keeps its own renderer and its own failure across A → B → A | `TabRendererIsolationTest` (instrumented) | **Unknown — compiled, not run** (no device) |
| 28 | Exactly one renderer attached to the window after every switch | `TabRendererIsolationTest` (instrumented) | **Unknown — compiled, not run** |
| 29 | Closing the failed tab leaves the healthy tab rendered | `TabRendererIsolationTest` (instrumented) | **Unknown — compiled, not run** |
| 30 | Single-tab failure/retry/Home journey still works | `BrowserRecoveryInstrumentedTest` (instrumented, unedited) | **Unknown — compiled, not run** |

## Edge cases

- **Invalid manifest → regular browser mode.** Unchanged. `candidateDisposition` and the validator
  are not edited, and `ManifestDiscoveryCoordinatorTest`, `SiteSkinRuntimeTest` and
  `SiteSkinTraceNeutralityTest` pass unedited. What changed is only *which tab* an outcome is
  attributed to.
- **Origin change / redirect.** Exact-origin activation is untouched;
  `BrowserState.forObservedOrigin` still drops integrated mode on any non-matching origin. One
  redirect case is newly relevant and is a deliberate non-fix: a TLS failure whose `SslError.url`
  differs from the URL observed at `onPageStarted` (a redirect chain failing after the start)
  publishes nothing and can still leave a spinner. That is the fail-closed direction, it is strictly
  better than the previous behaviour of never publishing, and the plan records why narrowing it needs
  a `BROWSE-004` change.
- **Offline with cached manifest.** N/A — no change to `ManifestCache`, its keys, freshness or
  stale-replay rules. `ManifestCacheTest` passes unedited.
- **Oversized or malformed payload.** N/A — no change to parsing, bounds or diagnostics; `spec/` is
  untouched and `:siteskin-core:test` runs green with no Android SDK.
- **Accessibility (TalkBack, font scale).** No new UI. `BrowserStatusRegion` and `BrowserErrorPage`
  are unedited, so the live region still derives from state — and it now derives from the *right*
  tab's state, which is an accessibility fix as much as a visual one: a leaked observation used to
  make the region announce another tab's load. `BrowserSurfaceConventionsTest` and
  `BrowserAccessibilityTest` pass unedited. `RendererOwnership.kt` declares no composable.
- **Website-controlled input.** None added. The ownership key is a browser-issued `Long`; a page's
  only lever remains callback timing, which is exactly what the routing now contains.
- **Process death / restore.** `BrowserSessionSnapshot` is unchanged and `RendererPageBook` is
  deliberately unpersisted, so a restored tab still re-traverses discovery, consent and exact-origin
  activation. `BrowserSessionSnapshotTest` passes unedited.
- **Unresolvable address input.** Verified by inspection plus `AddressResolverTest`: a submit whose
  input `resolveAddressInput` rejects never calls `controller.navigate`, so no observation is
  produced, no loading state is set and no other tab is touched. There is no state transition to
  assert, which is why this is inspection rather than a new test.

## Result

Status: QA_PASSED
Notes:

**Scenarios 27–30 are Unknown, not Pass.** This checkout has no emulator, so the instrumented suite
is compiled and never executed. The behaviour those cases cover — that a retained `WebView` can be
adopted by a new host after a switch — is held in the gate only structurally, by scenarios 10–16.
That is the strongest available evidence here and it is deliberately not described as more.

**Scenario 21 is the ticket's most useful result and it is not in the diff's headline.** Its negative
control was written to confirm `BROWSE-004`'s `isForMainFrame` filter; deleting the filter left the
entire suite green, so a guard four tickets old and named in this PRD's own acceptance criteria had
**no test at all**. The test now exists, and re-running the control fails exactly it.

**One defect found and deferred, not carried.** `/review` FINDING-3: returning a tab to Home and
navigating again never loads the new URL, because `HardenedWebView`'s factory only loads when it had
to create the renderer. Pre-existing on `main`, unrelated to issue #103, and fixing it changes *when a
reload happens* — which is this ticket's own acceptance criterion 2 — with no device available to
confirm it. Filed as `BROWSE-010` in `docs/BACKLOG.md` and as GitHub issue #106, with the mechanism and the
reproduction.
