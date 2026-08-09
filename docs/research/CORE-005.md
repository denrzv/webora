# CORE-005: Research
Status: RESEARCH_READY

## Question

How should trusted inert actions become a closed browser command model without moving Android
execution or remote-input validation across the `:siteskin-core` boundary?

## Current state

- `SecurityValidator` is the trust boundary. It allow-lists all nine action type strings, resolves
  internal and external URLs, drops unknown/unsafe items, and constructs `NormalizedAction` through
  an internal constructor.
- `NormalizedAction` deliberately remains inert and exposes `type`, `url`, and `value`. Its instances
  can only originate inside core, but the stringly shape admits impossible combinations when a
  downstream consumer branches on it directly.
- `SiteSkinConfiguration` supplies the normalized same-origin `site.homeUrl`; the browser supplies
  the current page URL used by `share`. Neither value should come from an action payload.
- There is no Android action executor yet. `:siteskin-core` is a JVM library whose dependency leak
  check forbids Android/AndroidX artifacts.

## Origins involved

- `internal_url` already carries an absolute URL bound to the manifest's exact serving origin.
- `home` must use the trusted configuration's same-origin home URL, never a manifest action field.
- `external_url` may carry another HTTPS origin, but its result must retain an explicit
  confirmation-required semantic for the browser UI.
- `share` uses the browser-observed current page URL. Resolution does not reinterpret it as a
  website-supplied URI command.
- `phone`, `email`, and `map` carry values normalized by CORE-004 for later construction of only the
  browser-selected `tel:`, `mailto:`, and `geo:` platform operations.

## Manifest-controlled surface

The manifest chooses one of the allow-listed action types and, where the v1 schema requires it, a
URL or value. It may choose an internal destination, propose an HTTPS external destination, or
prefill inert phone/email/map data. It cannot select an Android intent action, URI scheme, package,
component, permission, arbitrary extras, share target, menu implementation, or reload behavior.

## Browser-controlled remainder

The browser owns the current page URL, trusted home URL, external-navigation confirmation, Android
intent construction, activity resolution, dial confirmation, permission policy, share-sheet
content/type, WebView navigation/reload, menu UI, and graceful failure when no handler exists.
Resolution in core produces data only; it performs none of those effects.

## Relevant contracts

- `spec/SPEC.md` §7 defines nine effects and mandates the type/scheme allow-lists.
- `docs/adr/README.md` ADR-003 forbids code and arbitrary intents; ADR-007 fixes the action
  allow-list and unknown-item behavior.
- `conventions.md` requires sealed hierarchies and zero Android dependencies in core.
- CORE-004 already owns raw-input diagnostics and dropping. CORE-005 should not duplicate that
  validator or add diagnostics unreachable from trusted inputs.

## Proposed shape

Add `dev.siteskin.core.action.ResolvedAction` as a public sealed interface. Use data classes for
`NavigateInternal`, `NavigateExternal`, `Dial`, `ComposeEmail`, `OpenMap`, and `Share`; use data
objects for `Refresh` and `OpenMenu`. Resolve `home` to `NavigateInternal(homeUrl)`, so nine input
types map to eight effect variants while retaining all nine specified behaviors.

Add a stateless `ActionResolver` accepting a trusted `NormalizedAction`, the trusted site object, and
the browser-observed current page URL. It returns nullable `ResolvedAction`: exhaustive known cases
resolve, while inconsistent internal state returns null. Payload access is guarded even though such
states cannot currently cross the validator, so future internal refactors fail closed.

## Files and tests

- Add `siteskin-core/src/main/kotlin/dev/siteskin/core/action/ResolvedAction.kt`.
- Add `siteskin-core/src/main/kotlin/dev/siteskin/core/action/ActionResolver.kt`.
- Add `siteskin-core/src/test/kotlin/dev/siteskin/core/action/ActionResolverTest.kt` covering all
  nine inputs, payload selection, fail-closed inconsistent states, and a deny-list negative control.
- Extend trusted API reflection tests only if needed to prove the sealed model or constructor
  boundary; no spec/schema/fixture change is expected because CORE-004 already satisfies corpus
  normalization.

## Risks

1. **Capability inflation:** a generic URI/open-intent variant would undo the allow-list. Mitigate
   with semantic variants and no scheme or Android-action parameter.
2. **Wrong payload authority:** `home` or `share` could accidentally consume action values. Tests
   must provide hostile unused fields internally and prove browser-owned context wins.
3. **Validation duplication:** re-parsing URLs here could diverge from CORE-004. Consume trusted
   normalized values and fail only on missing impossible payloads.
4. **Android leakage:** keep all types as strings/data and rely on `:siteskin-core:check` plus the
   full pre-commit gate.
