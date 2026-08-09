# BROWSE-002 review

## Scope

Reviewed both task commits for architecture, origin/security boundaries, renderer lifecycle, back dispatch, URL parsing, test coverage, and Detekt constraints.

## Findings

### FINDING-1 · Medium · navigation correctness

The initial Compose back handler was enabled from an asynchronous `canGoBack` observation and always consumed back. If renderer history changed before dispatch, `goBack()` returned false but normal activity back was swallowed. Page-start observations also read `WebView.url`, which can still describe the prior page instead of the callback destination. TASK-FIX-1 now checks live history, temporarily disables its callback before delegating to the activity dispatcher, and carries WebView callback URLs/loading state directly. Resolved.

## Not findings

- Explicit HTTP remains accepted because BROWSE-002 is ordinary browser navigation; HTTPS is the default for host-like input and remains mandatory for later SiteSkin activation.
- A Regular mode may carry no origin for a malformed/transient renderer callback; Integrated mode cannot, and only trusted core types can construct it.
- Search uses a fixed browser-owned HTTPS endpoint. Provider preferences and persistence are later product settings, not website-controlled configuration.
- The Android wrapper/controller is intentionally thin and is compiled rather than Robolectric-tested, matching repository convention.
- No screenshot was captured because `adb devices` reports no connected target. The APK and Android-test sources compile locally.

## Test coverage

| Area | Evidence |
|---|---|
| URL/search allow-list and negative schemes | `AddressResolverTest` |
| Regular-mode observations and address editing | `BrowserStateTest` |
| Existing immutable WebView policy | `WebViewHardeningTest` and compiled device test |
| App integration / Android source | debug APK and Android-test Kotlin compilation |
| Repository quality/security gates | pre-commit guardrail |

## Verdict

RESOLVED — FINDING-1 is fixed and no open findings remain.
