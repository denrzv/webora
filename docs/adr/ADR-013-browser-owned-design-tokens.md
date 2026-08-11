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

This ADR records which one won, why the selection changed once it was drawn, and the rule that would
have survived whichever had.

## Decision

**Direction A — "Soft instrument", unamended.**

A leans into the platform's current language: a 52 dp rounded search pill, tonal containers instead
of outlines, and a floating pill dock that lifts the navigation controls off the bottom edge. The
identity lives in **its own tonal chip below the field** — adjacent to the address, never inside it.

| | |
|---|---|
| Chrome | 52 dp pill + identity chip + 60 dp floating dock; tonal elevation, not shadow |
| Palette | Teal seed on a warm neutral ground. Primary `#00696E` light / `#5CDBD8` dark; primary container `#B4E5E3` / `#1F2C2C`; chrome surface `#D3EBEA`; ground `#F7F6F3` / `#0E1414`; ink `#191C1C` / `#DEE4E3` |
| Geometry | Radius 999 dp pills, 20 dp cards; 4 dp base spacing, 20 dp gutter |
| Type | 28 / 22 / 15 / 13 / 12, Roboto |
| Icons | 8, at 20 dp, stroke 1.9 |

The teal was chosen to sit apart from the pink, blue and orange brands demo sites use: while SiteSkin
is active the browser's own colour should not read as part of the page.

### Why this changed after B was drawn

B — "Get out of the way" — was selected first, from the annotated sketches, for maximum page area and
zero learning cost. Two renders reversed that, and both are kept as evidence:
[`selected-b.html`](../design/directions/selected-b.html) and
[`selected-a.html`](../design/directions/selected-a.html), each showing the same two scenes — a
regular site and the Bloom Flowers integration — in the direction's own tokens.

- B's cost is not theoretical. Its own verdict says *"Webora looks like every other browser"*, and
  drawn at 360 dp beside the integrated chrome that is exactly what it is: the regular-mode frame
  carries no signal that the integrated frame is a different thing the browser decided to do.
- B is the direction with the `C1` risk. Its identity mechanism is a two-state omnibox, and dropping
  the second state — so the domain sits inside the editable field — violates `ADR-006`. That is a
  live regression risk on every future change to the address bar, carried for the sake of ~40 dp.

A costs the ~40 dp and buys back both. Its identity chip is a separate element that cannot collapse
into the address field, because there is nothing to collapse: the chip and the field were never the
same control.

**A decision that survives being drawn is stronger than one made from a description.** The sketches
in `index.html` annotate the trade; they do not show it at the size the trade is felt.

### The amendment was tried and does not transfer

The B decision carried an amendment from direction C: an identity surface fixed to one graphite
colour in both themes, so it can never take a colour from anything. It was re-measured against A's
palette and **rejected**, on numbers rather than taste:

| Fixed chip | vs A light ground `#F7F6F3` | vs A dark ground `#0E1414` |
|---|---|---|
| `#1C1F26` graphite | 15.26:1 | **1.13:1** |
| `#00363A` | 12.23:1 | **1.41:1** |
| `#04413F` | 10.6:1 | **1.62:1** |

The mechanism works in B because the fixed thing is a **full-bleed band that *is* the surface** —
nothing sits behind it to separate from. A's identity is a **chip**, an element *on* a surface, and a
fixed dark chip on A's dark ground stops reading as a chip at all. Applying it would trade a legible
identity element in dark mode for theme independence that A does not need: `C2` already keeps
manifest values out of browser tokens, and A satisfies `C1` structurally rather than chromatically.

So A is taken pure, and it is also the cheaper of the two in tokens. B plus the amendment needed four
theme-independent on-strip foregrounds — measured in the previous revision of this ADR after
rendering showed `#146C43` at 2.56:1 and `#1552C4` at 2.38:1 on graphite. A's chip needs an ordinary
light/dark pair:

| On the identity chip | Light `#B4E5E3` | Dark `#1F2C2C` |
|---|---|---|
| Identity text | `#00201F` — 12.44:1 | `#B4E5E3` — 10.49:1 |
| TLS secure | `#0F5132` — 6.80:1 | `#6DD58C` — 7.94:1 |
| TLS not secure | `#7A1B14` — 7.65:1 | `#FFB4AB` — 8.50:1 |

The chip separates from its ground at only 1.27:1 light / 1.29:1 dark. **This is correct and should
not be "fixed".** The chip is a status display, not an interactive control, so WCAG 1.4.11 does not
require its boundary to carry contrast; the identity is carried by text at 10.49:1 or better, and TLS
state is carried by a lock glyph and the word "Secure" as well as by hue, so no meaning rests on
colour alone (`A11Y-001`).

## The C1 mechanism, and what violates it

**Separate element, always.** The identity chip renders `securityPresentation(state.mode)`, derived
from the committed `SiteOrigin` — never `state.addressText`, never document content, never a manifest
field. The pill above it renders `state.addressText` and is never an identity claim. The two can
visibly disagree, which is precisely the signal `ADR-006` exists to preserve.

The browser-authored semantics node carrying `BROWSER_SECURITY_TAG` hangs on the chip. It is half the
guarantee — assistive technology reads the semantics tree, not the pixels — and the visible chip is
the other half.

> **Violation condition.** Moving the domain or TLS state *into* the address pill, or deleting the
> chip when the pill gains focus, **violates `ADR-006`**. An editable value and an identity claim are
> never the same pixels. The chip is the cheapest correct answer available; collapsing it to recover
> vertical space is a regression, not a simplification.

`UX-003` owns implementing it. This is A's lowest-risk property and the reason it was chosen over B on
`C1` as well as on identity — but low risk is not no risk, so the condition is written down.

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
| **B — Get out of the way** | The shape people know from Chrome, Safari and Firefox: a 48 dp omnibox and a hairline 52 dp toolbar, ~100 dp of chrome against A's ~140 dp, and it stays densest at 200% font scale. | No identity of its own — drawn beside the integrated chrome, regular mode reads as Chrome. Its `C1` mechanism is a two-state omnibox whose second state is easy to drop late for simplicity, and dropping it breaks `ADR-006`. |
| **C — The instrument frame** | Webora gains an identity argued from the same thesis as the product's security model, with contrast checked once against a fixed frame rather than per theme. | An always-dark bar is a strong opinion many users read as heavy in light mode. Its fixed-surface idea was measured against A above and does not transfer to a chip. |

Neither was eliminated on `C1`; all three stated an implementable mechanism. The selection is a trade
between page area, learning cost and identity — recorded so it can be revisited on that basis rather
than re-argued from taste.

## Consequences

- **`UX-002`** builds `WeboraTheme` to the palette, geometry and type scale above, including the
  identity-chip pairs from the measured table. It carries the `C2` test.
- **`UX-003`** implements the pill plus the separate identity chip. Its acceptance criterion is a
  security one: the chip's value comes from `securityPresentation(state.mode)` and survives the pill
  entering and leaving focus.
- **`UX-004`** applies the same tokens to home, onboarding and settings.
- Tonal roles need a full container/on-container token set in both themes; A re-derives fully for
  dark rather than fixing any surface.
- Every direction inherits `C3`–`C7` unchanged: the `RAW_BUTTON_IMPORT` gate, no string literals
  reaching the screen, `A11Y-001` intact, an icon source that does not yet exist, and edge-to-edge
  already on.
- The integrated SiteSkin chrome is untouched. `UX-005` is descoped; that surface already has a
  `NavigationBar`, a theme projection and a contrast guard.
