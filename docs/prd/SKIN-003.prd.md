# SKIN-003: Bottom navigation, quick actions, and menu
Status: PRD_READY

## Context / Problem

SiteSkin can already provide trusted navigation and quick-action models, theme tokens, and a secure
top bar, but the Android app does not render the remaining native navigation surfaces. Integrated
sites therefore cannot expose their validated primary destinations or actions through the chrome
promised by the protocol. These surfaces must remain bounded and usable without letting remote
labels, counts, or action data obscure controls or expand into arbitrary native capability.

## Goals

- Render up to five trusted navigation items in a native bottom bar with an accurate active state.
- Render trusted quick actions behind a bounded floating action button affordance.
- Render a side menu containing trusted menu entries alongside fixed browser-owned entries.
- Route selections through closed callbacks carrying trusted model values; keep action execution
  and Android intent construction outside presentation code.
- Keep all layouts legible, operable, and semantically labelled at maximum supported font scale.

## Non-goals

- SiteSkin activation, consent, origin-change deactivation, or skin swapping (`SKIN-004`).
- Reimplementing core validation, action resolution, or navigation matching.
- Executing resolved actions, constructing Android intents, granting permissions, or navigating a
  WebView directly from a Composable.
- Allowing a manifest to remove, rename, reorder, or restyle browser-owned menu commands.
- Regular-browser navigation chrome or website-provided arbitrary icons.

## User stories

- As a user, I can move among an integrated site's primary destinations from a compact native bar.
- As a user, I can identify the current destination without relying on colour alone.
- As a user, I can reach a site's validated quick actions without the controls covering each other.
- As a user, I can open a menu that clearly distinguishes site-provided entries from browser
  security and settings controls.
- As a large-text user, I can read or understand every control without labels overlapping.

## Acceptance criteria

1. Bottom navigation renders at most five trusted items, preserves their validated order, and
   exposes selection through a typed callback without constructing or launching native intents.
2. When more than five entries reach presentation, exactly the first five render; empty navigation
   renders no bottom bar, and long labels truncate without overlap at maximum font scale.
3. The active navigation destination is visually and semantically distinct without colour alone,
   and no-match state falsely selects no item.
4. Quick actions render through a bounded FAB/menu surface, preserve validated order, and expose
   each selection through a typed callback; empty actions render no FAB.
5. The side menu separates site-controlled entries from fixed browser-owned security/settings
   entries, and remote data cannot suppress or replace the browser-owned section.
6. All interactive targets meet the project minimum touch size and expose browser-authored
   accessibility roles, state descriptions, and content descriptions where visible text is absent.
7. Pure JVM tests cover caps, empty states, truncation-oriented presentation bounds, active-state
   derivation, and the browser-owned menu invariant; Android test sources compile.
8. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: consume only trusted core models; never accept generic URIs, intents, packages,
  components, flags, extras, or permissions in the UI contract.
- Reliability/fallback: empty or unexpectedly oversized collections degrade to absent or capped
  chrome without affecting the page renderer.
- Performance: presentation mapping is deterministic, synchronous, and linear in already-bounded
  collections; Compose items use stable keys.
- Accessibility: controls remain distinguishable without colour, use minimum 48 dp targets, expose
  useful semantics, and truncate rather than overlap under large font scaling.

## Risks

- A website could imitate browser commands in its menu; browser-owned entries need a fixed,
  visually separated section and browser-authored labels/icons.
- Long remote labels and large font scale can crowd the bar; presentation must cap lines and use
  ellipsis while retaining complete accessibility labels.
- Passing raw action fields into UI callbacks could bypass the core allow-list; callbacks must use
  trusted navigation/action values only.
- Multiple quick actions can overwhelm a single FAB; the expanded surface must be bounded by the
  validator's collection limits and collapse predictably after selection or dismissal.

## Open questions

None at PRD scope; research will select the existing trusted model seams and composition boundary.
