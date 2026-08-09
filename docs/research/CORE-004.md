# CORE-004: Research
Status: RESEARCH_READY

## Question

How can core establish the origin-bound trusted configuration and reproduce the v1 security
normalization contract while CORE-001 (public origin utilities) and CORE-002 (DTO parsing and
unknown-field discovery) are still pending?

## Current state and prerequisite seams

`:siteskin-core` currently has a production `SchemaValidator` over `JsonElement`, the shared
diagnostic types, constants, and `ManifestSource`. There is no `SiteOrigin`, DTO graph, parser, or
trusted model. The safe continuation is the same parsed-tree seam used by CORE-003: accept a
schema-valid `JsonObject` plus a serving-origin string, validate that origin independently, and
project only known v1 fields into private-constructor domain objects. This does not parse bytes or
discover unknown properties; CORE-002 can later adapt its DTO/parser output without weakening the
trusted constructor boundary.

Java's `URI` provides RFC-style resolution and normalized scheme/host/port components without an
Android dependency, but it normalizes `/../../evil` to an in-origin path. The corpus explicitly
requires that attempted traversal to be dropped, so dot-segment underflow must be detected before
resolution. URL acceptance cannot use prefixes or hosts alone.

## Origins involved

- **Serving origin:** a browser-owned exact HTTPS origin. It supplies the base for relative URLs and
  becomes a required field in the trusted configuration. Userinfo, query, fragment, non-root path,
  non-HTTPS scheme, or absent host makes the validator return no configuration.
- **Internal navigation and home:** must resolve to the same normalized scheme, ASCII host, and
  effective port. Protocol-relative references, changed ports, userinfo authorities, cross-host
  targets, and traversal above root are removed. Invalid `site.homeUrl` falls back to origin root.
- **External navigation:** may target another origin but only over HTTPS; CORE-005/platform code
  still owns user confirmation and execution.
- **Brand asset:** `logoUrl` is retained only at the exact serving origin. A subdomain is not trusted.
- `mailto`, `tel`, and `geo` values remain inert normalized action data; no permission or platform
  dispatch enters core.

## Manifest-controlled surface

The website can propose branding colours and logo location, toolbar text, site labels, navigation,
menu and quick-action items, icon tokens, match patterns, and action payloads. After this ticket it
can influence only bounded strings/collections, recognized browser icon tokens, corrected colours,
and inert actions that survived the browser's type/scheme/origin allow-lists.

The website cannot instantiate or copy a trusted configuration, select a resource by URL through an
icon field, create a new action type, expand a collection past browser limits, choose an unsafe URI
scheme, or bind an internal capability to another origin.

## Browser-owned remainder

- The exact serving origin and HTTPS requirement are caller/browser inputs, never manifest claims.
- Action and icon vocabularies, generic icon substitution, effective ports, defaults, bounds,
  diagnostic codes/order, WCAG targets, and correction algorithm are fixed core policy.
- Domain/TLS chrome, consent, external-navigation confirmation, asset downloading/MIME checking,
  action execution, and graceful regular-mode fallback remain browser-owned later-ticket behavior.
- Unknown-field discovery remains CORE-002 ownership. The `showDomain` and unknown-field fixtures
  are therefore not fully attributable to this validator; it must ignore them and must never honor
  them, while a later pipeline adapter prepends `SS-W-FIELD-UNKNOWN`.

## Relevant code and contract

| Path | Finding |
|---|---|
| `siteskin-core/src/main/kotlin/dev/siteskin/core/ManifestValidation.kt` | Existing public diagnostic/result vocabulary can be extended with pointers while preserving CORE-003 callers |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/SiteSkin.kt` | Limits are already public constants; trusted configuration does not exist |
| `spec/SPEC.md` §§3, 5–12 | Fixes exact origin semantics, allow-lists, limits, WCAG algorithm, dispositions, and normalization order |
| `spec/diagnostics.json` | Security codes and their disposition are authoritative; pointer values live in fixture expectations |
| `spec/fixtures/{valid,invalid}` | Canonical results define defaults, uppercase colours, action payload shape, collection ordering, and diagnostics |
| `siteskin-core/src/test/.../spec/SpecCorpus.kt` | Already exposes parsed bodies, origins, expected results, diagnostics, and rejecting-layer reachability |
| `docs/plan/SPEC-001.md` | Explicitly assigns security normalization and canonical projection to CORE-004 |
| `CLAUDE.md` | Trusted objects must be constructible only through their validator; core remains Android-free |

## Prior decisions

- ADR-004 (recorded through the spec/ADR index) defines exact-origin binding and distrusts
  subdomains; fixtures distinguish host, port, protocol-relative, traversal, and userinfo failures.
- ADR-007 requires unknown actions to drop only their item, enabling forward-minor compatibility.
- ADR-006 requires `toolbar.showDomain` to remain unknown and browser-owned.
- ADR-010 requires localized invalid input to degrade without breaking page rendering.
- SPEC-001 defines canonical JSON independently of Kotlin types; model design must follow it.

## Risks and plan obligations

- **Constructor bypass** → private constructors, no data-class `copy()`, only immutable public views.
- **URI parser ambiguity** → parse components, reject userinfo/traversal underflow, compare exact
  normalized origins, and add focused negative controls for each attack form.
- **Diagnostic drift** → carry code plus JSON pointer, emit by normative stage and document the one
  CORE-002 unknown-field prefix seam.
- **Unicode splitting** → use `BreakIterator` grapheme boundaries for title/subtitle/labels and test
  combining marks/surrogate pairs rather than relying only on ASCII corpus fixtures.
- **Contrast mismatch** → implement sRGB linearization and the exact 8-channel-step algorithm, then
  compare canonical output and focused ratios.
- **Oversized implementation** → split origin, model, colour, and validator concerns into small pure
  files under the same module; Detekt limits remain binding.

## Open questions resolved for planning

- Corpus icons establish the v1 vocabulary actually exercised: `home`, `grid_view`,
  `shopping_cart`, `person`, `call`, `share`, `menu`, with `generic` as the browser fallback.
- Absent colours remain omitted; absent/invalid home URL becomes the serving-origin root. Invalid
  schema-level colours cannot reach this API, while valid short hex expands to uppercase six-digit.
- A malformed serving origin is a caller contract failure, not a manifest diagnostic. The public
  validator returns a rejected outcome without constructing a trusted object; focused tests pin it.

## Question
What the plan needs decided before it can commit to a trust boundary and a file list.

## Origins involved
- serving origin(s)
- asset origin(s), and why they are same-origin

## Manifest-controlled surface
What a website can influence if this ships as scoped.

## Browser-owned remainder
What must stay browser-controlled, and the affordance that enforces it.

## Relevant code
| Path | Why it matters |
|---|---|

## Prior art
ADRs, spec sections, fixtures and tickets that already decided part of this.

## Risks
- risk → the plan's obligation in response

## Open questions
Carried into `/plan` as explicit unknowns, not silently resolved here.
