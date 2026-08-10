# HARDEN-001: Adversarial conformance corpus
Status: PRD_READY

## Context / Problem
The M4 roadmap calls for an adversarial corpus that proves the SiteSkin trust boundary remains
closed under hostile schemes, resource-exhaustion shapes, redirect abuse, deceptive IDNs,
duplicate identifiers, and over-limit collections. Several individual controls already have unit
or conformance coverage, but there is no single hardening ticket that audits the promised matrix,
fills its gaps, and makes regressions visible to both Webora and third-party implementers.

## Goals
- Audit the roadmap's complete HARDEN-001 attack matrix against the published conformance corpus
  and transport/origin tests.
- Add portable corpus cases for validation-layer attacks and focused app/core tests for attacks
  that require transport or origin context.
- Bound hostile nesting before it can exhaust parser or structural-validation resources.
- Preserve stable diagnostics, validation-layer ordering, and graceful regular-browser fallback.

## Non-goals
- Changing the SiteSkin v1 wire format, limits, or diagnostic dispositions.
- Adding new website-controlled native capabilities or a general-purpose URI dispatcher.
- Implementing brand-impersonation UI, privacy controls, accessibility review, or demo sites owned
  by later M4 tickets.
- Treating transport scenarios such as redirect loops as context-free JSON fixtures.

## User stories
- As a browser user, I want malicious manifests to fail closed without delaying or breaking normal
  browsing.
- As a SiteSkin implementer, I want committed adversarial examples that make the protocol's
  security boundary reproducible outside Webora.
- As a maintainer, I want each roadmap attack class pinned by a negative control so weakening a
  protection fails a deterministic test.

## Acceptance criteria
1. The adversarial matrix explicitly covers `javascript:`, `file:`, `content:`, `intent:`, and
   `data:` action URLs and proves each item is dropped with `SS-E-SCHEME-DENIED`.
2. The corpus proves a manifest over 128 KiB is rejected before parsing, while exact-boundary input
   remains eligible for validation.
3. Deeply nested JSON is rejected within a documented finite nesting bound without stack overflow,
   unbounded allocation, or a new remotely triggerable crash path.
4. Manifest discovery tests prove redirect loops and chains beyond two redirects fail closed and
   never publish a trusted configuration.
5. Origin tests cover Unicode/punycode IDN homograph presentation signals without weakening exact
   canonical-origin comparison.
6. Corpus cases prove later duplicate ids are dropped and over-limit navigation, quick-action, and
   menu collections keep only the first allowed items in document order with stable diagnostics.
7. Corpus registry/completeness tests and the CLI/core shared validator consume all new portable
   fixtures without implementation-specific exceptions.
8. Every protection added or audited has a recorded negative-control result.
9. Invalid input continues to degrade to regular browser mode; no manifest controls browser-owned
   domain/TLS presentation, redirect policy, parser limits, or native dispatch.
10. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: All remote input stays untrusted through bounded parsing and explicit
  allow-lists; tests and fixtures contain no secrets or telemetry.
- Reliability/fallback: Rejection is deterministic and leaves the current page in regular browser
  mode; caller-owned streams remain open according to the validator contract.
- Performance: Size, redirect, and nesting work is bounded before expensive interpretation; focused
  JVM tests remain suitable for the no-Android-SDK core gate.
- Accessibility: This ticket changes no UI; IDN signals remain browser-owned inputs for the
  separately tested security presentation.

## Risks
- A nesting cap added at the wrong layer could change diagnostic ordering or reject ordinary valid
  manifests; derive it from the bounded v1 shape and pin boundary cases.
- Duplicating transport behavior in the JSON corpus would blur layer ownership; keep redirect tests
  in the app transport harness and portable validation cases under `spec/fixtures`.
- Existing coverage can create a false sense of completion if it checks counts rather than each
  hostile spelling or first-wins ordering; matrix tests must identify every case explicitly.

## Open questions
- The repository has no root `ROADMAP.md`; `docs/DEVELOPMENT_PLAN.md` M4 is therefore the roadmap
  source for this ticket.
- Research must determine whether the JSON libraries already impose a suitable nesting ceiling or
  whether the shared validator needs an explicit pre-parse depth guard.
