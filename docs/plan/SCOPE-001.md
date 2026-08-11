# SCOPE-001: Implementation plan
Status: PLAN_APPROVED

## Overview
Bring four artifacts' claims into line with three owner decisions: hosting moves to
`denrzv.github.io`, the demo fleet narrows to Bloom Flowers, and distribution becomes an APK rather
than a Play listing. Descoped work is retained with its reasoning. No protocol, security or
reference-site content changes.

## Flow
No runtime flow changes. The only behavioural difference a user could observe is that the Home
screen's single suggestion now resolves.

## Data
**Trust boundary: untouched.** The reference site moves origin; the browser treats
`denrzv.github.io` exactly as it treats any other origin — discovery, validation, consent, origin
binding and the manifest cache are unchanged. `defaultSuggestedSites` remains a compiled,
browser-owned, HTTPS-only, resource-id-keyed catalogue that confers no trust.

**Cache keys: unchanged.** `NET-002` keys on canonical origin plus schema version. A different
origin is simply a different key; nothing migrates.

**The manifest does not move.** All five pinned copies stay byte-identical, and the fixture keeps
`bloomflowers.example`. This is the property `SPEC-001` built for and its first real exercise.

## Security
- **Origin binding.** Nothing widens. `denrzv.github.io` is one more origin, validated identically.
- **Identity display.** `github.io` sits in the PSL PRIVATE section, so `denrzv.github.io` is its own
  registrable domain and the browser's chrome shows it rather than merging all GitHub Pages users.
  Guaranteed by `PublicSuffixListTest`, which already asserts `a.github.io` ≠ `b.github.io`.
- **HTTPS.** `ADR-001` requires it for SiteSkin mode; `*.github.io` provides valid TLS with no
  configuration.
- **Allow-lists, spec, corpus.** Untouched — criterion 9 is a "these paths do not appear in the diff"
  assertion.
- **Removing `CNAME` is a security-adjacent cleanup, not cosmetics.** While it pins a domain with no
  DNS, bloom-flowers' Pages deployment 301s every request to an unreachable host — so the published
  reference integration is currently unreachable at every URL.

## File-by-file plan

### `denrzv/webora`

#### Modified: `app/src/main/java/app/webora/browser/browser/SuggestedSite.kt`
Reduce `defaultSuggestedSites` to one entry, `https://denrzv.github.io/`. The `SuggestedSite` type,
`create` validation and `isSafeSuggestion` are unchanged.

#### Modified: `app/src/main/res/values/strings.xml`
Remove `suggested_pixelplay_name`/`_description` and `suggested_journal_name`/`_description`, which
become unreferenced. Keep the Bloom pair; its text — "Fresh flowers delivered today" — is also the
manifest's subtitle and stays accurate.

#### Modified: `docs/ROADMAP.md`
`DEMO-002` moves out of M4's pending list; `M5` becomes **Distribution** with `DIST-001`. A
`Descoped` section lists `DEMO-002` and `PLAY-001..003` with one line each and a pointer to
`BACKLOG.md` for the reasoning.

#### Modified: `docs/BACKLOG.md`
`DEMO-002` and the three `PLAY` entries move under `## Descoped`, each keeping its original text plus
a `Why not now` line and a `What would revive it` line. New `DIST-001` defines the APK deliverable.

#### Modified: `docs/DEVELOPMENT_PLAN.md`
The hosting section is rewritten around what is true: `webora.app` is taken; the origin-root rule
makes a user Pages site the only free option that can host a SiteSkin integration; `denrzv.github.io`
serves it today and is a stopgap. The multi-origin argument is kept but marked **deferred with
`DEMO-002`** — it was never refuted, only postponed. The Play compliance section keeps its analysis
under a descoped heading, with an explicit carve-out that **targetSdk 36 already shipped** and is not
descoped.

#### Modified: `CLAUDE.md`
The `DEMO-001` note's closing paragraph on hosting is corrected to name the live origin and the
stopgap status. The origin-root rule itself stands unchanged — it is what selected the replacement.

#### Modified: `reports/qa/DEMO-001.md`
An addendum, not a rewrite: scenario 23 passes against `https://denrzv.github.io`, with the live
SHA-256, the observed `/catalog` → `/catalog/` redirect on real GitHub Pages, and `siteskin-lint`
exit 0. It restates that scenario 24 (on-device) remains blocked, so the addendum cannot be read as
closing more than it does.

### `denrzv/bloom-flowers`

#### Removed: `CNAME`
The pinned host cannot resolve and makes the deployment unreachable.

#### Removed: `.github/workflows/pages.yml`
`denrzv/denrzv.github.io` is the publisher. Without `CNAME` this workflow would publish a second copy
at a project path that cannot host a SiteSkin integration at all. The reason is recorded in
`README.md` so its absence is not mistaken for an oversight.

#### Modified: `README.md`
Live site → `https://denrzv.github.io`; a short section on where the deployment lives and why the
origin root is required.

#### Modified: `.github/workflows/siteskin-lint.yml`
Default origin input and comments → `https://denrzv.github.io`. The job stops being aspirational: it
now targets an origin that exists, so its comment about waiting for DNS is deleted.

#### Modified: `INTEGRATION.md`
One paragraph only. §1's aside pointed at `CNAME` as the worked example of a custom domain; it
becomes a pointer to the user-Pages arrangement. The guide is otherwise origin-independent.

## Tests

| Test | Asserts |
|---|---|
| `HomeModelsTest` | Unchanged — constructs its own suggestion rather than reading the catalogue |
| `:app:test` | Compiles after the string-resource removal; an unreferenced-string typo surfaces here |
| `BrowserSurfaceConventionsTest` | Still green — the literal and touch-target rules are unaffected, and its `webora.app.src` property must remain untouched |
| `:siteskin-core:test` | Unchanged; proves the corpus and fixture were not disturbed |
| `tools/check-routes.py` | Still passes in bloom-flowers after `CNAME` removal — it derives paths from the manifest, which does not name the domain |
| `bash scripts/pre-commit-check.sh` | Gates every webora commit |

**Live evidence already gathered**, recorded rather than re-run: manifest reachable at the origin
root with `application/json`; live bytes match `BLOOM_FLOWERS_SHA256`; `/catalog`, `/cart`,
`/account` each 301 to their trailing-slash form on real GitHub Pages; `siteskin-lint` exit 0.

**No negative control applies.** This ticket removes no protection and adds no guard; it corrects
claims. The one behavioural change — a suggestion URL — is covered by existing tests.

## Rollout / versioning
No schema, manifest or protocol change; no version implication. Order matters only in bloom-flowers:
`CNAME` and `pages.yml` go together, or a deploy between the two commits would publish a
project-path copy.

## Open questions
None. Renaming `denrzv/webora` and acquiring a domain remain the owner's, later, and neither blocks
anything here.
