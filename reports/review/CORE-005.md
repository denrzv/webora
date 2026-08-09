# Review: CORE-005
Date: 2026-08-09
Status: RESOLVED

## Summary

The sealed model is small, pure JVM, and avoids a generic native/URI capability. Resolver dispatch
is allow-listed and its focused tests include a useful negative control. One trust-boundary API
issue must be fixed before QA.

## Architecture

| Concern | Assessment |
|---|---|
| Core boundary | PASS — production code imports no Android types; dependency leak gate passes |
| Closed model | PASS — sealed semantic effects avoid contradictory flags and generic execution |
| Responsibility | PASS — execution/confirmation remain outside core |
| Trusted context | PASS — resolver accepts the trusted `SiteConfiguration` domain object |

## Security

| Property | Assessment |
|---|---|
| Unknown action | PASS — null fallback and negative control prove fail-closed behavior |
| External navigation | PASS — separate effect retains confirmation semantics |
| Payload authority | PASS — share is browser-observed and home is type-bound to trusted site data |
| Native capability | PASS — no package/component/intent/permission fields exist |

## Findings

### FINDING-1 · Medium · trusted-domain-construction
**File:** `siteskin-core/src/main/kotlin/dev/siteskin/core/action/ActionResolver.kt:11`

Current: the public resolver accepts `homeUrl: String`, so any caller can create an internal
navigation result for a cross-origin/untrusted string while the KDoc calls it trusted.

Fix: accept the trusted `SiteConfiguration` domain object and source `site.homeUrl` internally.
Update tests to prove the typed trusted context supplies the home destination.

Resolution: `TASK-FIX-1` changed the resolver signature and tests as specified.

## Not findings

- Nine manifest action types mapping to eight resolved variants is intentional: `home` is an input
  source for the same internal-navigation effect, not a new execution capability.
- Returning null for impossible normalized states is defense in depth, not duplicated raw-manifest
  validation; CORE-004 remains responsible for diagnostics.
- Specialized values are intentionally strings here. Android scheme/action selection occurs later
  in browser-owned app code, so encoding platform intents in core would weaken the boundary.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `TrustedModelApiTest.kt` | sealed permitted-subclass assertion | Closed effect surface |
| `ActionResolverTest.kt` | all nine actions, hostile ignored fields, malformed/unknown inputs | Dispatch and source authority |
| Core conformance suite | existing corpus | Upstream type/scheme/origin dropping |

## Verdict

RESOLVED — FINDING-1 was addressed by `TASK-FIX-1`; ready for QA.
