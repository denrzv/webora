# Review: BROWSE-005
Date: 2026-08-09
Status: RESOLVED

## Summary

The change establishes narrow capability adapters and useful pure policy tests, but two remote-input boundaries must be tightened before QA.

## Architecture

| Concern | Assessment |
|---|---|
| Core boundary | Correct: Android capability policy remains in `:app`; `:siteskin-core` is unchanged. |
| Capability shape | Correct: external navigation is a closed hierarchy and adapters construct typed intents. |
| Composition | Correct: Activity owns SAF results and Android services; WebView remains a thin request source. |

## Security

| Property | Assessment |
|---|---|
| Arbitrary intents | Pass: no `Intent.parseUri`, component, package, flags, or arbitrary extras. |
| Confirmation | Pass: intercepted supported navigation becomes inert pending state before launch. |
| Main-frame isolation | Finding 1. |
| Upload restriction | Finding 2. |
| Permissions | Pass: no dangerous permission added. |

## Findings

### FINDING-1 · High · top-level capability boundary
**File:** `app/src/main/java/app/webora/browser/web/HardenedWebViewClient.kt`

The request overload does not inspect `isForMainFrame`, so a hostile iframe can raise browser-owned external navigation confirmation. Ignore non-main-frame external requests and add a pure negative-control test.

### FINDING-2 · Medium · MIME allow-list
**File:** `app/src/main/java/app/webora/browser/web/TransferPolicy.kt`

Unknown or excessive accept hints become `*/*`, expanding a page request into an unrestricted picker. Return no picker contract for inputs outside the allow-list and cancel the WebView callback instead.

## Not findings

- Public Downloads does not need a storage runtime permission on the supported API range when used through `DownloadManager`.
- `URLUtil.guessFileName` affects only a sanitized filename under the browser-selected Downloads directory; it cannot choose an arbitrary path.
- Handler resolution can race with launch; `runCatching` makes the adapter fail safely when that occurs.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `ExternalNavigationTest` | 2 | Supported closed schemes and arbitrary/malformed denial. |
| `TransferPolicyTest` | 3 | Download schemes, MIME bounds, and content URI restriction. |
| `HardenedWebViewClientTest` | 2 | Browser/external dispatch and framework errors. |

## Verdict

Resolve both findings in `TASK-FIX-1`, then rerun review and the complete gate.

Both findings were resolved: modern request handling now ignores non-main-frame external requests,
and unsafe or absent MIME hints cancel rather than widen the picker. Focused negative controls and
the full pre-commit gate pass.
