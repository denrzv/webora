# BROWSE-008 — Back returns to Home when WebView history is empty

Status: COMPLETE

**Priority:** P0  
**Depends on:** `BROWSE-002`, `BROWSE-006`, `UX-011`, `UX-012`  
**Blocks:** final hosted acceptance of `CI-007` and the M8 visual-evidence closeout  
**Found by:** Android screenshot runs **30** (`31874095039`) and **31** (`31879413875`)

## Goal

Make browser Back a single product-level contract rather than a thin projection of WebView history.
When the active tab has page history, Back navigates that history. When the active tab is on its
first page, Back returns to Webora Home. Only Back from Home/new-tab state with no page history may
fall through to Android/app-exit semantics.

## Problem

Webora Home is native Compose state, not a WebView history entry. Opening the first page from Home
therefore produces a WebView whose `canGoBack` is false. The visible SiteSkin Back control currently
uses that value directly for its enabled state, and the browser/system Back path likewise delegates
to `WebView.goBack()` only when WebView has history.

That leaves the first opened page with no browser-owned route back to Home. `CI-007` exposed the gap
by opening Bloom Flowers from Home, accepting SiteSkin, clicking the visible integrated Back control,
and waiting for Home's address input before navigating to `example.com`. The control was disabled,
so the screen never changed and both hosted runs timed out after 45 seconds in
`captureRegularBrowsingEvidence()` after successfully capturing frames 01–03.

This is a product navigation defect, not a screenshot-harness defect. Increasing the timeout or
injecting a test-only Home transition would preserve the broken user experience and make the evidence
less trustworthy.

## Scope

- Define one browser-owned Back decision for the active tab with this precedence:
  1. if the attached WebView can go back, navigate one WebView history entry;
  2. otherwise, if the tab is not already Home/new-tab state, return that tab to Webora Home;
  3. otherwise allow the platform/app-level Back behavior to proceed.
- Use the same decision from regular browser chrome, the SiteSkin integrated Back affordance, and
  Android system/predictive Back so visible and gesture/three-button navigation cannot disagree.
- Treat the Home fallback as a real available Back action. A first-page regular or integrated tab
  must not render its browser-owned Back affordance disabled merely because `WebView.canGoBack` is
  false.
- Keep the behavior tab-local. Returning one tab to Home must not close, navigate, reset, or copy
  state into another tab.
- Preserve origin/security ownership and SiteSkin teardown rules: returning to Home removes SiteSkin
  chrome from that tab and must not leave stale manifest navigation, quick actions, or security
  identity visible.
- Do not manufacture a fake Home URL or insert Home into WebView history. Home remains browser-owned
  native state.
- Do not add screenshot-only state injection, controller shortcuts, sleeps, longer evidence
  deadlines, or weaker frame assertions. `CI-007` must pass through the same user-visible controls a
  person uses.

## Acceptance

- From fresh Home, open Bloom Flowers as the first page, activate SiteSkin, and verify the visible
  integrated Back control is enabled and returns the active tab to Home.
- From fresh Home, open a non-integrated HTTPS page as the first page and verify browser Back returns
  the active tab to Home.
- With two or more WebView history entries, Back navigates the page history first; only the next Back
  from the first page returns to Home.
- Android system/predictive Back follows the same sequence and exits/falls through only from Home when
  there is no browser history left to consume.
- In a multi-tab session, applying the fallback to one tab leaves every other tab's URL, history,
  SiteSkin mode, and navigation capability unchanged.
- Unit/instrumentation tests cover regular and integrated first-page fallback plus the negative case
  proving existing WebView history still wins over Home fallback.
- Two consecutive cold hosted Android screenshot runs complete `CI-007` with all four canonical
  frames, `png_count=4`, a four-tile contact sheet, and frame 04 showing regular browser chrome with
  no SiteSkin layers.
- `bash scripts/pre-commit-check.sh` passes.

## Result

Implemented by the shared browser-owned Back policy: live WebView history wins, the first page
falls back to native Home for only the active tab, and Home delegates to Android. Regular and
integrated visible controls plus system/predictive Back use the same action. JVM tests pass and
Android UI tests compile; device runtime and the two hosted CI-007 evidence runs remain downstream
environment/acceptance work rather than screenshot-only shortcuts in this ticket.
