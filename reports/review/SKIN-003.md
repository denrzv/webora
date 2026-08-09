# Review: SKIN-003
Date: 2026-08-09
Status: RESOLVED

## Summary

Reviewed commit `31cedd2` for architecture, origin/trust ownership, action capability boundaries,
collection limits, accessibility, test strength, and complexity. One accessibility finding requires
a micro-fix; all security and architecture boundaries pass.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | PASS — trusted models and match policy stay in core; Android presentation stays in `:app`. |
| Trust seam | PASS — the factory consumes only `SiteSkinConfiguration` and a browser-observed page URL. |
| Effect boundary | PASS — callbacks carry trusted `NavigationItem` or a closed browser command; no UI effect execution exists. |
| Scope | PASS — activation, action resolution/dispatch, WebView navigation, and intents remain deferred to `SKIN-004`. |
| Complexity | PASS — Detekt and the full pre-commit gate pass. |

## Security

| Property | Assessment |
|---|---|
| Collection bounds | PASS — model and renderer preserve first-N 5/5/20 limits; lowering the cap fails the negative control. |
| Active destination | PASS — `NavMatcher` uses the observed URL and no-match selects nothing; forced-first negative control fails. |
| Menu ownership | PASS — fixed closed commands occupy a separate browser-labelled section; removing them fails the negative control. |
| Icon input | PASS — closed local glyph mapping with generic fallback performs no network/resource lookup. |
| Native capabilities | PASS — no URI parsing, Android intent, permission, package/component, or generic action contract was added. |

## Findings

1. **RESOLVED — Symbol glyphs contributed raw characters to accessibility semantics.** `SiteSkinIcon`
   renders decorative text (`⌂`, `▦`, and similar) without clearing its semantics. TalkBack may
   announce meaningless punctuation alongside the complete item label. Clear descendant semantics
   now clears semantics on the decorative glyph; the parent item owns the browser-useful full label,
   and Compose coverage asserts the glyph is absent from the merged semantics tree.

## Not findings

- Components are intentionally standalone and are not visible in `BrowserScreen`; `SKIN-004` owns
  consent-aware integrated-mode composition and action dispatch.
- A site can label its own menu item “Settings,” but it cannot place that item in, remove, or reorder
  the separately headed browser section. The structural distinction is the security property.
- Defense-in-depth renderer caps duplicate core normalization intentionally; presentation remains
  bounded if a future trusted producer raises protocol limits.
- Text glyphs avoid adding an icon dependency and form a closed mapping; the finding concerns only
  their accessibility semantics, not remote asset resolution.
- Runtime Compose tests and screenshots require a connected device. Repository policy explicitly
  accepts compilation in managed cloud and forbids provisioning a software-only emulator.

## Test coverage

JVM tests cover exact limits/order, empty collections, active/no-match behavior, trusted typed
selection, and fixed browser commands. Compose tests cover rendered cap/selected semantics,
quick-action collapse and typed selection, and separate menu sections; sources compile.

## Verdict

RESOLVED — `TASK-FIX-1` removes decorative glyphs from accessibility output; app unit tests,
instrumentation compilation, and the full gate pass.
