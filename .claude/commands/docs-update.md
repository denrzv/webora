# /docs-update

Update documentation to match what was built.

- `CLAUDE.md` — add a named "Note" section when a ticket establishes an architectural convention
  future work must not break. This repo uses those sections in place of long-form ADRs for
  decisions that emerge during implementation; `docs/adr/` holds the decisions made up front.
- `docs/DEVELOPMENT_PLAN.md` — only when a decision changes, not for progress.
- `docs/ROADMAP.md` — tick off milestones.
- `spec/SPEC.md` — any manifest-visible change, with a schema version bump if it is not additive.
