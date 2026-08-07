# AIDD workflow — Webora Browser

## State machine

```
S0  Ticket selected          docs/.active_ticket names it
S1  PRD_READY                docs/prd/<TICKET>.prd.md
S2  PLAN_APPROVED            docs/plan/<TICKET>.md
S3  TASKLIST_READY           docs/tasklist/<TICKET>.md
S4  Implement loop           /implement → /pre-commit → commit, one TASK at a time
S5  Review complete          reports/review/<TICKET>.md
S6  QA_PASSED                reports/qa/<TICKET>.md
S7  Docs updated             CLAUDE.md notes, ADRs, spec
S8  Validate                 /validate
```

## The gate

`scripts/gate-workflow.sh` runs on **PreToolUse** for `Edit|Write` and blocks (exit 2) until all
three artifacts for the active ticket carry their ready status:

| File | Required line |
|---|---|
| `docs/prd/<TICKET>.prd.md` | `Status: PRD_READY` |
| `docs/plan/<TICKET>.md` | `Status: PLAN_APPROVED` |
| `docs/tasklist/<TICKET>.md` | `Status: TASKLIST_READY` |

The check is `grep -E "^Status:\s*<WANT>\s*$" -m1`, so the status must be an exact top-level line —
no YAML frontmatter, no indentation, no trailing commentary on the same line.

`docs/`, `reports/`, and `spec/` are exempt from the gate; otherwise writing the PRD that satisfies
the gate would itself be blocked by the gate.

## Status vocabulary

| Artifact | Values |
|---|---|
| PRD | `DRAFT` → `PRD_READY` |
| Plan | `DRAFT` → `PLAN_APPROVED`, or `SUPERSEDED` |
| Tasklist | `DRAFT` → `TASKLIST_READY` |
| QA report | `DRAFT` → `QA_PASSED` / `QA_BLOCKED` |
| Security review | `PASS` / `PASS_WITH_NOTES` / `BLOCKED` |
| Review report | `RESOLVED` / `OPEN` |

## Commits

- One TASK per commit: `<TICKET> TASK-N: <short>`
- Post-review fixes are appended to the tasklist as `TASK-FIX-N` with a `- Source:` line recording
  provenance (`/review finding 3`, `post-/validate sweep`, `field observation`).
- Squash-merge to `main` as `<TICKET>: <title>`.

## Ticket ids

`<DOMAIN>-<NNN>`, uppercase, zero-padded to three digits. Two-segment domains are legal
(`HTTP-DEV-001`). Prefixes are coined per work theme and are not registered anywhere. Ids may be
reserved forward — citing a ticket that does not exist yet is normal. Superseded tickets keep their
file with `Status: SUPERSEDED` rather than being deleted.
