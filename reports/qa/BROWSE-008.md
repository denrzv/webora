# QA Report: BROWSE-008
Status: QA_PASSED

## Scope

Browser-owned history → native Home → Android exit Back precedence across regular, integrated,
system/predictive, and multi-tab behavior.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Existing WebView history | JVM negative control with consuming history callback | PASS — Home callback is not invoked. |
| 2 | First regular page | JVM policy plus compiled regular Compose control | PASS — Back is enabled and falls back to Home. |
| 3 | First integrated page | Compiled integrated Compose control | PASS — fixed Webora Back is enabled and dispatches shared action. |
| 4 | Home Back | JVM policy test and callback enablement inspection | PASS — browser does not consume; platform remains owner. |
| 5 | Multi-tab fallback | `BrowserSessionTest` | PASS — selected state resets; inactive tab is unchanged. |
| 6 | SiteSkin teardown | Sealed-state transition and chrome-handoff tests | PASS — Home contains no integrated configuration/chrome. |
| 7 | Unit and static quality | app unit suite and detekt | PASS. |
| 8 | Android UI tests | Android-test Kotlin compilation | PASS (compiled). |
| 9 | Full repository guardrails | `bash scripts/pre-commit-check.sh` | PASS. |

## Edge cases

- invalid manifest → regular browser mode: PASS — regular first-page Back uses the same policy.
- origin change / redirect: PASS — live WebView history is consulted first, so redirect/provider
  history behavior wins before Home fallback; no origin state is copied.
- offline with cached manifest: PASS — navigation policy does not depend on discovery or network.
- oversized or malformed payload: N/A — no manifest or payload handling changed.
- accessibility (TalkBack, font scale): PASS (compiled) — existing fixed labelled 48 dp controls are
  reused; enabled semantics now reflect the product-level destination.
- stale Compose history observation: PASS — live controller result is authoritative.
- stale integrated chrome after fallback: PASS — Home's sealed state has no SiteSkin projection.
- Home with no page history: PASS — disabled browser callback delegates to platform exit behavior.

## Environment limitations

`adb devices` reports no connected device and `/dev/kvm` is absent. Repository policy forbids a
software-only emulator here, so runtime instrumentation, predictive-gesture exercise, and a new
perceptual screenshot were unavailable. CI-007 owns the required two consecutive hosted four-frame
runs; this ticket does not weaken or bypass that downstream evidence gate.

## Result

Status: QA_PASSED

All runnable code, compilation, and guardrail scenarios pass. Device-only visual/system navigation
evidence remains an explicit environment limitation and downstream CI-007 acceptance item.
