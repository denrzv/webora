# Review: BROWSE-008
Date: 2026-08-15
Status: RESOLVED

## Summary

The change centralizes browser Back precedence in a pure app-layer seam and projects one action to
regular, integrated, and Android system surfaces. Live WebView history remains authoritative;
native Home is a browser-owned fallback. No manifest, URL, persistence, or network surface changes.

## Architecture

| Concern | Assessment |
|---|---|
| State ownership | PASS — the active `BrowserSession` tab alone performs Home fallback. |
| WebView boundary | PASS — the controller's live `goBack()` result wins over observed state. |
| UI consistency | PASS — regular, integrated, and dispatcher paths share availability/action. |
| Platform delegation | PASS — Home installs no enabled callback, preserving Android exit handling. |

## Security

| Property | Assessment |
|---|---|
| Manifest isolation | PASS — no trusted configuration or manifest field reaches Back policy. |
| Origin isolation | PASS — fallback loads no URL and transfers no state between origins/tabs. |
| SiteSkin teardown | PASS — resetting to sealed Home drops configuration and page identity. |
| Capability scope | PASS — no intent, permission, bridge, transport, or arbitrary command added. |

## Findings

None.

## Not findings

- The controller remains retained after returning Home. This is the existing tab-session contract:
  Home hides the renderer without destroying the tab, while the state reset prevents stale page or
  SiteSkin presentation. Destroying it would unexpectedly discard the tab's renderer lifecycle.
- Visible Back availability no longer equals `state.canGoBack`. This is intentional: availability
  now represents the product-level history-or-Home contract, while Forward remains raw WebView
  history capability.
- The pure executor returns a consumed Boolean although the Compose callback ignores it. Tests and
  non-UI callers need the explicit contract; the system callback is enabled only where consumption
  is guaranteed.
- No fake Home entry is inserted into WebView history. Native Home remains a sealed browser mode.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `BrowserBackTest.kt` | 3 JVM tests | history precedence, first-page fallback, Home delegation. |
| `BrowserSessionTest.kt` | added isolation test | active reset and inactive-tab preservation. |
| `BrowserSiteSkinLayoutTest.kt` | 2 compiled Compose tests | enabled regular/integrated Back dispatch. |

## Verdict

RESOLVED — no findings. The implementation is ready for QA.
