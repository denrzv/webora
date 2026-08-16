# DEMO-005: Expressive Bloom Flowers integrated showcase
Status: PRD_READY

## Context / Problem

The expressive Android chrome and the redesigned, deployed Bloom storefront/product route are
complete, but Webora's canonical live journey still asserts the retired persistent SiteSkin bottom
navigation and skips the product, history, and navigation-hub story. The cross-repository pieces
therefore exist without one executable integration contract proving that they form the M9 demo.

## Goals

- Align the live journey with Home → consent → storefront → Happy Days → Back/Forward → hub.
- Prove the hub exposes the manifest-described routes and typed quick action while Bloom remains
  exact-origin integrated.
- Prove leaving Bloom removes all branded chrome and restores ordinary browser chrome.
- Record the deployed Bloom prerequisites without copying or creating a Webora-only web contract.

## Non-goals

- CI-009's final screenshot names, contact sheet expansion, or two consecutive hosted acceptance runs.
- SiteSkin schema, validation, discovery, consent, action, or appearance changes.
- Website redesign, ecommerce behavior, or Webora-specific DOM/user-agent detection.

## User stories

- As a reviewer, I can run one journey that demonstrates the complete expressive Bloom story.
- As a user, I can browse a real product and use Back/Forward without losing integrated chrome.
- As a security reviewer, I can see exact-origin teardown before regular chrome appears.

## Acceptance criteria

1. The deployed Bloom prerequisites are complete: BLOOM-001/#3 and BLOOM-002/#4 are closed, the
   manifest validates, and `/catalog/happy-days/` is the real same-origin product route.
2. The live instrumentation journey waits for the expressive header and browser-owned dock, not the
   retired persistent SiteSkin bottom navigation or quick-action overlay.
3. The journey reaches Happy Days through real page navigation and proves browser Back and Forward
   retain the exact-origin integrated security surface and fixed dock.
4. The brand control opens the native hub; Home, Flowers, Cart, Account, and Call are reachable
   through semantics authored from the current trusted manifest projection.
5. Leaving Bloom for a regular HTTPS origin removes the expressive header, dock, hub, Bloom branding,
   and site navigation before ordinary Webora security/navigation chrome is asserted.
6. Instrumentation compiles; runtime capture is executed only on a connected device or hosted KVM
   runner and is otherwise reported as an environment limitation.
7. No schema or website-only runtime contract is added; CI-009 remains the owner of final two-run
   screenshot acceptance.
8. `bash scripts/pre-commit-check.sh` passes.

## NFR

- Security/privacy: only browser-owned tags and bounded trusted labels are asserted; no page content
  becomes authority and no telemetry is added.
- Reliability/fallback: waits are bounded and regular-mode teardown fails closed.
- Performance: the journey adds no production work.
- Accessibility: hub routes/actions are located through their accessible labels.

## Risks

- Remote copy changes can make content-text selectors flaky; use a stable same-origin product link
  destination exposed by the deployed accessible page and keep security assertions browser-owned.
- CI-009 may rename/capture additional frames later; avoid taking ownership of its evidence policy.

## Open questions

- Research must identify the narrowest stable way to activate the product link in a WebView while
  keeping the integration test black-box and avoiding a Webora-specific website hook.
