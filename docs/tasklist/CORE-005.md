# CORE-005: Tasklist
Status: TASKLIST_READY

## TASK-1 — Closed resolved-action model

- Add the sealed pure-JVM `ResolvedAction` hierarchy with semantic payload variants and no generic
  URI or native-intent escape hatch.
- Add API tests proving the hierarchy is sealed and limited to the intended browser effects.
- Tests: `TrustedModelApiTest` and the existing core suite.
- Acceptance: typed variants cover internal/external navigation, dial, email, map, share, refresh,
  and menu effects; public API is documented; no Android type or dependency is introduced.
- Status: complete

## TASK-2 — Trusted action resolver

- Add `ActionResolver` mapping all nine normalized v1 action types to the closed model.
- Source `home` and `share` payloads from browser-owned context, not action fields.
- Fail closed for inconsistent or unknown internal values without creating a new diagnostic layer.
- Add focused tests for every positive mapping, missing payloads, hostile irrelevant fields, and an
  allow-list negative control.
- Tests: `ActionResolverTest`, `:siteskin-core:test`, and `:siteskin-core:check`.
- Acceptance: all nine types resolve deterministically; external navigation stays distinct;
  specialized values remain inert; unknown/malformed values return null.
- Negative control: replacing the unknown-type fallback with a generic external-navigation result
  made `ActionResolverTest.unknown types and missing required payloads fail closed` fail with
  `AssertionError` (`./gradlew ...`, exit 1); the allow-list fallback was restored.
- Status: complete

## TASK-FIX-1 — Bind home resolution to trusted site context

- Source: `/review finding 1`
- Replace the arbitrary public home URL parameter with the trusted `SiteConfiguration` domain type.
- Update resolver tests to prove home navigation is sourced from that trusted object.
- Acceptance: callers cannot label an arbitrary string as the trusted home destination.
- Status: complete
