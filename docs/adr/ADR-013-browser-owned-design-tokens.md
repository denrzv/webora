# ADR-013 — Browser-owned design tokens, compiled and never derived

Status: **ACCEPTED** — `UX-001`
Date: 2026-08-11
Supersedes the `PROPOSED` stub in [`README.md`](README.md).

## Context

`docs/design/AUDIT.md` found the imbalance this milestone exists to correct: the browser gives
*websites* a six-role colour system with a WCAG contrast guard (`SiteSkinColorScheme`), and gives
itself a bare `MaterialTheme {}` at `MainActivity.kt:38`. The manifest-driven surface is better
specified than any browser-owned one.

Three directions were rendered as 360 dp mockups of the four surfaces, light and dark, in
[`../design/directions/index.html`](../design/directions/index.html), each annotated with palette,
geometry, icon budget and — the part that can eliminate a direction outright — its mechanism for
keeping the registrable domain and TLS state browser-owned under `ADR-006`.

This ADR records which one won, what was amended, and the rule that would have survived whichever
had.

## Decision

**Direction B — "Get out of the way", amended with the fixed identity frame from direction C — "The
instrument frame".**

B is the conventional shape people already know from Chrome, Safari and Firefox: one compact 48 dp
omnibox showing the domain rather than the URL, a hairline 52 dp toolbar of icon-only controls, and
as little chrome as the job allows. It was selected for maximum page area and zero learning cost,
and because it stays densest at 200% font scale.

| | |
|---|---|
| Chrome | 48 dp omnibox + 52 dp toolbar; hairline elevation only (1 dp) |
| Palette | Near-monochrome. Action `#1552C4` light / `#8AB4F8` dark; TLS secure `#146C43`; chrome surface `#F1F3F5`; ground `#FFFFFF`; hairline `#D5D9DF` |
| Geometry | Radius 8 dp controls, 10 dp cards; 4 dp base spacing, 16 dp gutter |
| Type | 22 / 16 / 14 / 13 / 12, Roboto |
| Icons | 8, at 20 dp, stroke 1.9 |

Colour is reserved for the one action per screen and for TLS state — and TLS state is *also* carried
by a lock glyph and the word "Secure", so no meaning rests on hue alone (`A11Y-001`).

### The amendment

B's own verdict names its cost: *"Webora looks like every other browser — no identity of its own."*
Direction C exists to solve exactly that, and its central idea is taken:

> **The identity surface never takes a colour from anything.** It is the same graphite in light mode
> and in dark mode, sourced from a token outside the light/dark pair.

This was chosen over A's tonal identity chip because it answers B's cost **without adding chrome
height**, which is the single thing B is optimising for. It costs one extra token concept. Per C's
own annotation, it makes contrast checking *simpler* rather than harder: the frame is verified once
against a fixed colour instead of once per theme.

It also does security work rather than decoration. An identity surface that can never take a site's
colour is harder to confuse with site chrome while SiteSkin is active — the boundary between what
Webora says and what the site says becomes something you can see, not something you have to read
about.

## The C1 mechanism, and what violates it

B is the direction that has to answer `C1` most carefully, and it is the reason B is the
highest-effort of the three. The effort is not in the styling.

**Two states, and the second is mandatory.**

1. **Display state.** The omnibox is **not a text field at all.** It renders
   `securityPresentation(state.mode)` as non-editable, browser-owned text, with the path in a dimmer
   weight. That value derives from the committed `SiteOrigin` — never `state.addressText`, never
   document content, never a manifest field.
2. **Edit state.** Tapping swaps in the real editable field, and the identity moves to a **separate
   row beneath it**. An editable value and an identity claim are never the same pixels.

The browser-authored semantics node carrying `BROWSER_SECURITY_TAG` survives both states. It is half
the guarantee — assistive technology reads the semantics tree, not the pixels — and the visible row
is the other half.

> **Violation condition.** Dropping the second state, so that the domain sits inside the editable
> address field, **violates `ADR-006`** regardless of how it looks. A Chrome-style omnibox collapses
> browser identity into a field whose value is also page-derived and user-editable. This is the
> single most important thing to hold onto now that B is chosen, and it is the thing most likely to
> be dropped late for simplicity.

`UX-003` owns implementing it. A future change that unifies the two states, or removes the semantics
node, is a regression against this ADR and against `ADR-006`, not a simplification.

## The rule that survives whichever direction won

**Webora's palette, typography and shape scale are compiled into the app. There is no path from a
manifest value into them.**

`SiteSkinColorScheme` (`SiteSkinTheme.kt:42`) remains the **entire** website-influenceable colour
surface. `WeboraTheme` is a separately compiled token layer that a website cannot reach, read or
influence.

This invariant is currently maintained only by the accident that there is no browser palette at all.
The moment `UX-002` creates one, the accident stops protecting it — so `UX-002` carries a test
asserting no manifest value can reach a browser token. Recorded here as a handoff rather than left
implicit.

## Directions not selected

| Direction | What it offered | The trade that lost it |
|---|---|---|
| **A — Soft instrument** | Feels current; a floating pill dock that is thumb-reachable and reads as deliberate. Identity in its own tonal chip below the field, satisfying `C1` cheaply. Lowest risk — nothing fights an existing gate. | Tallest chrome of the three: pill, chip and dock consume roughly 140 dp of a 720 dp screen — directly opposed to the priority B was chosen for. |
| **C — The instrument frame** | Webora gains an identity, argued from the same thesis as the product's security model. Contrast checked once against a fixed frame. | An always-dark bar is a strong opinion, and some users read it as heavy in light mode. Its best idea survives here as the amendment. |

Neither was eliminated on `C1`; all three stated an implementable mechanism. The selection is a trade
between page area, learning cost and identity — recorded so it can be revisited on that basis rather
than re-argued from taste.

## Consequences

- **`UX-002`** builds `WeboraTheme` to the palette, geometry and type scale above, plus one identity
  token outside the light/dark pair. It carries the `C2` test.
- **`UX-003`** implements the two-state address bar. It is the largest piece of `M6` and the one with
  a security acceptance criterion rather than a visual one.
- **`UX-004`** applies the same tokens to home, onboarding and settings.
- Every direction inherits `C3`–`C7` unchanged: the `RAW_BUTTON_IMPORT` gate, no string literals
  reaching the screen, `A11Y-001` intact, an icon source that does not yet exist, and edge-to-edge
  already on.
- The integrated SiteSkin chrome is untouched. `UX-005` is descoped; that surface already has a
  `NavigationBar`, a theme projection and a contrast guard.
