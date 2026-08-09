# CORE-004: Security validation and normalization
Status: PRD_READY

## Context / Problem
Schema validation proves only that parsed website-controlled JSON has the v1 shape. Before SiteSkin
chrome can use it, browser-owned policy must bind URLs to the manifest's serving origin, constrain
actions and icons to allow-lists, clamp attacker-controlled layout content, and correct hostile
colours. The result needs a trusted type that cannot be constructed without those checks.
## Goals
- Convert a schema-valid manifest plus its HTTPS serving origin into an immutable trusted
  `SiteSkinConfiguration`.
- Implement the security and normalization order fixed by `SPEC.md` §12, with stable diagnostics.
- Pin the implementation to every reachable security-layer conformance fixture.
- Make illegal trusted states unrepresentable at the public API boundary.
## Non-goals
- Byte limiting, JSON parsing, DTO serialization, and unknown-field discovery owned by CORE-002.
- Structural/version validation owned by CORE-003; callers must provide schema-valid JSON.
- Turning normalized actions into platform behavior, owned by CORE-005.
- Network fetching, redirects, asset MIME/byte/dimension validation, UI rendering, or Android code.
## User stories
1. As a browser user, I want a site manifest to affect only its exact HTTPS origin so delegated
   subdomains, changed ports, and hostile URI schemes cannot acquire browser capabilities.
2. As a site owner, I want a localized mistake to drop or normalize only the affected value and
   produce a stable diagnostic rather than disable the whole integration.
3. As a core consumer, I want possession of `SiteSkinConfiguration` to prove browser-owned security
   validation completed, without remembering a second boolean or calling order.
4. As a second implementer, I want canonical results identical to the published corpus.
## Acceptance criteria
1. A public `SecurityValidator` accepts a schema-valid parsed manifest and an exact HTTPS serving
   origin, and only it can construct the public immutable `SiteSkinConfiguration` model.
2. Relative and absolute internal URLs resolve canonically against the serving origin; cross-origin,
   protocol-relative hostile, userinfo, port-changing, and non-HTTPS serving-origin cases cannot
   enter the trusted model and emit the specified diagnostic.
3. `external_url` accepts HTTPS only; all other URL-bearing values follow the `https`, `mailto`,
   `tel`, and `geo` browser allow-list as applicable. Unknown actions drop their item.
4. Cross-origin logo assets are removed, including subdomains, and unknown icon names normalize to
   the generic browser icon without treating manifest text as a resource reference.
5. Duplicate ids drop later occurrences; collection and string limits retain the first allowed
   values, truncate without splitting grapheme clusters, and emit diagnostics in normalization order.
6. Colours normalize to uppercase `#RRGGBB`; hostile manifest colours are deterministically corrected
   to the WCAG AA targets and record `SS-W-CONTRAST-CORRECTED`.
7. Every security-layer fixture reachable after version/schema validation produces its published
   canonical JSON and diagnostics; focused negative controls fail when origin, scheme, duplicate,
   limit, icon, or contrast protections are removed.
8. Core remains pure JVM with no Android dependency and all security decisions are deterministic.
9. `bash scripts/pre-commit-check.sh` passes.
## NFR
- Security/privacy: treat every manifest value as untrusted; exact-origin and allow-list checks are
  mandatory, and trusted types have no bypass constructor or `copy()` escape hatch.
- Reliability/fallback: localized defects degrade by dropping or normalizing values; no validation
  failure throws into browser rendering or converts an empty navigation collection into rejection.
- Performance: validation is linear in bounded manifest content and performs no I/O or Android work.
- Accessibility: normalized colour pairs meet WCAG AA (4.5:1 body text, 3:1 UI/large text).
## Risks
- URI parsers can reinterpret protocol-relative URLs, userinfo, ports, or path traversal; compare
  normalized scheme/host/effective port rather than strings.
- JVM string indices can split surrogate pairs or grapheme clusters; truncation needs a Unicode
  boundary mechanism rather than `take(limit)`.
- The current parser/DTO ticket is pending, so this ticket must use the parsed `JsonElement` seam
  without accidentally absorbing CORE-002 ownership.
- Diagnostics from multiple normalization stages can drift from the normative emission order.
## Open questions
- Which browser-owned icon vocabulary and generic fallback token are already pinned by the corpus?
- Which defaults are required for absent optional colours and home URL in the canonical model?
