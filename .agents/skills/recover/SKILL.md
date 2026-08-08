---
name: recover
description: Recover interrupted Webora AIDD work or hand off safely from another coding assistant by reconciling Git state with the active tasklist.
---

# Recover interrupted work

Recover repository state before making new implementation changes. Do not spawn subagents.

1. Read `docs/.active_ticket` and the corresponding `docs/tasklist/<TICKET>.md`.
2. Inspect `git status`, `git diff`, and recent commits for the active ticket.
3. Determine what is actually complete from code, tests, tasklist results, and commits; do not trust conversational history.
4. If an existing task is partially implemented, finish that task rather than starting another one.
5. If the remainder is review/follow-up work that is not represented by an existing task, append a `TASK-FIX-N` micro-task with a `- Source:` line recording the interruption/handoff provenance.
6. Update tests and the tasklist with the actual result.
7. Run `bash scripts/pre-commit-check.sh`.
8. Commit using the repository convention. Do not amend or rewrite existing commits.
9. Reconcile the checkpoint with the execution environment:
   - If the checkout has a push-capable remote, push the current ticket branch and confirm the recovery commit is visible remotely.
   - If this is a managed cloud checkout that intentionally has no push-capable remote, do not invent remotes or credentials. Keep the recovery commit as an intra-session checkpoint and continue only within the same session.
   - Before ending a managed-cloud session or handing work to another assistant, persist accumulated commits through the platform-provided PR/export/sync mechanism. If no durable checkpoint can be created, stop and report the blocked handoff.

After the tree, tasklist, and available checkpoint mechanism agree again, return to the normal `feature-development` loop at the next incomplete task or phase.
