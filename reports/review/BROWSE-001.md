# Review: BROWSE-001
Date: 2026-08-09
Status: RESOLVED

## Summary

Reviewed the policy model, framework mapping, WebView client, Compose host, tests, and workflow
artifacts. The host applies a fixed browser-owned policy before the first load and exposes no native
bridge. One medium test-contract finding was resolved by TASK-FIX-1.

## Architecture

| Concern | Assessment |
|---|---|
| Android boundary | PASS — framework calls are thin wrappers around an immutable policy and pure URL classifier. |
| Compose lifecycle | PASS — factory hardens before load; update avoids reloading an unchanged URL. |
| Future ownership | PASS — browser state and external dispatch remain BROWSE-002/BROWSE-005 concerns. |
| Complexity | PASS — methods are short and Detekt passes. |

## Security

| Property | Assessment |
|---|---|
| Local resources | PASS — file/content and both file-URL escalation settings are false. |
| Transport | PASS — mixed content is never allowed; production cleartext remains disabled. |
| Safe Browsing | PASS — requested through the installed-provider feature seam when supported. |
| Native capability | PASS — no JavaScript interface is registered and non-web schemes fail closed. |

## Findings

### FINDING-1 · Medium · test completeness

The initial immutable model and tests did not represent Safe Browsing even though the wrapper always
requested it. TASK-FIX-1 added the policy value plus JVM and supported-provider instrumentation
assertions. Resolved.

## Not findings

- JavaScript is deliberately enabled: ordinary modern websites require it, while the privilege
  boundary is the absent native bridge rather than disabling renderer JavaScript.
- Deprecated file-URL settings are intentionally assigned. Explicit false values pin the security
  behavior on every supported API level rather than relying on provider defaults.
- Non-HTTP(S) URLs are blocked rather than dispatched. BROWSE-005 owns confirmation and external
  dispatch; silently handing them to the current renderer would weaken that future boundary.
- The instrumentation test is compiled but not run locally because the managed checkout has no
  `adb` or emulator. Pure tests cover the complete policy model; device CI owns framework execution.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `WebViewHardeningTest.kt` | 2 JVM tests | every policy value; HTTP(S), file, content, JavaScript and malformed URLs |
| `WebViewHardeningInstrumentedTest.kt` | 1 device test | real readable WebSettings and supported-provider Safe Browsing |
| Repository gate | full suite | core boundary, unit tests, Detekt, gitleaks and shellcheck |

## Verdict

RESOLVED — FINDING-1 is fixed and no open findings remain.
