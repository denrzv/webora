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

## Amendment — attributed manifest preview

The consent sheet now shows an explicitly attributed preview of the validated manifest: its bounded
title and optional subtitle, an optional contrast-guarded brand-colour swatch, and bounded counts of
navigation tabs, quick actions and menu items. Browser-authored explanation and site-authored text
remain separate nodes. The canonical origin remains in the browser-authored heading, so a manifest
cannot blend its brand claim into Webora's identity statement.

The preview is produced only by `SiteSkinConsentModel.from`, with every remote string and collection
bounded by `SiteSkinLimits`. It excludes the logo because previewing it would make refusal cost the
user another request to the site; it excludes the home URL and action payloads because the sheet
describes rather than navigates; and it excludes item labels because a stack of attacker-chosen
labels above Allow would become a site-authored message rather than a neutral capability summary.

It is a violation of this decision to concatenate manifest text into browser copy, replace the
canonical origin with a manifest claim, expose unbounded remote content, fetch a preview-only asset,
or let the preview invoke any site action. The earlier consequence allowing "optionally, the site
name" is therefore closed: only the attributed projection above may cross into the sheet.
