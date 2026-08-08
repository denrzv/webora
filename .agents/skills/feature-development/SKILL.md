---
name: feature-development
description: Run or resume one Webora AIDD ticket through idea, research, plan, tasks, implementation, review, QA, and validation in a single agent session.
---

# Feature development

Run the existing Webora AIDD workflow in the current main session. Do not spawn subagents.

`workflow.md` is the authoritative state machine. `PROJECT_RULES.md`, `conventions.md`, and `CLAUDE.md` are normative project context.

## Resume first

Before choosing a phase:
1. Read `docs/.active_ticket` when it exists.
2. Inspect the active ticket's PRD, research, plan, and tasklist statuses.
3. Inspect recent commits and the working tree when implementation may already have started.
4. Resume at the first incomplete phase or task. Never recreate completed artifacts merely because this is a new agent session.

## Phase instructions

The existing Claude command files are the current shared phase definitions. Read a phase file only when entering that phase:

- `.claude/commands/idea.md`
- `.claude/commands/researcher.md`
- `.claude/commands/plan.md`
- `.claude/commands/tasks.md`
- `.claude/commands/implement.md`
- `.claude/commands/pre-commit.md`
- `.claude/commands/review.md`
- `.claude/commands/qa.md`
- `.claude/commands/validate.md`

Follow the order in `workflow.md`. During implementation, complete exactly one `TASK-*` or `TASK-FIX-*`, run the pre-commit gate, and create exactly one commit for that task.

After each task commit:
- If the checkout has a push-capable remote, push and verify the current ticket branch before starting the next task.
- If this is a managed cloud checkout that intentionally has no push-capable remote, do not synthesize remotes or credentials. The local task commit is a valid intra-session checkpoint and the next task may start.

Before ending a managed-cloud session or handing work to another assistant, persist all accumulated task commits through the platform-provided PR/export/sync mechanism. If that durable checkpoint cannot be created, stop and report the blocked handoff.

If the working tree contains partial work from another session or assistant, stop the normal loop and use the `recover` skill semantics before continuing.
