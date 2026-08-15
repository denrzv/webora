# DEMO-004: Browser-first walkthrough plan
Status: PLAN_APPROVED

Input: [`../research/DEMO-004.md`](../research/DEMO-004.md) (`RESEARCH_READY`).

## Overview and flow

Add a dedicated walkthrough that begins with ordinary browsing rather than Bloom: open
`https://example.com`, create a second tab, verify the real local record on Home, open Bloom, consent
to SiteSkin, then switch back to the ordinary tab and observe regular chrome restoration. Lead with
the three navigation authorities, then map the four existing canonical frames to the relevant states
and mark tabs/records as interactive checks not pictured by the hosted run.

Link this guide from installation and screenshot docs, update the older SiteSkin-only install tour so
its labels match the current reference integration, and mark DEMO-004/M8 complete only after review,
QA, and validation. No product or harness change is needed: the ticket demonstrates the seams its
dependencies already shipped.

## Trust boundary and origin implications

`example.com` and `denrzv.github.io` are distinct exact HTTPS origins. Ordinary page bytes cannot
author Webora identity or tab/session state. Bloom bytes gain only the validated, consented SiteSkin
surface for Bloom's exact origin. Switching tabs restores each tab's independently observed mode;
neither manifest can influence the other tab.

Android retains OS navigation. Webora owns its labelled browser shell, tabs, local records, security
identity, consent, and fixed integrated escape control. SiteSkin owns only bounded site-authored
product navigation after trust validation. The documentation must not use “navigation” without
making the relevant owner clear where ambiguity matters.

## Security and evidence integrity

- Use visible production controls and naturally created state only; no Inspector, direct controller
  calls, seeded preferences, screenshot mode, or hidden gesture.
- Describe the canonical frame verdict through browser semantic ownership and unchanged screenshot
  gates, never through page title, body copy, colour, or manifest-controlled labels.
- Preserve regular fallback when Bloom discovery, validation, or consent does not activate SiteSkin.
- State that local records remain on-device and are covered by clear-browsing-data.
- Do not claim the four-frame hosted run captures the tab switcher, Recent/Favourite card, or the
  switch itself; those are manual observations in the installed walkthrough.

## File-by-file changes

| Path | Change |
|---|---|
| `docs/WALKTHROUGH.md` | Add the navigation ownership primer, reproducible browser-first steps, expected states, privacy/fallback notes, and exact evidence map. |
| `docs/INSTALL.md` | Point first-time reviewers to the walkthrough and update the short SiteSkin tour to current Bloom labels. |
| `docs/SCREENSHOTS.md` | Link the walkthrough and distinguish captured evidence from interactive tab/local-record checks. |
| `docs/ROADMAP.md` | Mark DEMO-004 and M8 complete after validation. |
| `docs/tasklist/DEMO-004.md` | Record implementation and review/QA/validation checkpoints. |
| `reports/review/DEMO-004.md` | Persist architecture, security, evidence-honesty and documentation review. |
| `reports/qa/DEMO-004.md` | Map every criterion and templated edge case to documentation/source/evidence checks. |

`CLAUDE.md`, production code, instrumentation, the screenshot workflow, and SiteSkin protocol remain
unchanged because the ticket establishes no new architectural rule or runtime behavior.

## Tests and checks

- Documentation link/term checks with `rg`; verify every referenced path exists.
- Source review against the shipped tabs, record, chrome and screenshot seams named in research.
- `git diff --check` for documentation hygiene.
- `bash scripts/pre-commit-check.sh` before each task commit, as required by the repository workflow.
- No focused code test is required for documentation-only changes under the user's instruction; the
  full pre-commit gate remains mandatory and supplies the repository-wide regression result.
- Runtime walkthrough and hosted screenshots cannot be rerun locally without a connected device or
  `/dev/kvm`; existing CI-007 QA is referenced honestly rather than reclassified as a DEMO-004 run.

## Rollout / versioning

No application, protocol, schema, storage, artifact, or version change. The guide describes the
current debug APK and four-frame evidence inventory.

## Open questions
None.
