# BROWSE-010 — Leaving Home again shows the previous page under a permanent spinner

Status: PRD_READY

Issue: [#106](https://github.com/denrzv/webora/issues/106) · Backlog: `docs/BACKLOG.md`

## Context / Problem

A tab that returns to Home and then navigates again never loads the new page.

`BrowserScreen` composes `RegularBrowser` only when the tab is not on Home, so returning a tab to
Home removes its `AndroidView` from composition while `BrowserWebViewController` deliberately retains
the `WebView` — `BROWSE-006` requires live back/forward history to survive. Navigating that tab out
of Home re-enters the subtree and runs `HardenedWebView`'s factory again, where the load is gated on
the renderer being new:

```kotlin
val existing = controller.attached()
(existing ?: WebView(context)).apply {
    …
    if (existing == null) loadUrl(initialUrl)
}
```

`existing` is not null, so the new URL is **never requested**. The renderer keeps painting the
previous page while `BrowserState.displayedUrl` and the address chip say the new one, and `isLoading`
stays `true` forever because no callback can arrive for a navigation that never started.

Reproduction: load a page → Home → type any address.

Found by `BROWSE-009`'s `/review` by reasoning about renderer reuse, not by a run. It is
**pre-existing on `main`**; `BROWSE-009` neither causes nor cures it.

## Goal

A tab that returns to Home and navigates again renders the address it navigated to, and its loading
state terminates — without reloading on tab switch, and without destroying the retained renderer.

## Non-goals

- Changing `BrowserSession`, tab identity, ordering, selection or the eight-tab limit.
- Destroying, recreating or pooling renderers on selection or on Home.
- Weakening any `BROWSE-001` hardening, or changing what the renderer is allowed to load.
- Changing `AddressResolver`, the address bar, or how a destination becomes a URL.
- Re-opening `BROWSE-009`'s event routing; owner ids and the pure router stay as they are.

## User stories

- As a user, going Home and typing a new address shows that address.
- As a user, a page that is loading eventually stops loading — the spinner resolves to a page or to
  an error, never to neither.
- As a user, switching between tabs still restores each tab's page instantly, without a reload and
  without losing its back/forward history.

## Acceptance criteria

1. Page → Home → new address renders the new address, and its loading state terminates.
2. The decision to load at mount time is a **pure function** of browser-observed values, callable
   from the JVM gate. It reads no page content, document title, or manifest field.
3. Tab switching performs no reload. Selecting a tab whose renderer already holds that tab's current
   page issues no load, including for a tab whose last navigation **failed** and for a tab whose
   current page was reached by an in-page link rather than by a browser-issued navigation.
4. `TabRendererIsolationTest`'s existing cases remain unedited and remain valid, including its
   assertion that the reattached renderer still reports its own URL.
5. The retained `WebView` is not destroyed or recreated on Home, so `BROWSE-006`'s live-history
   retention across tab switches is unchanged.
6. The Back contract after a Home round trip is **explicitly decided** and written down, reconciled
   with `BROWSE-008`'s history → Home → platform-exit ordering: either preserved as-is with the
   reasoning recorded, or changed deliberately with its own evidence.
7. A negative control proves the new rule is load-bearing: reverting it reproduces the never-loaded
   page, and a rule that fires on tab switch fails a test rather than passing quietly.
8. `bash scripts/pre-commit-check.sh` passes and `:app:compileDebugAndroidTestKotlin` compiles.

## NFR

- Security/privacy: no new remote input, no new network path, no change to renderer settings. The
  staleness signal must be browser-owned; a page must not be able to influence whether the browser
  re-issues a navigation, or to what.
- Reliability/fallback: the failure being removed is a terminal state with no exit. The fix must not
  introduce a second one — a rule that can loop (load → observation → load) is worse than the bug.
- Performance: no extra page load on any path that works today. A spurious reload costs the user
  bandwidth and discards form state.
- Accessibility: the loading live region already announces progress and completion; terminating the
  loading state is what makes that announcement honest.

## Risks

- **Reading `WebView.url` as the staleness signal.** It reports the framework's view of the current
  document, which for a failed navigation may be the failed URL, the previous URL or `about:blank`.
  A rule built on it can fire on tab switch for the error tab that `TabRendererIsolationTest` drives
  — turning a fix into the reload regression `BROWSE-009`'s acceptance criterion 2 forbids.
- **In-page navigation.** A link click never passes through `controller.navigate`, so any
  browser-side record of "what we asked for" drifts from what the renderer shows unless observations
  maintain it too. Getting this wrong reloads on every switch back to a tab the user browsed within.
- **`loadUrl` appends to the renderer's back stack.** After a Home round trip the pre-Home entries
  are still there, so Back from the newly loaded page can reach a page the tab's browser state has
  forgotten. This is criterion 6, and it is the decision the issue insists is made *before* either
  behaviour changes.
- **Only a device can confirm the outcome.** `NET-004` records what happens when a change is
  justified by reasoning and then blessed by a run that never exercised it. Whatever ships must be
  driven by the JVM gate where the decision lives, with the device evidence named as outstanding
  rather than assumed.

## Open questions

None blocking. Criterion 6's decision is made in research and committed in the plan.
