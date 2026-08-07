# SPEC-001: SiteSkin Manifest v1.0 — normative spec, JSON Schema, conformance corpus
Status: PRD_READY

## Context / Problem

Webora's value depends on websites publishing manifests. That makes the manifest format a public
API, and a public API written after the implementation is a description of whatever the code
happened to do — including its accidents.

This ticket writes the contract first. The conformance corpus produced here is what `CORE-002`
through `CORE-005` are then written to satisfy. Nothing in `:siteskin-core` gets implemented until
the fixture that demands it exists.

The order matters for a second reason: `siteskin-lint` (`SPEC-003`) and the browser must agree
exactly. If they diverge, "passes lint" stops meaning "will activate" and the spec stops being a
contract. Sharing one validator is necessary but not sufficient — both must be pinned to the same
corpus.

## Goals

- `spec/SPEC.md` as normative text: discovery, origin binding, versioning, structure, actions,
  limits, branding safety, diagnostics.
- `spec/siteskin-1.0.schema.json` — JSON Schema (draft 2020-12) covering structural validity.
- `spec/fixtures/valid/**` — manifests that must be accepted, with the expected normalized result.
- `spec/fixtures/invalid/**` — manifests that must be rejected, each paired with its expected
  diagnostic code.
- A stable diagnostic code per rejection reason, listed in the spec.
- At least one fixture per diagnostic code. A code with no fixture does not exist.

## Non-goals

- Kotlin implementation — that is `CORE-002..005`.
- The lint CLI — `SPEC-003`.
- Dark-theme variants, localization, badges — deferred to schema 1.1 and recorded as such.
- Signed manifests — `ADR-012`.

## User stories

- As a site owner, I can read one document and publish a valid manifest without reading Kotlin.
- As an implementer of a *different* browser, I can build a conforming implementation from the spec
  and the corpus alone.
- As a Webora maintainer, I can add a validation rule only by adding a fixture that fails first.

## Acceptance criteria

1. `spec/SPEC.md` is normative, uses RFC 2119 keywords, and carries `Status: SPEC_READY`.
2. `spec/siteskin-1.0.schema.json` validates every fixture in `fixtures/valid/` and rejects every
   structurally-invalid fixture in `fixtures/invalid/`.
3. Every diagnostic code in the spec's table has at least one fixture producing it.
4. Every fixture in `invalid/` names its expected code in a sibling `.expected.json`.
5. The corpus covers, at minimum: oversized payload, malformed JSON, unknown major version,
   cross-origin `internal_url`, each denied scheme (`javascript:`, `file:`, `content:`, `intent:`,
   `data:`), unknown action type, cross-origin asset, duplicate ids, every over-limit collection,
   and a contrast-correction case.
6. `spec/SPEC.md` states explicitly that `toolbar.showDomain` is not part of the format, with a
   pointer to `ADR-006`.
7. The Bloom Flowers manifest in `denrzv/bloom-flowers` validates against the schema.
8. Fixtures are plain JSON with no comments — they are parsed by the tests.
9. `bash scripts/pre-commit-check.sh` passes.

## NFR

- **Security/privacy:** the spec must state the trust model — manifest is untrusted input, the
  browser is the security authority — before it describes any field. A reader who skims must not
  come away thinking the site is in control.
- **Reliability/fallback:** every rejection path in the spec names its fallback, and the fallback is
  always regular browser mode.
- **Performance:** limits chosen so a conforming manifest parses in well under a frame.
- **Accessibility:** contrast rules are normative, not advisory, and specify the WCAG level (AA).

## Risks

- **The corpus ossifies a mistake.** A fixture asserting wrong behaviour makes that behaviour a
  requirement. Mitigation: review the invalid corpus against the threat model in `HARDEN-001`
  before freezing, and version the corpus with the schema.
- **Spec and implementation drift** once `CORE-*` starts. Mitigation: the corpus is executed by
  `:siteskin-core:test`, so drift is a red build rather than a stale document.
- **Over-specifying v1.0** makes 1.1 additions awkward. Mitigation: state the unknown-field policy
  (ignore + warn) in v1.0 so additive growth is already legal.

## Open questions

- Should `match` patterns (`/cart/**`) be v1.0 or v1.1? **Decision: v1.0.** Without it, active-item
  detection is exact-path only, which breaks on the first site with a product detail page — and
  retrofitting a matching syntax is a breaking change dressed as an addition.
- Glob syntax or a restricted regex? **Decision: glob.** Regex from an untrusted source invites
  catastrophic backtracking, and the mitigation for that is more code than glob costs.
