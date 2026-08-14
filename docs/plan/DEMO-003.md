# DEMO-003: Implementation plan
Status: PLAN_APPROVED

## Overview

Turn the canonical Bloom Flowers manifest from a compatibility-era example into the deliberate
showcase for the already-shipped SiteSkin semantic vocabulary, then publish the identical bytes and
the explanatory guide in `denrzv/bloom-flowers`, and finally run the unchanged live lint and hosted
Pixel 6 evidence journey. This is a coordinated content/fixture rollout, not a schema or app-UI
change.

The order is intentional: Webora remains the canonical source and proves the intended document
offline; the source-site repository then copies those exact bytes and updates its own checksum and
guide; only the deployed origin can satisfy live lint and visual acceptance. A Webora-only commit is
a valid task checkpoint but is not ticket completion.

## Flow

1. **Author:** change the canonical fixture to use the domain-specific `flower` token and user-facing
   Flowers/Account semantics while retaining the real `/catalog`, `/cart`, and `/account` routes.
2. **Validate:** drive the fixture through the existing total validator, canonical-result corpus,
   exact-origin URL checks, and route matcher. Add a focused showcase-shape assertion that pins the
   ordered label/icon/action choices rather than relying on screenshots to notice a regression.
3. **Normalize:** update the canonical expected result and checksum pin; there is no new
   normalization rule or schema surface.
4. **Publish:** copy the fixture byte-for-byte to `denrzv/bloom-flowers`, update its checksum, and
   update every manifest transcription and semantic-icon explanation in `INTEGRATION.md`.
5. **Deploy/verify:** let that repository's checksum/route/deployment gates finish, run
   `siteskin-lint` against `https://denrzv.github.io`, then dispatch the unchanged Android screenshot
   workflow for clean Home → consent → integrated evidence.

## Trust boundary and origin implications

The manifest remains untrusted remote bytes served from exactly `https://denrzv.github.io`; the logo
and every internal destination resolve within that same scheme/host/port tuple. No project path,
subdomain, sibling Pages tenant, CDN, redirect exception, or registrable-domain equivalence is
introduced. `SiteSkinValidator` retains the complete parse/version/schema/security/normalization
boundary and invalid publication still degrades to regular browsing.

The website may select only bounded text, existing same-origin destinations/patterns, palette/logo,
and existing closed semantic tokens. Webora continues to own token-to-vector mapping, generic
fallback, tint, decorative icon semantics, selected-state computation from the observed path,
canonical origin/TLS display, consent labels and callbacks, typed effect dispatch, and screenshot
ownership. The `phone` action continues to resolve to browser-built dialer data and grants no
permission. Nothing in this plan touches those implementation seams.

## Data

- **Untrusted representation:** JSON in `spec/fixtures/valid/bloom-flowers.json` and its published
  byte-identical copy. Its labels, tokens, routes, and phone payload have no authority before full
  validation.
- **Trusted representation:** the existing `SiteSkinConfiguration` constructed only by
  `SiteSkinValidator`; no DTO/domain/storage change.
- **Canonical expected result:** `bloom-flowers.expected.json`, using the synthetic
  `https://bloomflowers.example` corpus origin so exact resolved values remain deterministic.
- **Integrity key:** SHA-256 of the fixture bytes, pinned independently by Webora's corpus test and
  `denrzv/bloom-flowers`'s `.sha256` file.
- **Storage/cache:** unchanged (`origin + schemaVersion`); a changed document revalidates through
  normal HTTP validators. No migration or version bump.

## Security

- **Origin binding:** retain relative same-origin URLs and the end-to-end URL corpus; no absolute or
  external resource is added.
- **Allow-lists:** use only existing tokens (`home`, `flower`, `shopping_cart`, `person`, `call`) and
  existing action types (`internal_url`, `phone`). Never add a schema enum, drawable lookup, generic
  URI/intent action, or remote icon mechanism.
- **Fallback:** unknown/invalid content retains existing drop/warn/reject dispositions and regular
  browser fallback. There is no production validator change.
- **Identity/consent:** ADR-006's domain/TLS row, UX-008's Back affordance, ADR-011/UX-007's browser
  action hierarchy, and the browser menu section remain unchanged and must still be present in
  hosted evidence.
- **Evidence:** do not alter readiness, dismissal policy, capture deadline, contested-frame checks,
  Inspector isolation, or contact-sheet accounting to obtain a green demonstration.

## File-by-file plan

### Webora repository

#### Modified: `spec/fixtures/valid/bloom-flowers.json`

Rename the catalogue presentation from `Catalog`/`grid_view` to `Flowers`/`flower`, and the profile
presentation from `Profile` to `Account` (including an `account` id so fixture terminology is
coherent). Keep route URLs and match patterns unchanged because those are the paths the static site
actually serves.

#### Modified: `spec/fixtures/valid/bloom-flowers.expected.json`

Mirror the canonical normalized labels, token, and id. Resolved origins/URLs and diagnostics remain
unchanged.

#### Modified: `siteskin-core/src/test/kotlin/dev/siteskin/core/nav/ReferenceIntegrationNavTest.kt`

Update the expected id for account routes and add a focused ordered presentation/action assertion:
Home/home/internal URL, Flowers/flower/internal URL, Cart/shopping_cart/internal URL,
Account/person/internal URL, and Call/call/phone. Read the real fixture through the validator so the
test cannot drift into a sixth copy. Retain the no-match negative control and item-coverage guard.

#### Modified: `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt`

Replace `BLOOM_FLOWERS_SHA256` with the recomputed digest only after the fixture and canonical result
tests pass. Its failure remains the one-sided-edit negative control.

#### Modified: `spec/SPEC.md`

Make the worked example demonstrate the preferred domain-specific `flower` token and the complete
four-item/quick-action showcase rather than preserving a stale compatibility example. Normative
vocabulary and behavior do not change.

#### Modified: `CLAUDE.md`

Record the final reference-integration semantic choices and the requirement that their ids/labels,
tokens, routes, and actions stay aligned across the fixture, external copy, guide, and live origin.

#### Modified: `docs/ROADMAP.md`

Tick `DEMO-003` only after publication, live lint, and hosted evidence complete. Do not mark it done
in the Webora fixture task.

### `denrzv/bloom-flowers` repository

#### Modified: `.well-known/siteskin.json`

Copy the Webora fixture byte-for-byte; do not independently reauthor it.

#### Modified: `.well-known/siteskin.json.sha256`

Pin the identical recomputed digest so the repository's manifest guard accepts only the coordinated
copy.

#### Modified: `INTEGRATION.md`

Update the full manifest transcription and icon discussion. Explain why Bloom chooses `flower` for
its domain-specific catalogue, `shopping_cart`, `person`, and `call`; distinguish the preferred
`catalog` token from compatibility `grid_view`; state that labels and route paths need not equal
semantic tokens but must describe the real site; and preserve all security limitations.

No HTML/CSS/framework change is planned: the deployed routes and visual site already match.

## Tests

| Check | Assertion | Negative control |
|---|---|---|
| Focused `ReferenceIntegrationNavTest` | Exact ordered showcase labels/icons/action types come from the validated fixture; all deployed paths select the intended id | Restore `grid_view`, rename Account back to Profile, or mismatch an expected action ⇒ focused assertion fails |
| Existing route matcher cases | `/`, catalogue, cart, and account route shapes select correctly | Remove a `match` or make every item match broadly ⇒ positive or no-match control fails |
| `SecurityConformanceTest` / corpus | Fixture validates with no diagnostics and equals its canonical normalized result | One-sided expected-result change fails |
| `SpecCorpusTest` checksum | Canonical bytes equal the cross-repository digest | Change fixture without constant ⇒ fails |
| `OriginCorpusTest` | Every URL remains exact-origin resolvable | Cross-origin URL ⇒ fails |
| External checksum and route checks | Published-source bytes match and every named path/logo exists | Edit served copy alone or hide a route ⇒ fails |
| Guide JSON validation | Every JSON block remains parseable/accepted and the full block matches publication | Stale `grid_view` transcription or malformed JSON ⇒ fails |
| `:siteskin-lint:run --args="https://denrzv.github.io"` | Deployed origin serves an accepted final manifest | Any HTTP/deployment/manifest error ⇒ nonzero; never reinterpret as a warning |
| Hosted screenshot workflow | Three uncontested canonical frames, complete contact sheet, intended consent/integrated presentation | Other-window ownership, missing PNG, or tile mismatch ⇒ run fails |
| `bash scripts/pre-commit-check.sh` | Whole Webora gate | Required before each Webora task commit |

The local environment has no `/dev/kvm` or connected device; per repository instructions it will
compile Android instrumentation but will not provision a software emulator. Runtime evidence uses
the existing hosted workflow after publication.

## Rollout / versioning

No SiteSkin version, schema, diagnostic, application version, or cache migration changes. This is a
site document choosing values already valid in `1.0`.

Land the Webora fixture checkpoint first, then the external copy/checksum/guide checkpoint, then
deploy and validate live. Because the repositories deliberately fail one-sided edits, the Webora PR
may remain red at the checksum pin only if its own constant is omitted; it must not omit the constant
to simulate coordination. Ticket/roadmap completion waits for the external commit and hosted run.

## Open questions

1. Authenticated owner access is required to commit and deploy `denrzv/bloom-flowers`; this managed
   checkout cannot perform that account operation. If it remains unavailable after the Webora task,
   stop at the checkpoint and report the exact remaining files/commands rather than claiming rollout.
2. The Pages origin returned HTTP 403 during research. Live lint after publication is the deciding
   check; no code-side workaround or alternate origin is planned.
