# Webora agent instructions

This repository uses a file-backed AIDD workflow. The workflow state lives in repository artifacts, not in any assistant session.

## Required context

Read and follow:
- `workflow.md` — workflow state machine and artifact statuses.
- `PROJECT_RULES.md` — hard project rules.
- `conventions.md` — implementation and testing conventions.
- `CLAUDE.md` — despite the filename, this is shared normative project architecture, security, testing, and implementation context.

Before proposing architecture, read `docs/DEVELOPMENT_PLAN.md` and the relevant ADRs/spec sections.

## Working model

- Work in one main agent session. Do not spawn subagents unless the user explicitly asks for them.
- Resume from repository state; do not repeat workflow phases whose artifacts already carry their ready status.
- `docs/.active_ticket` identifies the current ticket.
- Implement exactly one task at a time and keep one task per commit.
- Run `bash scripts/pre-commit-check.sh` before each task commit.
- Treat every completed task commit as a checkpoint. If the checkout has a push-capable remote, push and verify that commit before starting the next task.
- In a managed cloud checkout that intentionally has no push-capable remote, do not invent remotes or credentials. Local task commits are valid checkpoints within the same session, so work may continue one task at a time.
- Before ending a managed-cloud session or handing work to another assistant, persist accumulated commits through the platform-provided PR/export/sync mechanism. If no durable checkpoint can be created, stop and report the blocked handoff.
- In managed cloud without `/dev/kvm` or a connected Android device, do not provision a software-only Android Emulator. Compile relevant instrumentation tests, and report unavailable runtime instrumentation or screenshots as an environment limitation rather than a task failure.
- Preserve the AIDD order and status vocabulary defined in `workflow.md`.
- If work was interrupted or another assistant worked on the branch, reconcile `git status`, `git diff`, recent commits, and the active tasklist before editing.

Use the repository-local `feature-development` skill for the normal ticket loop and `recover` for interrupted handoffs.
