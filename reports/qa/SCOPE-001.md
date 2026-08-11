# QA Report: SCOPE-001
Status: QA_PASSED

## Scope

Recording three owner decisions across both repositories: hosting moves to `denrzv.github.io`, the
demo fleet narrows to Bloom Flowers, and distribution becomes a debug APK rather than a Play
listing. One production file changes; the rest is documentation and two workflow deletions.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | No `*.webora.app` survives in any compiled value | grep over `app/src` | **Pass** — none |
| 2 | The Gradle property `webora.app.src` is untouched | grep `app/build.gradle.kts`, `BrowserSurfaceConventionsTest` | **Pass** — both intact. This was the ticket's main hazard: the property name is textually identical to the dead domain |
| 3 | `defaultSuggestedSites` holds one resolving entry | read + `:app:test` | **Pass** — `https://denrzv.github.io/`, which returns 200 |
| 4 | Removing four string resources breaks no reference | `:app:test` compile | **Pass** |
| 5 | No `webora.app` anywhere in the demo repository | grep | **Pass** — none |
| 6 | The manifest never named the domain | `tools/check-routes.py` after `CNAME` removal | **Pass** — 11 paths still resolve, confirming the manifest is origin-relative |
| 7 | Exactly one publisher serves the site | `pages.yml` deleted; `denrzv/denrzv.github.io` publishes | **Pass** — and `README.md` records why, so the absence is not read as an oversight |
| 8 | Remaining workflows are well-formed | YAML parse | **Pass** — `checksum`/`routes`, `lint` |
| 9 | Live origin still serves after the changes | `curl` | **Pass** — manifest `200`; `/catalog` `301` → `/catalog/` |
| 10 | Roadmap shows only intended work | read | **Pass** — `M5` is Distribution; descoped items sit under their own heading |
| 11 | Descoped items keep enough to revive | read `BACKLOG.md` | **Pass** — each has a definition, a why-not-now and a what-would-revive-it |
| 12 | The three carve-outs survive descoping | read | **Pass** — `targetSdk 36` shipped; `ADR-006`/`HARDEN-002` are security not compliance; `DATA_SAFETY.md` stands |
| 13 | `DEMO-001`'s decision record is preserved | read | **Pass** — PRD/research/plan/review unchanged; QA gains an addendum only |
| 14 | Protocol surface untouched | diff | **Pass** — `spec/`, `:siteskin-core` unchanged |
| 15 | Full gate green | `bash scripts/pre-commit-check.sh` | **Pass** |
| 16 | `pre-commit` hook rules satisfied before push | EOF/whitespace scan over both repositories | **Pass** — run explicitly because the local gate does not invoke the `pre-commit` framework that CI's `guardrails` job runs |

## Edge cases

- **Invalid manifest → regular browser mode.** N/A — no validation or activation path changed.
- **Origin change / redirect.** The reference integration's origin changed, which is the one case
  worth thinking about: a client that had cached the old origin's manifest is unaffected, because
  `NET-002` keys on canonical origin and a new origin is simply a new key. Nothing migrates and no
  stale entry can be served for the wrong host.
- **Offline with cached manifest.** N/A — no cache change.
- **Oversized or malformed payload.** N/A — no parser change; the manifest is byte-identical.
- **Accessibility.** The removed catalogue entries took their string resources with them, so no
  literal was introduced into a composable. `BrowserSurfaceConventionsTest` still enforces that for
  what remains.
- **Untrusted text in a display surface.** N/A.

## Result

Status: QA_PASSED
Notes: 16 scenarios pass, 0 blocked, 0 failed. `DEMO-001`'s scenario 24 (on-device rendering) stays
blocked, which is unchanged by this ticket and recorded in that report's addendum rather than here.

## Validation (`/validate`)

| Gate | Result |
|---|---|
| PRD `PRD_READY` · Research `RESEARCH_READY` · Plan `PLAN_APPROVED` · Tasklist `TASKLIST_READY` | ✅ |
| Review `RESOLVED` · QA `QA_PASSED` | ✅ |
| `scripts/gate-workflow.sh` | ✅ |
| Every task ticked | ✅ 5 of 5, none deferred |
| `bash scripts/pre-commit-check.sh` | ✅ |
| `CLAUDE.md` updated | ✅ the `DEMO-001` note now records that the origin-root rule selected the hosting twice, and why the publisher strips `CNAME` |
| Commits pushed | ✅ `denrzv/webora` on `claude/bloom-flowers-reference-y5ybs7`; `denrzv/bloom-flowers` on `claude/scope-001-hosting` |

### Outstanding

1. **`denrzv/bloom-flowers` needs its branch merged.** `claude/scope-001-hosting` carries the `CNAME`
   and `pages.yml` removals. Nothing breaks while it waits — the publisher strips `CNAME` at deploy
   time anyway — but until it merges, that repository still contains a file pinning a dead domain.
2. **`DIST-001` is defined and not built.** The APK deliverable is scoped, not produced.
3. **On-device rendering** remains unverified, as in `DEMO-001`.
