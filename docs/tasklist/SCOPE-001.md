# SCOPE-001: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/SCOPE-001.prd.md`
- Research: `docs/research/SCOPE-001.md`
- Plan: `docs/plan/SCOPE-001.md`

## Tasks

- [x] TASK-1: Point the browser's suggestion catalogue at the live origin
  - Modified: `app/src/main/java/app/webora/browser/browser/SuggestedSite.kt`,
    `app/src/main/res/values/strings.xml`
  - Acceptance: `defaultSuggestedSites` holds one entry, `https://denrzv.github.io/`; the PixelPlay
    and Daily Journal entries and their four string resources are gone; `SuggestedSite`,
    `create` and `isSafeSuggestion` are unchanged. No `*.webora.app` remains in any compiled value.
  - Tests: `:app:test`, `HomeModelsTest`, `BrowserSurfaceConventionsTest`
  - Care: `BrowserSurfaceConventionsTest` contains `webora.app.src`, a Gradle property name, not the
    domain. Inspect every match; do not script the replacement.
  - Result: one entry left, four string resources removed. Grep confirms no `*.webora.app` in any
    app source, and `webora.app.src` survives untouched in `app/build.gradle.kts` and
    `BrowserSurfaceConventionsTest`. Full gate green.

- [x] TASK-2: Retire the dead custom domain in the demo repository
  - Repository: `denrzv/bloom-flowers`
  - Removed: `CNAME`, `.github/workflows/pages.yml`
  - Modified: `README.md`, `.github/workflows/siteskin-lint.yml`, `INTEGRATION.md`
  - Acceptance: no `webora.app` anywhere; the live site is `https://denrzv.github.io`; exactly one
    publisher serves the site, and `README.md` says why `pages.yml` is gone so its absence is not
    read as an oversight; `siteskin-lint.yml` defaults to the live origin and drops its
    waiting-for-DNS comment. `tools/check-routes.py` still passes — it derives paths from the
    manifest, which names no domain.
  - Tests: `tools/check-routes.py`, `sha256sum --check`, workflow YAML parse
  - Care: `CNAME` and `pages.yml` must go in one commit; a deploy between them would publish a
    project-path copy that cannot host a SiteSkin integration.
  - Result: both removed in one commit. No `webora.app` remains anywhere in the repository;
    `check-routes.py` still reports 11 paths resolving, confirming the manifest never named the
    domain; both remaining workflows parse. The lint workflow's "manual because DNS" rationale was
    replaced with the real one — `denrzv/webora` is private, so its checkout needs a token.

- [ ] TASK-3: Narrow the roadmap and backlog to one demo, and replace Play with APK distribution
  - Modified: `docs/ROADMAP.md`, `docs/BACKLOG.md`
  - Acceptance: `DEMO-002` and `PLAY-001..003` sit under a descoped heading, each keeping its
    original text plus why it is not being built and what would revive it; `M5` is Distribution;
    `DIST-001` defines `:app:assembleDebug` on a GitHub Release with install instructions, and
    states explicitly that signing, R8 keep verification and store assets are out of scope.
  - Tests: `bash scripts/pre-commit-check.sh`

- [ ] TASK-4: Correct the development plan's hosting and distribution reasoning
  - Modified: `docs/DEVELOPMENT_PLAN.md`
  - Acceptance: the `webora.app` subdomain table is replaced by what is true — the domain is taken,
    the origin-root rule makes a user Pages site the only free option that can host a SiteSkin
    integration, and `denrzv.github.io` serves it today as a stopgap that cannot supply the distinct
    origins `DEMO-002` would need. The multi-origin argument is retained and marked deferred rather
    than deleted, because it was postponed and not refuted. The Play analysis is retained under a
    descoped heading with an explicit carve-out that **targetSdk 36 already shipped** and is not
    descoped.
  - Tests: `bash scripts/pre-commit-check.sh`

- [ ] TASK-5: Record the live verification and update the architecture note
  - Modified: `CLAUDE.md`, `reports/qa/DEMO-001.md`
  - Acceptance: `CLAUDE.md`'s `DEMO-001` hosting paragraph names the live origin and its stopgap
    status while keeping the origin-root rule that selected it. `reports/qa/DEMO-001.md` gains an
    addendum — not a rewritten table — recording scenario 23 as passed with its evidence (live bytes
    matching `BLOOM_FLOWERS_SHA256`, the `/catalog` → `/catalog/` redirect observed on real GitHub
    Pages, `siteskin-lint` exit 0) and restating that scenario 24 remains blocked.
  - Tests: `bash scripts/pre-commit-check.sh`
