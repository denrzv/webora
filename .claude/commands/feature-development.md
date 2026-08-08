# /feature-development

Full loop: `/idea` → `/researcher` → `/plan` → `/tasks` → implement tasks one at a time.

For every completed task: `/pre-commit` → commit → push the current ticket branch. Do not begin the
next task until the checkpoint is visible on the remote. Then `/review`, `/qa`, `/validate`.
Squash-merge to `main` as `<TICKET>: <title>`.
