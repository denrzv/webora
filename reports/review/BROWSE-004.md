# Review: BROWSE-004
Date: 2026-08-09
Status: RESOLVED

## Summary

Regular browsing now derives visible transport/domain identity from the committed `SiteOrigin`, maps WebView failures into a closed browser-owned model, and provides recovery without exposing framework descriptions or remote styling.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | PASS — URL parsing/display uses core `SiteOrigin`; Android callbacks remain in `:app`. |
| State model | PASS — failure is explicit nullable data within Regular mode, not contradictory mode flags. |
| Browser ownership | PASS — identity, menu actions, error reasons, Retry, and Home are compiled UI. |

## Security

| Property | Assessment |
|---|---|
| Identity provenance | PASS — address edits cannot update `BrowserMode.Regular.origin`. |
| TLS claim | PASS — only parsed HTTPS origins produce `SECURE`. |
| Error content | PASS — the UI shows a closed reason and registrable domain, not full URLs/descriptions. |
| Main-frame isolation | PASS after TASK-FIX-1 — only callbacks with `isForMainFrame` emit failure state. |

## Findings

### FINDING-1 · Medium · Main-frame isolation

**File:** `app/src/main/java/app/webora/browser/web/HardenedWebViewClient.kt`

The legacy SSL callback provides no `isForMainFrame` signal, so forwarding it could let a subresource TLS failure replace a healthy document. TASK-FIX-1 retains fail-closed cancellation but does not emit page state; the modern request callback remains the only error-state source.

## Not findings

- Retaining the full retry URL in memory is not a display leak: Compose renders only its parsed registrable domain, and no value is logged or persisted.
- `Page information` and `Settings` currently dismiss the menu without opening new surfaces. They are stable browser-owned placeholders; certificate detail/settings behavior is outside BROWSE-004 and no remote capability is dispatched.
- Showing `Not secure` for HTTP is intentional regular-browser behavior; SiteSkin activation remains HTTPS-only and is not implemented here.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `SecurityPresentationTest.kt` | 3 | HTTPS/HTTP/absent identity and edited-address negative control. |
| `BrowserFailureStateTest.kt` | 3 | Safe retry, unsafe-scheme denial, stale-error clearing. |
| `HardenedWebViewClientTest.kt` | 1 | Closed framework error classification. |

## Verdict

RESOLVED — no open findings.
