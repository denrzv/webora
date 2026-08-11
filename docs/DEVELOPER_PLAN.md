# Webora Browser — developer execution plan

Status: ACTIVE
Last updated: 2026-08-11

This document is the execution companion to [`DEVELOPMENT_PLAN.md`](DEVELOPMENT_PLAN.md),
[`ROADMAP.md`](ROADMAP.md), and [`BACKLOG.md`](BACKLOG.md). It does not replace ticket PRDs or
`docs/plan/<TICKET>.md`: those are created by the AIDD workflow when a ticket starts. Its purpose is
to make the recommended implementation order, parallelism, evidence gates, and hand-off points
obvious to a developer or coding agent.

## Current objective

The first live Android screenshot journey is now capable of booting a GitHub-hosted Pixel 6 / API 33
emulator, running the Bloom Flowers integration end to end, and returning screenshots. That evidence
also exposed product-quality problems that a green instrumentation result cannot detect:

- canonical screenshots can be contaminated by an Android `System UI isn't responding` dialog;
- screenshot review requires downloading and drilling into a diagnostics-heavy ZIP;
- the debug SiteSkin Inspector is useful to developers but visually dominates the canonical demo;
- SiteSkin navigation and quick actions still render Unicode placeholder glyphs rather than a
  deliberate browser-owned icon system;
- the first-use consent actions do not form a clear, adaptive hierarchy on a phone-sized viewport;
- the Bloom Flowers reference integration proves protocol correctness but does not yet showcase the
  level of visual quality Webora is meant to provide.

M7 turns those findings into explicit work rather than treating them as screenshot quirks.

## M7 execution tracks

### Track A — trustworthy visual evidence

These tickets can start as soon as the screenshot workflow from `CI-001` is on `main`.

1. **`CI-002` — Deterministic clean Android screenshot capture**
   - identify and remove known System UI contamination from canonical captures;
   - add a deliberate emulator-ready/settled gate rather than equating `sys.boot_completed=1` with
     visual readiness;
   - if a known System UI ANR dialog still appears, dismiss only that system-owned dialog in test
     automation and retain diagnostics;
   - never hide, auto-dismiss, or downgrade a Webora crash/ANR.

2. **`DEVX-002` — Screenshot review experience**
   - create a human-facing screenshots artifact separate from diagnostics;
   - flatten canonical PNGs to the artifact root;
   - generate one labelled `preview.png` contact sheet for the complete journey;
   - keep raw test output and logcat in a separate diagnostics artifact;
   - make the job summary state screenshot count, commit/run metadata, and artifact names.

3. **`DEVX-003` — Inspector isolation and canonical evidence mode**
   - keep the SiteSkin Integration Inspector available in debug builds;
   - remove its persistent floating overlay from canonical product screenshots;
   - expose the inspector through an explicit debug affordance or a deterministic visual-evidence
     mode rather than making every demo frame look like a developer tool.

`CI-002` and `DEVX-002` may proceed in parallel. `DEVX-003` is independent of the screenshot
transport but must land before final M7 visual acceptance.

### Track B — SiteSkin product polish

1. **Finish `UX-002` first.** It owns the browser design tokens and bundled vector icon foundation.
2. **`UX-005` — SiteSkin integrated chrome & semantic icon set** *(revived)*
   - replace `⌂`, `▦`, `▣`, `●`, `☎` and the generic quick-action `+` presentation with real
     browser-owned vector icons;
   - keep manifest values semantic names only; sites never provide arbitrary icon URLs or drawables
     for trusted browser chrome;
   - define and document a small semantic vocabulary covering at least Home, catalog/storefront,
     flowers or an equivalent domain-specific catalog cue, cart, account/person, call, search/menu,
     and deterministic fallback;
   - preserve selected/unselected navigation semantics, minimum touch targets, TalkBack labels, and
     font-scale behaviour.
3. **`UX-007` — Adaptive SiteSkin consent action hierarchy**
   - make `Allow` unmistakably primary while keeping `Not now` and `Never for this site` readable
     and correctly weighted;
   - support narrow phones, long/localised labels, and 200% font scale without awkward split rows or
     clipping;
   - preserve the browser-owned origin/security message and existing consent semantics.
4. **`DEMO-003` — Bloom Flowers visual fidelity & protocol showcase**
   - update the reference manifest/site to deliberately exercise the polished semantic icon set and
     quick-action presentation;
   - use the live Pixel 6 journey as visual acceptance evidence;
   - keep `DEMO-002` (multiple origins) descoped: M7 improves the quality of the existing reference
     integration rather than expanding demo count.

`UX-005` depends on `UX-002`. `UX-007` can start after the already-complete `UX-006`. `DEMO-003`
starts only after `UX-005`, `UX-007`, and `DEVX-003` are stable enough to demonstrate.

## Recommended order

```text
                         ┌─ CI-002 ─────┐
CI-001 (done) ───────────┤              ├─ clean visual evidence ────────┐
                         └─ DEVX-002 ───┘                                │
                                                                         │
DEVX-001 (done) ─────────── DEVX-003 ────────────────────────────────────┤
                                                                         ├─ DEMO-003
UX-002 ──────────────────── UX-005 ──────────────────────────────────────┤
                                                                         │
UX-006 (done) ───────────── UX-007 ──────────────────────────────────────┘
```

The evidence track should not wait for all M6 browser-surface work. The icon ticket does wait for
`UX-002`, because introducing a second ad-hoc icon mechanism would recreate the inconsistency M6 is
meant to remove.

## Quality gates for every M7 ticket

A ticket is not done merely because its implementation test passes. Use the gate that matches the
kind of defect the ticket is meant to prevent.

- **Code gate:** `bash scripts/pre-commit-check.sh` is green.
- **Behaviour gate:** affected unit/instrumentation tests pass and include a negative control for
  security- or trust-boundary invariants.
- **Visual gate:** when a ticket changes a canonical browser surface, inspect the live Pixel 6
  screenshot evidence rather than relying only on Compose semantics assertions.
- **Security gate:** manifest-controlled input never gains access to browser-owned security identity,
  arbitrary trusted-chrome assets, or the ability to suppress the domain/TLS indicator.
- **Accessibility gate:** changed controls retain accessible names, >=48 dp targets where applicable,
  contrast guarantees, and 200% font-scale usability.
- **Evidence gate:** canonical screenshots must be free from known OS/debug overlays before they are
  accepted as product evidence.

## Ticket workflow

This file is sequencing guidance, not permission to skip AIDD gates. For each ticket:

```text
/idea <TICKET>
  -> PRD_READY
/research <TICKET>      when required by the ticket
/plan <TICKET>
  -> PLAN_APPROVED
/tasks <TICKET>
  -> TASKLIST_READY
/implement <TICKET> TASK-N
```

Create the ticket-specific PRD, research, plan, and tasklist only when that ticket starts. The scope
and acceptance criteria in `BACKLOG.md` are the durable input; implementation details should remain
fresh enough to incorporate evidence from the preceding ticket.

## M7 exit criteria

M7 is complete when a reviewer can run the manual Android screenshot workflow and obtain, with
minimal friction, a clean and intentional visual story:

1. Webora Home with no OS/debug contamination;
2. first-use SiteSkin consent with a clear adaptive action hierarchy;
3. Bloom Flowers in integrated mode with real semantic icons and deliberate quick actions;
4. one contact-sheet preview plus adjacent canonical PNGs in the human-facing artifact;
5. diagnostics still available separately when a run fails;
6. the SiteSkin Inspector remains accessible to developers without appearing in canonical evidence;
7. all protected domain/TLS and manifest-trust invariants remain unchanged.
