# ADR-011: First-use consent before SiteSkin mode activates

Status: ACCEPTED
Date: 2026-08-07
Resolves: concept document §60 Q15, §60 Q10

## Context

The concept leaves open whether integrations are trusted automatically or require confirmation
(§60 Q15), and separately requires a per-site opt-out (§60 Q10).

Automatic activation means the browser's chrome changes appearance without the user having asked
for anything. Even with `ADR-006` keeping the domain visible, a UI that restyles itself on
navigation is disorienting the first time, and it makes the trust boundary invisible: the user
cannot tell that the site influenced the chrome, so they cannot reason about what else it might
influence.

## Decision

The first time a given origin's manifest validates, the browser shows a bottom sheet before
applying any branding:

> **bloomflowers.example** wants to customise this browser's navigation and appearance.
> The address bar and security indicator stay under your control.
>
> `Allow`  ·  `Not now`  ·  `Never for this site`

- **Allow** — persist the decision for that origin; activate. Subsequent visits are silent.
- **Not now** — regular mode for this session; ask again next time.
- **Never for this site** — persist the refusal; never ask again for that origin. Reversible in
  Settings.

The decision is keyed on origin (scheme + host + port), matching `ADR-004`. A decision for
`https://shop.example` says nothing about `https://admin.shop.example`.

## Consequences

- One extra tap per site, once. Acceptable for a change this visible.
- This is the natural enforcement point for the per-site opt-out (§60 Q10) — the same persisted
  decision serves both, rather than building two mechanisms.
- The sheet is browser-owned UI: the manifest supplies only the origin string and, optionally, the
  site name, both rendered in browser typography with the origin non-editable.
- Requires persistence before `SKIN-004` can activate anything, so the storage layer lands with
  `PRIV-001` rather than after it.
- The global SiteSkin toggle short-circuits this entirely: off means never ask, never activate.

## Alternatives rejected

**Activate silently, offer opt-out afterwards.** Puts the burden on a user who has already seen the
restyled chrome and has no reason to connect it to the site.

**Ask once globally rather than per site.** A single "allow site customisation" grant is not
meaningfully informed consent, and it discards the per-origin boundary the rest of the design is
built on.
