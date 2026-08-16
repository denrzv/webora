# Review: DEMO-005
Date: 2026-08-16
Status: RESOLVED

## Summary

Reviewed `5e632f3..a051616`. The journey correctly moves to the expressive dock/hub and adds real
product history with a black-box accessibility seam. Cross-repository verification found one
integration-blocking drift: completed Bloom publishes Catalog/Profile, while Webora's canonical
fixture, spec, conformance expectation, walkthrough, and new journey still name Flowers/Account.

## Architecture

| Concern | Assessment |
|---|---|
| Trust boundary | PASS — UiAutomator is instrumentation-only; production receives no DOM/test hook. |
| Origin/tab isolation | PASS — browser security/dock tags guard same-origin history and teardown. |
| Sources of truth | PASS after fix — fixture/spec/corpus now match the completed Bloom manifest. |
| Scope/complexity | PASS — no production behavior or protocol capability changed. |

## Security

| Property | Assessment |
|---|---|
| Browser ownership | PASS — product prose selects content; only browser tags assert trust/chrome. |
| Typed site projection | PASS — hub labels are observed, Call is not launched or synthesized. |
| Fail-closed teardown | PASS — expressive header, dock, hub, and legacy layers must be absent. |
| No bridge | PASS — accessible black-box UiAutomator selector is test-only. |

## Findings

### FINDING-1 · High · canonical manifest drift

The public `denrzv/bloom-flowers` default branch currently serves a 1,336-byte manifest whose second
and fourth navigation entries are `catalog: Catalog/grid_view` and `profile: Profile/person`.
Webora's fixture still has `catalog: Flowers/flower` and `account: Account/person`; the new live test
inherits the stale labels. The fixture's own note says Bloom serves a byte-identical copy, so both
repositories cannot currently satisfy their documented contract, and the hosted hub assertion would
fail against the completed upstream deployment.

Fix `TASK-FIX-1`: reconcile the Webora fixture/expected result/spec/conformance and all current user
guidance/test selectors to the completed Bloom vocabulary. Preserve the byte-copy negative control.

## Not findings

- Selecting the remote product by accessible text is not trusting it as security state; the test
  separately requires browser-owned identity/dock tags and exact-origin teardown.
- UiAutomator does not widen production dependencies: it is `androidTestImplementation` only and is
  used because Compose cannot author semantics for a remote WebView DOM.
- The four screenshot filenames remain unchanged intentionally. DEMO-005 owns story alignment;
  CI-009 owns expanded frames/contact sheet and two cold accepted runs.
- The Call action is asserted reachable but not invoked intentionally; opening the dialer would
  contest the evidence frame and typed action/effect behavior already has focused tests.

## Test coverage

The JVM contract pins expressive/product/history/hub/teardown markers and rejects the retired bottom
navigation wait. Instrumentation compilation covers the UiAutomator API. Runtime remains unavailable
without a connected device or `/dev/kvm`.

## Verdict

RESOLVED. `TASK-FIX-1` updates the fixture, normalized result, pinned SHA-256, specification,
navigation corpus, live selector, and guidance to the completed Bloom manifest. The public-source
bytes now compare byte-for-byte with Webora's fixture, and focused core/app/compiled instrumentation
checks pass.
