# DEMO-003: Research
Status: RESEARCH_READY

## Question

Which checked-in and published artifacts define Bloom Flowers, which already-shipped protocol/UI
capabilities the showcase should exercise, and which repository and hosted-device boundaries must
the implementation plan respect?

## Origins involved

- **Page and manifest origin:** `https://denrzv.github.io`. `LiveSiteScreenshotTest`, the suggested
  site, and the hosted workflow all use this exact HTTPS origin. The manifest discovery URL is
  `https://denrzv.github.io/.well-known/siteskin.json`; the project-style
  `denrzv.github.io/bloom-flowers/` URL cannot work because discovery is origin-rooted.
- **Manifest source repository:** public repository `denrzv/bloom-flowers`. Its default-branch
  `.well-known/siteskin.json` is currently byte-identical to this repository's
  `spec/fixtures/valid/bloom-flowers.json` and its checksum file pins
  `ed0ca884eac5f5c0014454266a16f70487980ce7e60f29184d3b6afcf2d96e1d`. Its `INTEGRATION.md`
  transcribes a shortened copy of the same manifest and still describes only the older drawn set.
- **Brand asset origin:** `/assets/siteskin/logo.png` resolves at the same `denrzv.github.io` origin.
  There is no CDN, subdomain, or redirect exception. `ADR-004` requires full scheme/host/port
  equality and `NET-003` already bounds and verifies the asset.
- **No second demo origin:** `DEMO-002` remains descoped. The private-suffix rule for `github.io`
  also means a sibling/project host must never be treated as equivalent.

The public GitHub API exposes the source repository, but this checkout has no Git remote and no
GitHub authentication. Direct Pages retrieval returned HTTP 403 during research. The plan therefore
must separate changes that can be committed to Webora from the coordinated publication step and
must not claim live acceptance before the owner lands/deploys the matching source-repository edit.

## Manifest-controlled surface

The validated manifest may request:

- the bounded site name, short name, home URL, toolbar title/subtitle, palette, and same-origin logo;
- up to five ordered navigation items with bounded labels, closed semantic icon tokens, typed
  actions, and bounded handwritten path patterns;
- up to five quick actions with the same bounded presentation and typed action model.

For this ticket the intended site-authored changes are narrower: use `flower` rather than the
compatibility `grid_view` token for the flower catalogue, name the account destination as Account,
and keep `home`, `shopping_cart`, `person`, and `call` for their existing semantics. The fixture's
routes already describe the static deployment correctly: `/`, `/catalog[/…]`, `/cart[/…]`, and
`/account[/…]`. The `phone` payload continues to open the dialer rather than place a call.

No schema or allow-list addition is necessary. `SPEC.md` §5 and `SecurityValidator.ICONS` already
contain `home`, `catalog`, `flower`, `grid_view`, `shopping_cart`, `person`, and `call`; `UX-005`
already maps each final token to a bundled vector with generic fallback.

## Browser-owned remainder

- Manifest bytes still pass the bounded `SiteSkinValidator` pipeline and exact-origin URL
  resolution. Parsing success alone grants nothing.
- Webora chooses the bundled drawable for each semantic token. The site cannot provide a resource
  id, font glyph, SVG, remote icon, content description, tint, selected state, or fallback.
- `NavMatcher` derives selection from the browser-observed committed HTTP(S) path. The manifest
  supplies patterns but cannot force a default selection when nothing matches.
- The canonical origin, registrable-domain/TLS row, Back control, browser menu section, consent
  labels/order/actions, action resolution, intent construction, and external-navigation confirmation
  remain browser-owned under ADR-004, ADR-006, ADR-011, and UX-008.
- Icons remain decorative; bounded labels and selected-state semantics are the accessibility
  contract. The consent projection shows bounded title/count information, not item labels or action
  payloads.
- Canonical evidence remains owned by the screenshot guard. Another focused window, an Inspector
  overlay, a missing frame, or an incomplete contact sheet fails acceptance rather than being edited
  out or silently dismissed.

## Relevant code

| Path | Why it matters |
|---|---|
| `spec/fixtures/valid/bloom-flowers.json` | Canonical authored manifest; copied one-way into `denrzv/bloom-flowers`. |
| `spec/fixtures/valid/bloom-flowers.expected.json` | Canonical normalized result that must change with labels/icons/ids. |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt` | Pins canonical fixture bytes to the external repository's checksum. |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/nav/ReferenceIntegrationNavTest.kt` | Executes the real fixture against all deployed route shapes and a no-match control. |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/origin/OriginCorpusTest.kt` | Proves every reference URL stays inside its serving origin. |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/SecurityValidator.kt` | Closed semantic-icon allow-list; no new vocabulary is needed. |
| `app/src/main/java/app/webora/browser/siteskin/SiteSkinChrome.kt` | Closed token-to-vector map and decorative rendering shipped by `UX-005`. |
| `app/src/androidTest/java/app/webora/browser/visual/LiveSiteScreenshotTest.kt` | Canonical Home → consent → integrated journey, exact origin assertion, and three frames. |
| `.github/workflows/android-screenshots.yml` | Hosted Pixel 6 / API 33 execution and complete artifact/contact-sheet checks. |
| `scripts/android-screenshot-ci.sh` and `scripts/android-emulator-ready.sh` | Fail-closed capture ownership and quiet-device readiness; neither should change. |
| `CLAUDE.md` | Normative reference-integration copy direction and trust/UI decisions. |
| `docs/ROADMAP.md` | Ticket completion ledger; update only after all acceptance evidence is honest. |
| `denrzv/bloom-flowers:.well-known/siteskin.json` | Published-source copy; must remain byte-identical to the Webora fixture. |
| `denrzv/bloom-flowers:.well-known/siteskin.json.sha256` | External checksum guard; must pin the recomputed fixture digest. |
| `denrzv/bloom-flowers:INTEGRATION.md` | Site-owner adoption guide and manifest transcription requiring semantic-choice documentation. |

## Prior art

- `DEMO-001` established the static site, origin-root deployment, five-copy/checksum discipline,
  route test, and adoption guide. Its plan makes Webora's fixture the one-way source.
- `UX-005` shipped the final closed semantic icon vocabulary and browser-owned vector mapping,
  including the domain-specific `flower` token and `catalog` preference over compatibility
  `grid_view`; its QA is `QA_PASSED`.
- `UX-007` shipped the full-width Allow / Not now / Never consent hierarchy and 200% font-scale
  behavior; its QA is `QA_PASSED`.
- `DEVX-003` removed the Inspector from canonical composition while preserving it in browser-owned
  debug menus; its QA is `QA_PASSED`.
- `CI-002`, `DEVX-002`, and `CI-006` provide guarded captures, contact-sheet review, and calibrated
  device-quiet readiness. CI-006 recorded two consecutive clean three-frame hosted runs.
- `ADR-004`, `ADR-006`, `ADR-011`, and `ADR-013` fix the origin, identity, consent, and browser-token
  boundaries. `SPEC.md` §§5, 7, 8, 11, 13, and 14 define icons, matching, limits, security, corpus,
  and site-owner validation.

## Risks

- **Cross-repository drift** → recompute the digest, update the Webora constant, and require the
  external manifest plus checksum to move together before live lint/screenshot acceptance.
- **Guide drift** → validate every JSON block in `INTEGRATION.md` and explicitly explain why
  `flower`, `shopping_cart`, `person`, and `call` are semantic requests rather than asset names.
- **A valid but inaccurate site map** → retain the positive route table, negative no-match cases,
  and full navigation-item coverage while updating any renamed id.
- **Compatibility token accidentally remains the showcase** → add an assertion over the canonical
  fixture's exact ordered labels/icons/actions so a future visually generic regression is loud.
- **External access is mistaken for completion** → report deployment/lint/hosted evidence as blocked
  until authenticated owner actions produce durable commits/runs; never weaken checks or claim the
  inaccessible live bytes match.
- **Emulator evidence becomes the implementation task** → do not modify capture policy for a demo
  content ticket; use the existing hosted workflow unchanged after deployment.

## Open questions

1. Who will land the coordinated `denrzv/bloom-flowers` manifest/checksum/guide commit? This checkout
   has neither that repository nor authenticated write access. It is a rollout dependency, not a
   reason to merge a one-sided fixture change as though publication were complete.
2. Direct Pages access returned HTTP 403 during research. After deployment, live lint and the hosted
   journey must establish whether this is transient/environment-specific or a current hosting fault.
