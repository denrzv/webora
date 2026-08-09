# CORE-005: Action model and resolution
Status: PRD_READY

## Context / Problem

CORE-004 produces trusted, inert action data after origin, scheme, and action-type validation. The
browser still needs a pure-core contract that converts those values into a closed set of commands
without exposing Android types or allowing website data to request arbitrary native behavior.

## Goal

Define a sealed `ResolvedAction` hierarchy and a resolver for all nine SiteSkin v1 action types.
The result carries only the minimum browser-owned data needed for later platform execution.

## In scope

- Resolve `internal_url`, `external_url`, `phone`, `email`, `map`, `share`, `home`, `refresh`, and
  `open_menu` trusted actions into a sealed core model.
- Preserve the distinction between in-WebView navigation, confirmed external navigation, safe
  platform intents, current-page sharing, and browser-owned commands.
- Keep platform execution, permission acquisition, and Android framework types outside core.
- Defensively return no action for unsupported or inconsistent input rather than inventing a
  fallback capability.

## Out of scope

- Android `Intent` creation or action execution.
- External-navigation confirmation UI.
- Navigation active-item matching (CORE-006).
- Re-validating raw manifest JSON or changing the v1 specification.

## Acceptance criteria

1. A sealed `ResolvedAction` represents each of the nine v1 action effects with typed payloads only
   where the effect requires one.
2. Every trusted v1 action resolves deterministically, including `home` from the trusted site's
   home URL and `share` from a caller-supplied current page URL.
3. Internal and home actions remain distinguishable from confirmed external navigation; phone,
   email, and map remain inert URI data and cannot execute code or acquire permissions.
4. Unsupported or structurally inconsistent action input produces no resolved action and cannot
   create an arbitrary scheme or native command.
5. The API remains pure JVM and has no Android dependency.
6. Unit tests cover all nine positive cases plus negative and boundary cases, including a security
   negative control.
7. `bash scripts/pre-commit-check.sh` passes.
