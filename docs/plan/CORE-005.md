# CORE-005: Implementation plan
Status: PLAN_APPROVED

References: `docs/prd/CORE-005.prd.md`, `docs/research/CORE-005.md`, `spec/SPEC.md` §7, and
`docs/adr/README.md` ADR-003/ADR-007.

## Flow

```text
trusted NormalizedAction + trusted home URL + browser-observed current page URL
  → exhaustive ActionResolver dispatch
  → typed ResolvedAction data, or null on an inconsistent trusted-model state
  → later browser-owned Android executor (out of scope)
```

## Trust and origin boundary

CORE-004 remains the only remote-input trust boundary: it allow-lists action types and schemes,
resolves exact-origin internal URLs, and drops unsafe items before constructing `NormalizedAction`.
CORE-005 consumes that trusted value and does not accept a raw DTO, JSON object, arbitrary intent,
or caller-selected action/scheme string.

`internal_url` preserves CORE-004's exact-origin absolute destination. `home` ignores all action
payload fields and takes the trusted configuration home URL. `external_url` remains a distinct
confirmation-required command even though its HTTPS destination may be cross-origin. `share`
ignores action fields and takes the browser-observed current page. Specialized native actions carry
only inert payload data; mapping them to `ACTION_DIAL`, `mailto:`, `geo:`, and platform UI stays in
the Android app.

The website cannot select packages, components, intent actions, flags, MIME types, permissions,
share targets, WebView methods, or menu implementations. There is deliberately no generic
`OpenUri`/`NativeIntent` escape hatch.

## Model

`ResolvedAction` is a sealed public interface with semantic variants:

- `NavigateInternal(url)` for `internal_url` and `home`.
- `NavigateExternal(url)` for `external_url`; the Android layer must confirm before leaving.
- `Dial(value)`, `ComposeEmail(value)`, and `OpenMap(value)` for inert specialized data.
- `Share(pageUrl)` for the browser-observed page.
- `Refresh` and `OpenMenu` as browser-owned commands.

Nine manifest action types intentionally map to eight effects because `home` is a trusted source of
an internal navigation rather than a distinct execution capability.

## Resolver behavior

`ActionResolver.resolve(action, site, currentPageUrl)` dispatches only the nine constants. It
requires the appropriate normalized payload for URL/value actions, ignores irrelevant fields for
browser-context actions, sources home navigation from the trusted site object, and returns null for
an unknown type or missing required payload. The
nullable fallback is defense in depth for future internal changes, not a second remote-input
validation/diagnostic layer.

## File-by-file changes

| File | Change |
|---|---|
| `siteskin-core/src/main/kotlin/dev/siteskin/core/action/ResolvedAction.kt` | Add the closed, typed effect hierarchy with public KDoc |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/action/ActionResolver.kt` | Add pure exhaustive dispatch and fail-closed payload extraction |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/action/ActionResolverTest.kt` | Cover all nine mappings, source authority, malformed internal state, and allow-list negative control |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/TrustedModelApiTest.kt` | Prove the action hierarchy stays sealed and exposes no generic capability if reflection adds useful assurance |

No schema, fixture, DTO, or security-validator change is planned: unknown actions and unsafe schemes
are already dropped by CORE-004 and pinned by the conformance corpus.

## Testing

- Focused resolver tests for each v1 action type.
- Negative tests for unknown type, missing URL/value, hostile irrelevant payloads, and a non-listed
  scheme/type control that would expose deny-list dispatch.
- `./gradlew :siteskin-core:test` for pure-JVM regression coverage.
- `./gradlew :siteskin-core:check` for the Android-dependency leak gate.
- `bash scripts/pre-commit-check.sh` before each task commit.

## Security negative control

Temporarily replace explicit type dispatch with a generic URI result (or add a default mapping) and
confirm the unknown/hostile-type test fails; restore the allow-list implementation before commit.
Record the command and observed failure in the tasklist.
