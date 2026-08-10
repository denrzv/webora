# DEMO-001: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/DEMO-001.prd.md`
- Research: `docs/research/DEMO-001.md`
- Plan: `docs/plan/DEMO-001.md`

Two repositories, both on `claude/bloom-flowers-reference-y5ybs7`. Tasks are ordered so the
manifest change lands and is proven in webora **before** the served copy moves, and so the site
exists before the check that reads it.

## Tasks

- [ ] TASK-1: Pin the reference manifest's route contract by test
  - New: `siteskin-core/src/test/kotlin/dev/siteskin/core/nav/ReferenceIntegrationNavTest.kt`
  - Acceptance: reads `spec/fixtures/valid/bloom-flowers.json` through the existing
    `siteskin.spec.dir` wiring — not a copied literal — validates it against
    `https://bloomflowers.example`, and asserts the id `NavMatcher.activeItem` selects for `/`,
    `/catalog`, `/catalog/`, `/catalog/roses`, `/cart`, `/cart/`, `/account`, `/account/orders`.
    The `/` case asserts `home`, and **fails at this point**, which is the finding. Written first
    per `/implement`; TASK-2 makes it pass.
  - Tests: `ReferenceIntegrationNavTest`
  - Note: also asserts a path the manifest does not describe (`/about`) selects nothing, so the test
    cannot pass by making everything match.

- [ ] TASK-2: Give `home` a `match`, across all three webora copies
  - Modified: `spec/fixtures/valid/bloom-flowers.json`,
    `spec/fixtures/valid/bloom-flowers.expected.json`,
    `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt`
  - Acceptance: `"match": ["/"]` on `bottomNavigation[0]` in both the body and the canonical result;
    `BLOOM_FLOWERS_SHA256` recomputed with `sha256sum` from the file rather than transcribed;
    `ReferenceIntegrationNavTest` now green including `/`; `SpecCorpusTest` and
    `OriginCorpusTest.theBloomFlowersManifestResolvesEndToEnd` still green.
  - Tests: `ReferenceIntegrationNavTest`, `SpecCorpusTest`, `OriginCorpusTest`
  - Negative control: revert the `.json` edit alone ⇒ `SpecCorpusTest`'s SHA assertion must fail;
    revert the constant alone ⇒ it must fail from the other side. Record both.

- [ ] TASK-3: Point the browser's suggestion catalogue at the deployment origin
  - Modified: `app/src/main/java/app/webora/browser/browser/SuggestedSite.kt`
  - Acceptance: Bloom Flowers reads `https://bloomflowers.webora.app/`, consistent with the
    `pixelplay.` and `journal.` entries; `.example` survives only in `spec/fixtures/`, where a
    reserved documentation name is correct. No string moves into a composable — the catalogue keeps
    its resource ids.
  - Tests: `HomeModelsTest` (unchanged — it constructs its own suggestion), `:app:test`

- [ ] TASK-4: The reference site — pages, stylesheet, and the logo
  - Repository: `denrzv/bloom-flowers`
  - New: `index.html`, `catalog/index.html`, `cart/index.html`, `account/index.html`,
    `assets/site.css`, `assets/siteskin/logo.png`, `tools/make-logo.py`
  - Acceptance: directory layout so `/catalog`, `/cart` and `/account` resolve on any static host;
    landing page is mockup screen 3 (hero, Best Sellers grid, `#D94F8A`); every page carries the
    shared header, in-page navigation and footer, and is fully usable with no SiteSkin present; no
    third-party origin, cookie, form submission, service worker or storage anywhere; semantic
    landmarks, a skip link, visible focus, and no meaning carried by colour alone. The logo is a
    512×512 PNG under 64 KB, generated deterministically by the committed script.
  - Tests: none yet — TASK-5 is the check, and it is written against a site that already exists so
    that it can be run in both directions.

- [ ] TASK-5: Offline route conformance in CI
  - Repository: `denrzv/bloom-flowers`
  - New: `tools/check-routes.py`
  - Modified: `.github/workflows/manifest-guard.yml`
  - Acceptance: derives every checked path from `.well-known/siteskin.json` — `site.homeUrl`, every
    `internal_url`, every `match` pattern's literal prefix, and `branding.logoUrl` — and maps each
    to the file the deployment would serve; fails on a missing one. No hand-written list of expected
    paths. Also asserts the logo's PNG signature, that it is 512×512, and that it is inside
    `NET-003`'s 512 KiB / 1024-per-axis / 1,048,576-pixel budget. Runs with no network. Wired as a
    job in `manifest-guard.yml`.
  - Tests: the script itself, run locally.
  - Negative control: `git mv catalog/index.html catalog/index.html.bak` ⇒ the check must fail
    naming `/catalog`; restore ⇒ green. Record the output.

- [ ] TASK-6: The served manifest copy and its checksum
  - Repository: `denrzv/bloom-flowers`
  - Modified: `.well-known/siteskin.json`, `.well-known/siteskin.json.sha256`
  - Acceptance: byte-identical to webora's `spec/fixtures/valid/bloom-flowers.json` after TASK-2 —
    verified by comparing hashes across the two checkouts, not by eye; `.sha256` recomputed with
    `sha256sum` from the file; `sha256sum --check` passes in `.well-known/`; the hash equals
    `BLOOM_FLOWERS_SHA256`.
  - Tests: `sha256sum --check`, `tools/check-routes.py`
  - Negative control: edit one byte of the served copy ⇒ `sha256sum --check` fails. Record it.

- [ ] TASK-7: Deployment — Pages, custom domain, and the manual lint job
  - Repository: `denrzv/bloom-flowers`
  - New: `CNAME`, `.github/workflows/pages.yml`, `.github/workflows/siteskin-lint.yml`
  - Acceptance: `CNAME` pins `bloomflowers.webora.app` so the site is served from an origin root
    where `/.well-known/siteskin.json` is reachable — the reason a project-Pages path cannot host
    this at all; `pages.yml` deploys the repository root on push to the default branch with the
    least permissions that work; `siteskin-lint.yml` is `workflow_dispatch` only, checks out
    `denrzv/webora`, and runs
    `./gradlew :siteskin-lint:run --args="https://bloomflowers.webora.app"`. Manual by design: a
    scheduled job would be red until DNS exists, and a job expected to be red teaches people to
    ignore it.
  - Tests: workflow YAML parses; the lint job is not runnable until DNS exists and is recorded as
    the ticket's one blocked criterion.

- [ ] TASK-8: `INTEGRATION.md` and the repository README
  - Repository: `denrzv/bloom-flowers`
  - New: `INTEGRATION.md`, `README.md`
  - Acceptance: `INTEGRATION.md` covers, with the reference site as the worked example — where the
    manifest goes and why it is origin-rooted; the minimum viable manifest; each optional block and
    what it changes on screen; `match` and active-item resolution including the exact-beats-glob and
    nothing-matches-means-nothing-active rules; validating with `siteskin-lint` and reading its exit
    code; what the browser refuses (non-HTTPS, cross-origin assets, unknown action type, unknown
    major version); and what a manifest never grants (Android permissions, chrome that hides the
    domain or TLS state, arbitrary intents). Every JSON snippet in it is valid against
    `spec/siteskin-1.0.schema.json`. `README.md` stays short and points at `INTEGRATION.md`.
  - Tests: snippets checked against the schema by hand against `spec/siteskin-1.0.schema.json`.

- [ ] TASK-9: Roadmap and architecture notes
  - Modified: `docs/ROADMAP.md`, `CLAUDE.md`
  - Acceptance: `DEMO-001` ticked; `CLAUDE.md` gains a "Reference integration (DEMO-001)" section
    covering the five pinned copies and the recompute-never-transcribe rule, why the deployment must
    be an origin root, the directory-layout decision with the `**`-matches-zero-segments consequence
    that lets `/cart/**` select `/cart`, and the rule that the reference site takes no third-party
    dependency and no exception.
  - Tests: `bash scripts/pre-commit-check.sh`
