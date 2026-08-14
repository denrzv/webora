# DEMO-003: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/DEMO-003.prd.md`
- Research: `docs/research/DEMO-003.md`
- Plan: `docs/plan/DEMO-003.md`

## Tasks

- [x] TASK-1: Make the canonical fixture exercise the final semantic showcase
  - Modified: `spec/fixtures/valid/bloom-flowers.json`,
    `spec/fixtures/valid/bloom-flowers.expected.json`, `spec/SPEC.md`,
    `siteskin-core/src/test/kotlin/dev/siteskin/core/nav/ReferenceIntegrationNavTest.kt`,
    `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt`, `CLAUDE.md`, and the
    DEMO-003 workflow artifacts.
  - Acceptance: the validated fixture presents ordered Home/home, Flowers/flower,
    Cart/shopping_cart, Account/person, and Call/call semantics; all real routes retain correct
    active selection; unknown paths select nothing; every URL stays exact-origin; the canonical
    result and recomputed cross-repository digest agree; no schema/allow-list/app/capture behavior
    changes.
  - Tests: focused `ReferenceIntegrationNavTest`, `SecurityConformanceTest`, `SpecCorpusTest`,
    `OriginCorpusTest`; negative controls for stale compatibility icon and one-sided checksum edit;
    `./gradlew :siteskin-core:test`; `bash scripts/pre-commit-check.sh`.
  - Result: the canonical fixture now presents Home, Flowers, Cart, Account, and Call with the final
    browser-owned semantic vectors while keeping its deployed routes and typed actions unchanged.
    The focused reference test passes against the validated document, the 26c5293a… digest is pinned,
    and all core tests pass. Restoring `grid_view` makes the showcase test fail; restoring only the
    old digest makes the checksum test fail. Both controls were restored byte-for-byte before the
    full gate.

- [ ] TASK-2: Publish the byte-identical manifest and document the semantic choices
  - Modified in `denrzv/bloom-flowers`: `.well-known/siteskin.json`,
    `.well-known/siteskin.json.sha256`, `INTEGRATION.md`.
  - Acceptance: the served-source manifest is byte-identical to Webora's canonical fixture; the
    digest agrees in both repositories; the guide's full example agrees with publication and
    explains `flower`, catalogue compatibility, cart, account/person, and call as semantic requests;
    every named route and logo remains served; the site stays plain HTML/CSS/JS with no new origin or
    remote native-chrome asset.
  - Tests: `sha256sum --check .well-known/siteskin.json.sha256`, `python3 tools/check-routes.py`, JSON
    parsing/validator checks for guide blocks, external repository workflows, and negative controls
    for one-sided bytes and a missing route.

- [ ] TASK-3: Record live lint and canonical Pixel 6 acceptance evidence
  - Modified: `docs/tasklist/DEMO-003.md`, `docs/ROADMAP.md`, `CLAUDE.md`; later review/QA reports are
    produced by their workflow phases.
  - Acceptance: deployed `https://denrzv.github.io` serves the final manifest and lint exits 0; the
    unchanged hosted workflow captures three uncontested frames and a complete contact sheet; frame
    02 retains the UX-007 hierarchy; frame 03 shows meaningful vectors, Home selected, coherent Call
    quick action, browser-owned identity/Back/menu, and no Inspector or OS overlay; evidence records
    exact source/deployment/app commits and run id; only then is the roadmap item checked.
  - Tests: `./gradlew :siteskin-lint:run --args="https://denrzv.github.io"`, hosted Android screenshot
    workflow result/artifact diagnostics, owner visual inspection of all three full-resolution PNGs,
    and `bash scripts/pre-commit-check.sh` (documentation-only edits do not require narrower code
    test reruns under the user's instruction).
