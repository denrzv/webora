# DEMO-001: Implementation plan
Status: PLAN_APPROVED

## Overview
Turn `denrzv/bloom-flowers` from three files into the reference integration: a framework-free static
site that serves every path its manifest names, an adoption document, an offline CI check that the
two agree, and a custom-domain Pages deployment. Fix the one authoring defect the research found —
`home` has no `match`, so the reference integration's own landing page highlights nothing — across
all five pinned copies. Point the browser's compiled suggestion catalogue at the origin the site is
actually deployed to.

The work is split across two repositories, both on `claude/bloom-flowers-reference-y5ybs7`:

- **`denrzv/webora`** — the manifest fixture and its expected result, the SHA pin, `SuggestedSite`,
  the roadmap tick, and the AIDD artifacts.
- **`denrzv/bloom-flowers`** — the site, the logo, `INTEGRATION.md`, the route-conformance check,
  the Pages deploy and the `CNAME`.

## Flow

There is no new runtime flow. The existing one is exercised end-to-end for the first time:

- **discovery** — `NET-001` requests `https://bloomflowers.webora.app/.well-known/siteskin.json`
  concurrently with the page load. This is the reason the deployment must be an origin root and not
  a project-Pages path; a `CNAME` is a functional requirement, not cosmetics.
- **validation** — the served bytes are byte-identical to `spec/fixtures/valid/bloom-flowers.json`,
  so `SiteSkinValidator` produces the canonical result already pinned in `.expected.json`, with no
  diagnostics.
- **normalization** — nothing is truncated or corrected: four navigation items against a cap of
  five, one quick action against a cap of five, and a palette that already clears WCAG AA against
  `#2B1B24`.
- **UI state** — `SKIN-004` activates after first-use consent; `SiteSkinChromeModel` asks
  `NavMatcher` for the active item on every committed URL. **The behaviour this ticket changes** is
  that `/` now selects Home instead of selecting nothing.

## Data

**Trust boundary — unchanged, and that is the deliverable.** The reference site is remote untrusted
input like any other origin. It gets a DTO, schema validation, security validation and an
origin-bound `SiteSkinConfiguration` on exactly the same path as a hostile manifest. Nothing in this
ticket adds a code path, a field, an allow-list entry or an exemption for it. The one place the
reference site is named inside the browser is `defaultSuggestedSites` — a compiled, browser-owned,
HTTPS-only, resource-id-keyed catalogue that `BROWSE-003` already established remote content cannot
influence. Being listed there confers no trust: the site is discovered, validated and consented to
identically whether it was reached from a suggestion or typed in.

**Storage / cache keys — unchanged.** `NET-002` keys on canonical origin plus exact schema version.
Changing the manifest body does not change its `schemaVersion`, so a client holding a cached `1.0`
entry for this origin re-validates it by `ETag`/`Last-Modified` in the normal way. No migration.

**The five pinned copies** are the only data this ticket moves, and they move together:

| Copy | Repository |
|---|---|
| `spec/fixtures/valid/bloom-flowers.json` | webora |
| `spec/fixtures/valid/bloom-flowers.expected.json` | webora |
| `BLOOM_FLOWERS_SHA256` in `SpecCorpusTest.kt` | webora |
| `.well-known/siteskin.json` | bloom-flowers |
| `.well-known/siteskin.json.sha256` | bloom-flowers |

Both checksums are **recomputed with `sha256sum` from the file**, never transcribed. The two guards
are independent by design (`manifest-guard.yml` documents why), so a partial update fails loudly on
whichever side moved — but only after the fact, which is why this is one task per repository and not
one edit among several.

## Security

**Origin binding.** The deployment origin `https://bloomflowers.webora.app` and the fixture origin
`https://bloomflowers.example` stay separate. The fixture keeps the RFC 2606 reserved name, which is
correct for a conformance artifact and must not drift to a real host; the deployment origin appears
in `CNAME`, `SuggestedSite`, `INTEGRATION.md` and the manual lint command, and nowhere in
`spec/`. The manifest body itself names no origin at all — every URL in it is origin-relative, which
is exactly why one file can be both the fixture and the served copy.

**Allow-lists.** Untouched. The reference uses `internal_url` and `phone`, both already allow-listed;
icons `home`, `grid_view`, `shopping_cart`, `person`, `call`, all already in the icon set. No
diagnostic code, schema constraint or action type is added. Criterion 9 is a "these files do not
appear in the diff" assertion.

**Fallback on failure.** The site must be fully usable with no SiteSkin at all — its own header nav,
layout and content cannot depend on the manifest being fetched, parsed or accepted. This is
`ADR-010` demonstrated rather than asserted: a visitor in Chrome sees a working flower shop.

**What the reference site must not do**, because it is the artifact most likely to be copied
verbatim: no third-party origin of any kind (no CDN, no hosted font, no icon set, no analytics), no
cookie, no service worker, no form submission, no `localStorage`. Everything is same-origin static
bytes. `INTEGRATION.md` states what a manifest *cannot* obtain — Android permissions, suppression of
the domain or TLS indicator, cross-origin assets, arbitrary intents — with the same weight it gives
the capability list, because a document that lists only capabilities teaches site owners to expect
more than the protocol grants.

**Route layout is a security-adjacent choice, not a styling one.** Directory layout
(`catalog/index.html`) is the only form that resolves on every static host examined. The published
`match` arrays already tolerate both the pre-redirect path and the redirect target — traced in the
research note and to be pinned by test, including the `**`-matches-zero-segments case that makes
`/cart/**` select `/cart`.

## File-by-file plan

### `denrzv/webora`

#### Modified: `spec/fixtures/valid/bloom-flowers.json`
Add `"match": ["/"]` to the `home` entry, in the same position the other items carry `match` (after
`action`). Nothing else changes.

#### Modified: `spec/fixtures/valid/bloom-flowers.expected.json`
Mirror it into the canonical result's `bottomNavigation[0]`. `match` is origin-relative and is not
resolved to absolute, matching how the other three items already appear.

#### Modified: `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt`
`BLOOM_FLOWERS_SHA256` ← recomputed. The KDoc pointing at the other repository stays.

#### New: `siteskin-core/src/test/kotlin/dev/siteskin/core/nav/ReferenceIntegrationNavTest.kt`
The route table from the research note, executed against the real fixture: for each of `/`,
`/catalog`, `/catalog/`, `/catalog/roses`, `/cart`, `/cart/`, `/account`, `/account/orders`, assert
the id `NavMatcher` selects. Reads `spec/fixtures/valid/bloom-flowers.json` through the existing
`siteskin.spec.dir` wiring so editing the fixture reruns it.

This is the test that would have caught the `home` defect, and it is the reason to write it here
rather than only in the other repository's CI: `NavMatcher` lives in webora, and the assertion is
about what the browser does with the reference manifest.

#### Modified: `app/src/main/java/app/webora/browser/browser/SuggestedSite.kt`
`https://bloomflowers.example/` → `https://bloomflowers.webora.app/`. One string; `isSafeSuggestion`
already constrains the shape, and `HomeModelsTest` uses its own literals rather than the catalogue.

#### Modified: `docs/ROADMAP.md`
Tick `DEMO-001`.

#### Modified: `CLAUDE.md`
A "Reference integration (DEMO-001)" section: the five pinned copies and the recompute rule, why the
deployment must be an origin root, the directory-layout decision and the `**`-zero-segment
consequence, and the rule that the reference site takes no dependency and no exception.

### `denrzv/bloom-flowers`

#### New: `index.html`, `catalog/index.html`, `cart/index.html`, `account/index.html`
Mockup screen 3 on the landing page: header with in-page navigation, hero, Best Sellers grid, footer.
Catalog is the product grid, cart a static example basket, account a static profile. Semantic
landmarks, a skip link, focus-visible affordances, no meaning carried by colour alone. Every page
cross-links to the others so the site works with no SiteSkin present.

#### New: `assets/site.css`
One stylesheet, the manifest's palette as custom properties so the correspondence between
`branding.*Color` and the page is legible to a reader. No reset framework, no external font.

#### New: `assets/siteskin/logo.png`
512×512, under 64 KB, generated deterministically by a committed script so it can be regenerated and
reviewed rather than arriving as an opaque binary. Well inside `NET-003`'s 512 KiB / 1024-per-axis /
1,048,576-pixel budget.

#### New: `tools/make-logo.py`
Pure-Python PNG writer (`zlib` + `struct`), no third-party dependency. Deterministic output so the
committed file can be reproduced byte-for-byte.

#### New: `tools/check-routes.py`
The offline conformance check. Reads `.well-known/siteskin.json`, extracts every `site.homeUrl`,
every `internal_url`, every `match` pattern and `branding.logoUrl`, maps each to the file the
deployment would serve, and fails if it is missing. Derived from the manifest and the filesystem —
never a hand-written list of expected paths, which is the same assertion the corpus already fails to
make. Also asserts the logo's PNG signature, dimensions and size against the `NET-003` budget.

#### Modified: `.github/workflows/manifest-guard.yml`
Add a `routes` job running `tools/check-routes.py`. Offline, no network, no cross-repo checkout, so
it is green from the first commit and stays that way.

#### New: `.github/workflows/pages.yml`
`actions/upload-pages-artifact` + `deploy-pages` from the repository root on push to the default
branch.

#### New: `.github/workflows/siteskin-lint.yml`
`workflow_dispatch` only. Checks out `denrzv/webora`, runs
`./gradlew :siteskin-lint:run --args="https://bloomflowers.webora.app"`. Manual by design: a
scheduled job would report red until DNS exists, and a job that is expected to be red teaches people
to ignore it.

#### New: `CNAME`
`bloomflowers.webora.app`.

#### New: `INTEGRATION.md`
The deliverable. Where the manifest goes and why it is origin-rooted; the minimum viable manifest;
each optional block and what it changes on screen; `match` and active-state resolution; validating
with `siteskin-lint` and reading its exit code; what the browser refuses; what a manifest never
grants; and the first-use consent the site owner cannot skip.

#### New: `README.md`
Short — what the repository is, where the site is served, and a pointer to `INTEGRATION.md` and to
`SPEC.md` in webora. It is not the adoption document and must not grow into one.

## Tests

| Test | Asserts | Negative control |
|---|---|---|
| `ReferenceIntegrationNavTest` (new, webora) | The published manifest selects `home` on `/`, `catalog` on `/catalog`, `/catalog/` and `/catalog/roses`, `cart` on `/cart` and `/cart/`, `profile` on `/account` and `/account/orders` | Remove `"match": ["/"]` from the fixture ⇒ the `/` case fails. This is the defect the ticket exists to fix, so the control is the fix itself |
| `SpecCorpusTest` SHA pin (existing) | Fixture bytes match `BLOOM_FLOWERS_SHA256` | Update the fixture without the constant ⇒ fails |
| `OriginCorpusTest.theBloomFlowersManifestResolvesEndToEnd` (existing) | Every URL still resolves inside the origin after the edit | — |
| `tools/check-routes.py` in CI (new, bloom-flowers) | Every manifest-named path resolves to a served file; the logo is a real PNG inside the `NET-003` budget | Rename `catalog/index.html` ⇒ fails. Recorded in the tasklist |
| `manifest-guard.yml` checksum (existing) | The served copy matches its `.sha256` | Edit one of the two ⇒ fails |
| `HomeModelsTest` (existing) | Unaffected — it constructs its own suggestion rather than reading the catalogue | — |

`bash scripts/pre-commit-check.sh` gates every webora commit. The bloom-flowers repository has no
Gradle build; its gate is its own workflows, run locally before commit.

**Not gated, recorded as evidence:** on-device rendering of mockup screen 3, the consent dialog, and
the tab-highlighting transitions. `AGENTS.md` rules out provisioning a software-only emulator in
managed cloud without `/dev/kvm`; QA records this as an environment limitation, not a failure.

## Rollout / versioning

No schema version change. The manifest edit is additive within `1.0`: adding an OPTIONAL `match`
array to one navigation item. Under `SPEC.md` §4 this is not a protocol change at all — it is a
document change by one site owner, and the fact that this particular document is also a conformance
fixture is a repository concern, not a compatibility one. `spec/versions.json` and
`spec/diagnostics.json` are untouched.

Deployment order matters once DNS exists: the `CNAME` and Pages workflow land with the site, so the
first successful deploy already serves a manifest whose paths all resolve. Nothing in the browser
requires a coordinated release — `SuggestedSite` pointing at a not-yet-resolving host degrades to a
normal navigation failure, which `BROWSE-004` already handles.

## Open questions

1. **DNS.** Registering `webora.app` and pointing `bloomflowers` at GitHub Pages is an account
   operation outside both repositories. Criterion 8 (`siteskin-lint` exits 0 against the deployed
   origin) is **blocked**, with the exact command recorded, and the workflow that runs it is manual
   so it cannot report green prematurely. Everything else in the ticket is verifiable today.
2. **`menu[]` stays unexercised.** No demo populates it, so the SiteSkin menu shows only its
   browser-owned section. Deferred to `DEMO-002`; adding it here would change the fixture a second
   time for a surface this ticket's acceptance criteria do not cover.
