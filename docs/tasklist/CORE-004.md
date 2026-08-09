# CORE-004: Tasklist
Status: TASKLIST_READY

## TASK-1 — Trusted model and security primitives

- Add immutable trusted configuration/action model types with no public constructors or `copy()`.
- Add exact-HTTPS-origin parsing/resolution with userinfo, port, subdomain, protocol-relative, and
  traversal-underflow protections.
- Add colour canonicalization, WCAG contrast calculation, and deterministic correction.
- Extend diagnostics with optional JSON pointer without regressing schema validation.
- Tests: `OriginPolicyTest`, `ColorPolicyTest`, `TrustedModelApiTest`, existing schema suite.
- Acceptance: primitives are pure JVM, deterministic, cover both positive and adversarial inputs,
  and cannot independently mint a trusted configuration.
- Negative control: bypass exact-origin comparison and contrast correction in turn; named tests fail.
- Negative-control result: exact-origin bypass failed `OriginPolicyTest`; contrast bypass failed `ColorPolicyTest`.
- Status: complete

## TASK-2 — Security validator and focused normalization

- Implement known-v1 projection from schema-valid `JsonObject` through ordered URL/action/icon,
  duplicate, limit, Unicode string, and colour stages.
- Return a trusted configuration plus stable code/pointer diagnostics; invalid serving origins return
  no configuration, while localized defects preserve the safe remainder.
- Cover every action type, icon fallback, all collection/string bounds, duplicate direction, home
  fallback, empty navigation, diagnostics order, and trusted model output.
- Tests: `SecurityValidatorTest` plus the full core suite and Detekt.
- Acceptance: focused expected models/diagnostics match SPEC §§7–12 and CORE-002 unknown fields are
  ignored rather than honored.
- Negative control: remove scheme restriction, duplicate filtering, clamping, and icon substitution
  in turn; named tests fail.
- Negative-control result: scheme, duplicate, clamp, and icon bypasses each failed `SecurityValidatorTest`.
- Status: complete

## TASK-3 — Published-corpus conformance and architecture record

- Add canonical JSON projection in test code and compare every security-reachable corpus fixture to
  its published result and security diagnostics, explicitly excluding CORE-002-owned unknown-field
  warning discovery.
- Extend the corpus test helper only as needed to parse fixture bodies.
- Record the trusted CORE-004 seam and later CORE-002 adapter responsibility in `CLAUDE.md`.
- Tests: `SecurityConformanceTest`, `./gradlew :siteskin-core:check`, full pre-commit gate.
- Acceptance: all canonical results match byte-for-value semantics, security diagnostics preserve
  expected order/pointers, core remains Android-free, and the full project gate passes.
- Negative control: bypass origin, scheme, duplicate, limit, icon, and contrast protections one at a
  time; at least one named conformance/focused assertion fails for each and the result is recorded.
- Negative-control result: focused and corpus assertions failed for origin and contrast bypasses
  (`TASK-1`) and scheme, duplicate, limit, and icon bypasses (`TASK-2`); all protections were restored.
- Status: complete

## TASK-FIX-1 — Preserve cross-stage diagnostic order

- Source: `/review finding 1`
- Separate same-origin logo preparation from colour correction and defer toolbar/string work so
  diagnostics follow SPEC §12 across a manifest triggering multiple stages.
- Add a combined focused test that asserts exact codes and pointers across URL/action, duplicate,
  limit, and colour stages.
- Acceptance: the combined ordering test and corpus conformance pass; pre-commit gate passes.
- Status: complete
