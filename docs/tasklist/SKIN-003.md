# SKIN-003: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/SKIN-003.prd.md`
- Research: `docs/research/SKIN-003.md`
- Plan: `docs/plan/SKIN-003.md`

## Tasks

## TASK-1 — Add bounded SiteSkin navigation surfaces

- [x] Add a pure immutable model projecting trusted navigation, quick-action, and menu items with
  5/5/20 defense-in-depth limits, stable ids, typed selections, exact active matching, and fixed
  browser-owned menu commands.
- [x] Add standalone Compose bottom navigation, quick-action FAB, and menu components with closed
  icon mapping, distinct browser/site sections, minimum touch targets, full accessibility semantics,
  and non-overlapping ellipsized labels.
- [x] Add JVM coverage for caps, order, empty collections, active/no-match behavior, trusted item
  preservation, and browser-menu invariants; add Android Compose coverage and compile it.
- [x] Run the focused JVM test after each code change, app unit tests, instrumentation compilation,
  Detekt, debug APK assembly, and `bash scripts/pre-commit-check.sh`.

Negative-control result: lowering the navigation cap, forcing the first item active, and removing
the fixed browser command list each independently failed its focused JVM test; every protection was
restored and the focused suite passed. No Android device is connected in managed cloud, so runtime
instrumentation and the screenshot are unavailable; Android test sources compile successfully.

## TASK-2 — Review, QA, validation, and closeout

- [x] Run `/review` and resolve every finding through `TASK-FIX-*` commits if needed.
- [x] Produce a `QA_PASSED` report with focused and full-gate evidence.
- [x] Update shared architecture notes and mark `SKIN-003` complete in the roadmap.
- [x] Run `/validate` and `bash scripts/pre-commit-check.sh`; record device/runtime limitations
  without weakening compilation and local test gates.

## TASK-FIX-1 — Keep decorative icons out of accessibility output

- Source: `/review` finding 1
- [x] Clear semantics from SiteSkin's closed decorative glyphs so item labels remain the only
  browser-useful announcement.
- [x] Compile Compose instrumentation, run app unit tests, and pass the full pre-commit gate.

## TASK-FIX-2 — Keep SiteSkin navigation surfaces in the viewport

- Source: local runtime validation on API 33 emulator after completion of `SKIN-001..004`.
- [x] Make the WebView/content area consume only the browser column's remaining height so bottom
  navigation stays visible as its sibling with and without SiteSkin.
- [x] Preserve a visible, non-overlapping quick-action FAB and reachable SiteSkin menu and external
  HTTPS confirmation at normal and large font scales without regressing BROWSE or recovery UI.
- [x] Add browser-layout regression coverage, run focused tests, lint, assemble, connected
  instrumentation, the full pre-commit gate, and capture emulator screenshots.

Negative-control result: `integratedBrowserKeepsBottomNavigationAndQuickActionsVisible` failed
deterministically because the five-item bottom-navigation node was outside the displayed viewport
while the content box used `fillMaxSize`. Giving that box the browser column's remaining weight made
the focused test pass; unit tests, lint, assembly, and the 12-test connected suite also passed. API
33 emulator captures at 1.0x and 1.5x confirmed all five items, the quick-action FAB, the SiteSkin
menu, same-origin navigation, and the browser-owned external HTTPS confirmation remained visible
and reachable without overlap.
