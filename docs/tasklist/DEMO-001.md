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

- [x] TASK-1: Pin the reference manifest's route contract, and give `home` a `match`
  - New: `siteskin-core/src/test/kotlin/dev/siteskin/core/nav/ReferenceIntegrationNavTest.kt`
  - Modified: `spec/fixtures/valid/bloom-flowers.json`,
    `spec/fixtures/valid/bloom-flowers.expected.json`,
    `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt`
  - Acceptance: the test reads the fixture through the existing `siteskin.spec.dir` wiring — not a
    copied literal — validates it against `https://bloomflowers.example`, and asserts the id
    `NavMatcher.activeItem` selects for `/`, `/catalog`, `/catalog/`, `/catalog/roses`, `/cart`,
    `/cart/`, `/account`, `/account/orders`. `"match": ["/"]` is added to `bottomNavigation[0]` in
    both the body and the canonical result, and `BLOOM_FLOWERS_SHA256` is recomputed with
    `sha256sum` from the file rather than transcribed.
  - Tests: `ReferenceIntegrationNavTest`, `SpecCorpusTest`, `SecurityConformanceTest`,
    `OriginCorpusTest`
  - Result: the test was written first and failed exactly as predicted — `/ must make \`home\` the
    active item ... expected:<home> but was:<null>` — which is the finding the ticket exists to
    close. Green after the fixture edit; full `:siteskin-core:test` green.
  - Negative control: reverting the fixture edit alone fails three tests from three directions —
    `ReferenceIntegrationNavTest` (behaviour), `SpecCorpusTest.bloomFlowersFixtureMatchesThePublishedCopy`
    (the SHA pin), and `SecurityConformanceTest` (the canonical result). Reverting the SHA constant
    alone fails `SpecCorpusTest` from the other side. Restored, all green.
  - Deviation: the plan split this into TASK-1 (a deliberately red test) and TASK-2 (the fix).
    Merged, because `PROJECT_RULES.md` requires `scripts/pre-commit-check.sh` to pass before every
    commit and a knowingly-red checkpoint cannot satisfy it. "Write the test first" is ordering
    *within* a task; the red-then-green evidence is recorded above instead of in a commit.
  - Deviation: `SecurityConformanceTest` also guards `.expected.json`, so the canonical result has a
    third independent guard the research note did not list. Recorded here rather than silently
    relied on.

- [x] TASK-2: Point the browser's suggestion catalogue at the deployment origin
  - Modified: `app/src/main/java/app/webora/browser/browser/SuggestedSite.kt`
  - Acceptance: Bloom Flowers reads `https://bloomflowers.webora.app/`, consistent with the
    `pixelplay.` and `journal.` entries; `.example` survives only in `spec/fixtures/`, where a
    reserved documentation name is correct. No string moves into a composable — the catalogue keeps
    its resource ids.
  - Tests: `HomeModelsTest` (unchanged — it constructs its own suggestion), `:app:test`
  - Result: one string. `bloomflowers.example` now survives only where a reserved documentation
    name belongs — the corpus, `SPEC.md`, unit tests and a `@Preview` — and in no shipped
    browser-owned value, verified by grep across the tracked tree. `isSafeSuggestion` accepts the
    new URL on the same terms as the two entries beside it, so the catalogue stays HTTPS-only with
    no user-info and no fragment.

- [x] TASK-3: The reference site — pages, stylesheet, and the logo
  - Repository: `denrzv/bloom-flowers`
  - New: `index.html`, `catalog/index.html`, `cart/index.html`, `account/index.html`,
    `assets/site.css`, `assets/siteskin/logo.png`, `tools/make-logo.py`
  - Acceptance: directory layout so `/catalog`, `/cart` and `/account` resolve on any static host;
    landing page is mockup screen 3 (hero, Best Sellers grid, `#D94F8A`); every page carries the
    shared header, in-page navigation and footer, and is fully usable with no SiteSkin present; no
    third-party origin, cookie, form submission, service worker or storage anywhere; semantic
    landmarks, a skip link, visible focus, and no meaning carried by colour alone. The logo is a
    512×512 PNG under 64 KB, generated deterministically by the committed script.
  - Tests: none yet — TASK-4 is the check, and it is written against a site that already exists so
    that it can be run in both directions.
  - Result: `python3 -m http.server` confirms the layout decision from the research note — `/`,
    `/catalog/`, `/cart/`, `/account/`, the logo and the manifest all return 200, and `/catalog`,
    `/cart`, `/account` return 301 to their trailing-slash form, which the published `match` arrays
    already cover. Rendered in Chromium at 1280 and at phone width: the landing page is mockup
    screen 3. The logo is 512×512 and 5,702 bytes — 1.1% of `NET-003`'s 512 KiB budget. Grep
    confirms no `src`/`href` leaves the origin except the INTEGRATION.md link on GitHub.
  - Deviation: tables are wrapped in an `overflow-x: auto` container with a `min-width`. Below about
    390 px a three-column table's min-content width exceeds the available column and would widen the
    whole page, clipping the prose beside it rather than only the table.
  - Note: headless Chromium clamps its layout viewport to 500 px on Linux, so a `--window-size=412`
    screenshot renders at 500 and crops — which looks exactly like horizontal overflow. Measured
    `scrollWidth` (485) against `innerWidth` (500) with an injected probe before concluding
    anything; there was no overflow.

- [x] TASK-4: Offline route conformance in CI
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
  - Negative control: hiding `catalog/index.html` fails three ways at once —
    `/bottomNavigation/1/action/url`, `/bottomNavigation/1/match/0` and `/bottomNavigation/1/match/1`
    — because the action URL and both match patterns name the same route. Prefixing the logo with
    seven junk bytes fails the PNG-signature check. Restored, exit 0 both times.
  - Result: 10 manifest paths at the time of writing, 11 after TASK-5 adds `"/"` as a match pattern.
    Wired as a second job in `manifest-guard.yml` and re-run by `pages.yml` before publishing.
  - Deviation: the script also guards against passing vacuously — a manifest naming no paths is a
    failure rather than a silent success, since every assertion in it is a loop over that list.

- [x] TASK-5: The served manifest copy and its checksum
  - Repository: `denrzv/bloom-flowers`
  - Modified: `.well-known/siteskin.json`, `.well-known/siteskin.json.sha256`
  - Acceptance: byte-identical to webora's `spec/fixtures/valid/bloom-flowers.json` after TASK-1 —
    verified by comparing hashes across the two checkouts, not by eye; `.sha256` recomputed with
    `sha256sum` from the file; `sha256sum --check` passes in `.well-known/`; the hash equals
    `BLOOM_FLOWERS_SHA256`.
  - Tests: `sha256sum --check`, `tools/check-routes.py`
  - Negative control: changing one word of the served copy fails `sha256sum --check`. Restored, OK.
  - Result: `cmp` confirms byte-identity with the fixture, and the recomputed checksum equals
    `BLOOM_FLOWERS_SHA256` — so all five pinned copies agree and both independent guards pass.

- [x] TASK-6: Deployment — Pages, custom domain, and the manual lint job
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
  - Tests: all three workflows parse; jobs are `checksum`/`routes`, `deploy`, `lint`.
  - Result: `pages.yml` re-runs `check-routes.py` before uploading, so a deploy cannot publish a
    manifest whose paths the repository stopped serving. The lint job is not runnable until DNS
    exists — the ticket's one blocked criterion.
  - Deviation: `siteskin-lint.yml` takes the origin as a `workflow_dispatch` input defaulting to
    `https://bloomflowers.webora.app`, so the job can be pointed at a staging origin without an
    edit. The default keeps the documented command honest.

- [x] TASK-7: `INTEGRATION.md` and the repository README
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
  - Tests: both JSON snippets were run through `SiteSkinValidator` itself — accepted, zero
    diagnostics — via a throwaway test that was deleted after the run. `jsonschema` could not be
    installed (no PyPI access from this environment), and validating against the real validator is
    the stronger check anyway: it covers the security layer the schema deliberately does not.
  - Result: the guide documents the `home`/`match` defect this ticket found, as the worked example
    for `SPEC.md` §7.1 clause 4. Section 7 ("What a manifest never grants") is given the same weight
    as the capability list.

- [x] TASK-8: Roadmap and architecture notes
  - Modified: `docs/ROADMAP.md`, `CLAUDE.md`
  - Acceptance: `DEMO-001` ticked; `CLAUDE.md` gains a "Reference integration (DEMO-001)" section
    covering the five pinned copies and the recompute-never-transcribe rule, why the deployment must
    be an origin root, the directory-layout decision with the `**`-matches-zero-segments consequence
    that lets `/cart/**` select `/cart`, and the rule that the reference site takes no third-party
    dependency and no exception.
  - Tests: `bash scripts/pre-commit-check.sh`
