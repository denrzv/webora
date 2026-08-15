# DEMO-004: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/DEMO-004.prd.md`
- Research: `docs/research/DEMO-004.md`
- Plan: `docs/plan/DEMO-004.md`

## Tasks

- [x] TASK-1: Publish the browser-first reference walkthrough
  - Modified: `docs/WALKTHROUGH.md`, `docs/INSTALL.md`, `docs/SCREENSHOTS.md`,
    `docs/tasklist/DEMO-004.md`.
  - Acceptance: one visible-control journey demonstrates ordinary HTTPS browsing, a second tab,
    a naturally created Recent or Favourite, Bloom consent/integration, and tab-switch restoration
    of regular chrome; the three navigation owners and evidence limitations are explicit; install
    and screenshot docs lead readers to it; no product/evidence code or second SiteSkin origin.
  - Checks: documentation links/terms, `git diff --check`, and
    `bash scripts/pre-commit-check.sh` (no narrower code tests for documentation-only changes).
  - Result: the new guide separates Android, browser, and bounded site navigation, then uses visible
    M8 controls to browse example.com, create a Favourite, keep it in one tab, consent to Bloom in a
    second tab, and restore regular chrome by switching back. Install and screenshot docs link the
    flow and explicitly state that tabs/local records are interactive checks, not uncaptured claims
    about the four-frame contact sheet.

- [x] TASK-2: Complete review, QA, documentation status, and validation
  - Modified: `reports/review/DEMO-004.md`, `reports/qa/DEMO-004.md`, `docs/ROADMAP.md`,
    `docs/tasklist/DEMO-004.md`.
  - Acceptance: review has no unresolved findings; QA maps all PRD criteria and edge cases; roadmap
    marks DEMO-004/M8 complete without claiming a new hosted run; all artifact statuses and tasks
    validate and the required gate passes.
  - Checks: workflow/status/link checks, `git diff --check`, and
    `bash scripts/pre-commit-check.sh` (no narrower code tests for documentation-only changes).
  - Result: review resolved with no findings; QA maps every criterion and edge case while recording
    unavailable device execution honestly. The roadmap now closes DEMO-004/M8, artifact statuses and
    links validate, and the full repository gate passes without claiming a new hosted screenshot run.
