# BROWSE-008: Back returns to Home when WebView history is empty
Status: PRD_READY

## Context / Problem

Webora Home is native Compose state rather than a WebView history entry. After a user opens the
first regular or SiteSkin page, `WebView.canGoBack()` is false, so the visible Back affordance is
disabled and system Back cannot return the active tab to Home. Hosted CI-007 runs 30 and 31 exposed
this product defect while attempting the documented Home → SiteSkin → Home → regular journey.

## Goals

- Define one browser-owned Back contract: WebView history first, native Home second, platform exit
  only from Home.
- Apply it consistently to regular chrome, integrated SiteSkin chrome, and Android system/predictive
  Back.
- Keep Home fallback tab-local and tear down the active tab's SiteSkin presentation.
- Keep Home native; do not manufacture a URL or WebView history entry.

## Non-goals

- Changing Forward, Reload, tab close/selection, or Android task-exit policy.
- Test-only navigation shortcuts, delays, relaxed evidence assertions, or fake Home URLs.
- Allowing a page or manifest to influence Back availability, precedence, labels, or callbacks.

## Acceptance criteria

1. On a first regular or integrated page, visible browser Back is enabled and returns only the
   active tab to native Home.
2. When WebView history exists, Back consumes one history entry before offering Home fallback.
3. System/predictive Back uses the same history → Home → platform precedence as visible Back.
4. Home has no browser-consumable Back action and delegates to platform/app-exit behavior.
5. Returning an integrated page to Home removes SiteSkin chrome and stale page identity without
   changing another tab's state or controller.
6. JVM and compiled instrumentation tests cover first-page regular/integrated fallback, history
   precedence, Home delegation, visible enabled state, and tab isolation.
7. `bash scripts/pre-commit-check.sh` passes.
