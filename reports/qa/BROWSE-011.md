# QA Report: BROWSE-011
Status: QA_PASSED

Issue: [#116](https://github.com/denrzv/webora/issues/116)
Verified at `7581c6c` on `claude/bloom-flowers-reference-y5ybs7`, based on `origin/main` `68ede98`.

## Scope

A browser-owned Refresh in protected integrated mode, on a new control row inside the expressive
header; one pure `refreshAction(BrowserState)` owning what refreshing means for all three reload
call sites. Seven commits including two post-review fixes.

**Command gates, all green at this commit:** `bash scripts/pre-commit-check.sh` (gitleaks,
shellcheck, emulator-readiness self-test, `:siteskin-core:test` with no Android SDK, `./gradlew test`,
`assertInspectorAbsentFromReleaseVariants`, detekt) and `:app:compileDebugAndroidTestKotlin`, which
the gate does not cover and `CI-003` records surviving a green run.

**806 JVM tests, 0 failures.**

## Test scenarios

The issue's ten test requirements, each mapped to what actually verifies it.

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Integrated page → Refresh → same page reloads, loading completes | `refreshAction` returns `Reload` for a committed page; `RendererMountActionTest` proves `reload()` leaves `hostedUrl` alone so the mount rule does not re-issue; completion arrives through the unedited `WebViewEvent` pipeline | PASS (JVM) |
| 2 | Reload on the same validated origin keeps integrated chrome | No new code: `observePage` → `forObservedOrigin` returns the same `Integrated` instance for an equal `SiteOrigin`. `BrowserStateTest`'s existing same-origin cases cover it and are unedited | PASS (JVM, by non-change) |
| 3 | Reload redirecting cross-origin removes SiteSkin chrome | Same path, opposite branch: an unequal origin yields `Regular`, and the header — with Refresh inside it — is not composed. `ChromeHandoffTest` unedited | PASS (JVM, by non-change) |
| 4 | Tab A reload while tab B exists → only A changes | `RendererHostContractTest`: the dispatcher names no `activeId`, reaches no controller map, writes no session state. `TabRendererIsolationTest` drives it on a device | PASS (JVM) + hosted evidence pending |
| 5 | Switch A → B during A's reload → late callbacks stay with A | Structural at the gate (same case); the mid-flight switch is the instrumented case's third act | PASS (JVM) + hosted evidence pending |
| 6 | Failed/recoverable main frame → Refresh retries the current target | `PageRefreshTest`: failure with a retry URL → `Retry(exact url)`; failure without one → `Reload`, not `None` | PASS (JVM) |
| 7 | Pristine Home/new-tab → no invalid dispatch | `refreshAction(BrowserState())` is `None`; after `TASK-FIX-2` Home's dock derives its disabled state from that same call rather than a literal | PASS (JVM) |
| 8 | A manifest cannot inject, replace or rebind Refresh | `SiteSkinTopBarContractTest` (4 cases, 3 controls) on the control's declaration; `PageRefreshTest`'s parity case on the decision, with a hostile manifest setting every field it is allowed to carry | PASS (JVM) |
| 9 | Semantics distinguish Refresh from the trust shield | `SiteSkinTopBarTest`: Refresh is enabled, ≥48 dp, dispatches once, and the chip keeps its own browser-authored sentence. `browserMenuCommands()` was checked for a duplicate "Reload" name — there is none | Hosted evidence pending; structure PASS (JVM) |
| 10 | Regular-mode Reload tests stay green | `BrowserChromeTest`, `BrowserFontScaleTest`, `BrowserChromeContractTest`'s dock-order case: all unedited except for the two new `SiteSkinTopBar` arguments, all green | PASS |

## Edge cases

- **Invalid manifest → regular browser mode.** N/A — no validation, parsing, discovery or protocol
  code was touched. `spec/`, `:siteskin-core` and the fixture corpus are byte-identical, and
  `:siteskin-core:test` runs with no Android SDK as always. Refresh is only composed in
  `PROTECTED_INTEGRATED`, which an invalid manifest never reaches.
- **Origin change / redirect.** Covered by scenarios 2 and 3. No new deactivation path exists;
  integrated chrome including this control disappears with the mode, because it *is* the mode's
  composition. A reload that redirects is an ordinary `PageStarted` for a different origin.
- **Offline with cached manifest.** N/A — no manifest, cache or transport code changed. A refresh
  re-enters discovery through the same `PageStarted` → `DiscoverManifest` effect any navigation does;
  `NET-002`'s freshness and stale-reuse rules apply unchanged.
- **Oversized or malformed payload.** N/A — no payload is read. Refresh carries no manifest-derived
  value at all; `refreshAction` reads two browser-observed fields.
- **Accessibility (TalkBack, font scale).** Refresh is a `WeboraIconButton`, so the 48 dp
  `MINIMUM_TOUCH_TARGET` is structural rather than restated. Its name is `R.string.reload` — the same
  name the regular dock gives the command, so a screen-reader user does not learn two words for one
  action. `BrowserSurfaceConventionsTest` discovers every `@Composable` and would fail a string
  literal reaching an accessible-name argument. Disabled state is carried by semantics, not colour.
  At 320 dp × 200 % the instrumented case asserts the control stays inside the host **and** that
  `UX-021`'s 140 dp chip floor still holds — the placement's whole purpose, since the rejected
  brand-row placement broke that floor at *default* scale.

## Known limits, stated rather than implied

- **The instrumented cases are evidence, not gate claims.** They compile here and need a device to
  run; this environment has none. The standing rule from `CI-002` through `CI-005` is that hosted
  runs are never replaced by compiled instrumentation, and this report does not claim otherwise.
- **`CI-009` hosted acceptance must be re-taken.** The integrated frames photograph a header that is
  now one row taller. No frame was added or removed, no capture policy, deadline, threshold,
  exclusion or dismissal was edited, and `ExpressiveBloomJourneyContractTest` needed no change — so
  what is pending is a fresh look at the pictures, not a changed contract.

## Result
Status: QA_PASSED
Notes: Two `/review` findings were fixed before this report — a superseded acceptance criterion the
shipped code contradicted, and a Home call site that restated a decision instead of asking it. Both
carry negative controls with recorded results.
