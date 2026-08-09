# CORE-004: Implementation plan
Status: PLAN_APPROVED

References: `docs/prd/CORE-004.prd.md`, `docs/research/CORE-004.md`, `spec/SPEC.md` §§3 and 5–12.

## Overview and flow

```text
schema-valid JsonObject + browser-observed HTTPS origin
  → validate/canonicalize serving origin
  → project known v1 fields (unknown fields remain CORE-002 evidence)
  → resolve URLs and apply exact-origin/scheme/action allow-lists
  → remove later duplicate ids
  → clamp collections and grapheme-safe strings
  → expand colours and deterministically enforce WCAG contrast
  → SecurityValidationResult(configuration, diagnostics)
```

The validator never performs I/O, Android work, platform dispatch, or schema/byte parsing. An invalid
caller origin or structurally invalid input yields no configuration rather than throwing trusted
partial state outward. Localized manifest failures emit diagnostics and retain the safe remainder.

## Trust and origin boundary

The serving origin is browser-owned input: an absolute HTTPS root with no credentials, query, or
fragment. Core canonicalizes it once and resolves all manifest references against it. Internal URLs
and logo assets compare normalized scheme, ASCII host, and effective port, not text prefixes;
subdomains and alternate ports are cross-origin. A traversal that attempts to move above the origin
root is rejected before RFC normalization can hide it. External navigation is allowed only as an
inert absolute HTTPS URL. Specialized actions accept only their browser-owned value form and never
gain execution or permissions in this ticket.

Every public trusted model constructor is private/internal to the file and the types are ordinary
immutable classes rather than data classes, preventing public `copy()` bypass. The only public
factory path is `SecurityValidator.validate`. The model holds canonical strings and browser tokens,
not `JsonElement`, arbitrary resources, intents, callbacks, or executable code.

Manifest-controlled chrome is limited to bounded text, known icon tokens/generic fallback, bounded
items, and corrected colours. Registrable domain, TLS state, consent, confirmation, asset loading,
and action execution stay browser-owned outside this model.

## Public API and data

- Extend `ManifestDiagnostic` with an optional RFC 6901-style `pointer`, defaulting to `null` so
  CORE-003 source compatibility remains intact.
- Add `SecurityValidationResult(configuration: SiteSkinConfiguration?, diagnostics)` with
  `isValid` meaning that a trusted configuration exists. Drop-item diagnostics are evidence, not a
  whole-result failure.
- Add immutable trusted types for site, branding, toolbar, navigation item, and normalized action.
  Optional members remain absent; `origin` and `site.homeUrl` are always present.
- Expose colours as canonical `#RRGGBB`, icons as browser-owned token strings, collection order as
  immutable lists, and action payload properties needed by CORE-005 without resolving behavior.
- Keep the validator input as `JsonObject` until CORE-002 supplies a DTO adapter. Document the
  schema-valid precondition without trusting it as a security assertion.

## Normalization rules

1. Project only v1 fields. CORE-002 later provides unknown-field diagnostics before this result.
2. Resolve home/internal/logo URLs; drop/fallback on exact-origin failure. Validate external schemes
   and action type/value forms, dropping the containing item where specified.
3. Replace unknown valid icon names with `generic`; remove later duplicate ids per collection.
4. Clamp collections after security drops and duplicate removal, preserving the first N. Clamp
   title/subtitle/labels at grapheme boundaries and emit pointers for each affected value.
5. Expand `#RGB`, uppercase all colours, and correct supplied background against supplied/default
   text and supplied primary against text using the normative target/step algorithm. Preserve absent
   optional values and emit corrections at their colour pointers.
6. Preserve diagnostic emission order by stage and document that CORE-002 prepends unknown-field
   warnings for the full §12 pipeline.

## File-by-file changes

| File | Change |
|---|---|
| `siteskin-core/src/main/kotlin/dev/siteskin/core/ManifestValidation.kt` | Add diagnostic pointer while retaining schema validation behavior |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/model/SiteSkinConfiguration.kt` | Trusted immutable domain graph with non-public constructors |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/validate/OriginPolicy.kt` | Pure HTTPS serving-origin parser, exact-origin resolver, traversal and scheme policy |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/validate/ColorPolicy.kt` | Hex canonicalization, luminance/contrast, deterministic correction |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/validate/SecurityValidator.kt` | Known-field projection, action/icon policy, duplicates, limits, ordered diagnostics, trusted construction |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/SecurityValidatorTest.kt` | Focused boundary, normalization, immutability, Unicode, and negative-control tests |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SecurityConformanceTest.kt` | Compare reachable security fixtures to canonical JSON and expected security diagnostics |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpus.kt` | Expose fixture body parsing/projection helpers needed by conformance test |
| `CLAUDE.md` | Record the stable CORE-004 trust seam and CORE-002 adapter responsibility |

No schema, fixture, registry, Gradle dependency, Android module, or ADR change is planned. If the
published canonical expectations prove internally contradictory, stop and create an explicit spec
follow-up rather than silently editing the contract.

## Tests and negative controls

- Corpus conformance covers fixtures with a canonical result whose rejecting layer is not before
  security. Unknown-field diagnostics are filtered as CORE-002-owned, but those fields remain absent.
- Focused origin tests cover HTTP serving origins, userinfo, alternate/default ports, subdomains,
  protocol-relative references, traversal underflow, fragments, and malformed URIs.
- Focused action tests cover every v1 type and denied schemes; icon tests prove unknown values become
  `generic`, never a resource reference.
- Limit tests cover post-drop ordering, duplicate direction, every collection bound, and grapheme
  clusters. Colour tests cover short hex, canonical case, exact WCAG boundary, both correction
  directions, and the hostile corpus value.
- Reflection/API tests prove there is no public trusted constructor or `copy()` method.
- Record negative controls in the tasklist: temporarily remove exact-origin comparison, scheme
  restriction, duplicate filtering, clamping, icon substitution, and contrast correction; each
  corresponding focused/corpus test must fail before restoration.
- Run `./gradlew :siteskin-core:test`, `./gradlew :siteskin-core:check`, `./gradlew detekt`, and the
  full `bash scripts/pre-commit-check.sh` gate according to task boundaries.

## Security and graceful failure

Only an origin-bound `SiteSkinConfiguration` crosses the trust boundary. Dropping all navigation
still produces a trusted configuration with an empty list. An invalid home URL falls back to the
origin root; an invalid logo disappears; an invalid item disappears. Caller-contract failures yield
no configuration. Downstream browser code can therefore stay in regular mode without a crash and
cannot accidentally execute raw manifest text.

## Rollout / versioning

This implements the already-published 1.0 contract and changes no protocol version. The parsed-tree
input is an explicit interim seam: CORE-002 may add an adapter but may not expose a trusted-model
constructor or change normalization semantics.
