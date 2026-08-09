# SKIN-003: Research
Status: RESEARCH_READY

## Question

How should Android render trusted SiteSkin bottom navigation, quick actions, and menu entries while
preserving the core action allow-list, browser-owned controls, bounded layout, and a pure test seam?

## Origins involved

- The serving origin is the exact canonical HTTPS origin already embedded in
  `SiteSkinConfiguration`; this ticket performs no discovery or origin comparison.
- Item actions were normalized against that serving origin by `SecurityValidator`. Internal URLs
  and the configured home URL therefore remain exact-origin; external HTTPS URLs are trusted only
  as inert values that still require browser confirmation when eventually dispatched.
- The browser-observed current-page URL is needed only by the existing `NavMatcher` (active item)
  and `ActionResolver` (`share`). It is browser input, not a manifest claim.
- No asset origin is added. Icons are closed symbolic names normalized by core, never URLs; the
  existing logo pipeline remains the only remote brand-asset path.

## Manifest-controlled surface

After validation, a site can influence the ordered labels and allow-listed symbolic icons of at
most five bottom-navigation items, twenty menu items, and five quick actions. Each item carries a
trusted `NormalizedAction`; bottom-navigation match patterns can influence which item the pure
`NavMatcher` reports active for a browser-observed page URL. The validated branding colour scheme
may colour the surfaces, but labels remain bounded to 32 characters and icons cannot name assets.

## Browser-owned remainder

Compose owns layout caps as defense in depth, truncation, touch targets, expanded/collapsed state,
semantics, stable ordering, generic icon fallback, and the visual active indicator. A no-match
result must not select the first destination. Presentation callbacks carry the trusted item/action
rather than raw URI or intent fields.

The menu container, section headings/divider, dismissal behavior, page/security information, and
settings controls remain fixed browser UI. Site entries cannot imitate these controls structurally:
they render in a separate section and cannot remove, replace, or reorder the browser section.
Android intent construction, external confirmation, permissions, WebView commands, and final
action dispatch remain outside these components. `open_menu` requests only this browser-owned
container.

## Relevant code
| Path | Why it matters |
|---|---|
| `siteskin-core/.../model/SiteSkinConfiguration.kt` | Supplies trusted, normalized collections and item/action values; constructors are inaccessible to app production code. |
| `siteskin-core/.../nav/NavMatcher.kt` | Existing bounded pure matcher returns at most one active trusted item and explicitly permits no match. |
| `siteskin-core/.../action/ActionResolver.kt` | Existing seam maps normalized actions to the closed `ResolvedAction` hierarchy. |
| `app/.../siteskin/SiteSkinTheme.kt` | Supplies contrast-corrected Compose colours already derived from trusted branding. |
| `app/.../siteskin/SiteSkinTopBar.kt` | Establishes SiteSkin Compose/test-tag/accessibility conventions and the future sibling surface. |
| `app/.../browser/BrowserScreen.kt` | Current composition root and browser-owned regular menu; integrated activation remains intentionally absent until `SKIN-004`. |
| `app/src/main/res/values/strings.xml` | Browser-authored visible and accessibility labels. |
| `app/src/test/.../siteskin/` | Pure JVM presentation-model precedent without Robolectric. |
| `app/src/androidTest/.../siteskin/` | Compose semantics/layout test precedent; runtime execution depends on connected Android hardware. |

## Prior art

- `spec/SPEC.md` §§5, 7, 7.1, 8, and 10 define item shape, closed actions, active matching,
  first-N limits (5/20/5), and empty-collection behavior.
- ADR-003 and ADR-007 prohibit executable manifest content and restrict actions/schemes to closed
  allow-lists; `CORE-005` implements the resolution seam.
- ADR-006 requires browser-owned security chrome, and its rationale applies to separating menu
  security/settings entries from site branding.
- ADR-008 requires integrated state to remain a sealed `BrowserMode`, so this ticket must not add
  UI booleans that independently imply activation.
- ADR-011 makes first-use consent a prerequisite for actual activation; that belongs to
  `SKIN-004`/`PRIV-001`, not these presentational components.
- `SKIN-001` and `SKIN-002` established trusted theme projection and pure-model/thin-Compose
  conventions.

## Risks

- Remote labels overlap at large font scale → cap visible lines with ellipsis, use equal bounded
  navigation slots, preserve full labels in semantics, and add Compose layout coverage.
- A future caller bypasses core collection limits → presentation mapping defensively keeps the
  first 5/20/5 and pure tests pin exact behavior.
- Active selection relies on object identity (trusted models are not data classes) → derive and
  compare stable item ids returned by `NavMatcher`.
- Site menu entries spoof browser settings/security → render immutable browser commands in a
  distinct, labelled section and test that remote entries cannot alter it.
- UI directly executes remote-influenced actions → components emit typed presentation selections;
  resolution/dispatch remains an explicit higher-layer responsibility.
- Material icon dependencies widen or raw names become asset lookups → use a local closed mapping
  to existing Material primitives/generic glyphs and never resolve a remote resource.

## Open questions

None. `SKIN-003` can ship reusable presentation models/components and tests without wiring them
into live mode; `SKIN-004` owns the activation composition and action-dispatch lifecycle.
