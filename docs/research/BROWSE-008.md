# BROWSE-008: Research
Status: RESEARCH_READY

## Current product seams

- `BrowserScreen` owns `BrowserSession`, the active tab id, and one `BrowserWebViewController` per
  tab. It currently installs `BrowserBackHandler(enabled = state.canGoBack)` and passes
  `controller::goBack` plus raw `state.canGoBack` to both regular and integrated visible chrome.
- `BrowserState` distinguishes native `BrowserMode.Home` from `Regular` and `Integrated`; replacing
  an active tab state with `BrowserState()` is already the Home action and removes page/SiteSkin
  presentation without touching other tabs.
- `BrowserWebViewController.goBack()` checks the live attached WebView, performs history navigation,
  and returns whether it consumed Back. Live controller state must win over a potentially delayed
  Compose observation.
- `BrowserBackHandler` presently disables itself whenever observed WebView history is empty, then
  delegates to the dispatcher only if a live `goBack()` unexpectedly fails. It has no Home seam.
- `RegularBrowser` independently wires the two visible surfaces, creating the disagreement exposed
  by hosted evidence.

## Trust and ownership boundary

Back is entirely browser-owned. A page can create ordinary WebView history through navigation, and
a validated manifest can select neither Back policy nor its UI. The decision consumes only the
browser-observed mode and the controller's live history result. SiteSkin Back remains a fixed Webora
control; its label, placement and callback are not manifest data.

Home fallback does not resolve or load a URL. It replaces only the active tab's `BrowserState` with
the native Home state, which drops integrated configuration, displayed URL, origin, loading/error,
and navigation flags from presentation. Other tab states and their retained controllers remain
unchanged.

## Design risks and tests

- **Stale observations:** call live `controller.goBack()` first; only fall back to Home when it
  returns false. A negative-control JVM test must prove history wins.
- **Visible/system drift:** derive visible availability from the same pure policy and pass the same
  callback to regular and integrated chrome; system handling uses that callback too.
- **Exit recursion:** install a dispatcher callback only when Back is browser-consumable. Home
  disables it, allowing platform handling without temporarily toggling/re-entering the callback.
- **Tab leakage:** capture/update the active id through current Compose state and test
  `BrowserSession.update` isolation.
- **SiteSkin leakage:** Home's sealed mode and existing chrome handoff make stale integrated chrome
  unrepresentable after the state reset.

## Likely affected files

- `app/src/main/java/app/webora/browser/browser/BrowserBack.kt` — pure decision/action policy.
- `BrowserScreen.kt` — one shared Back callback and system-handler integration.
- JVM policy/session tests and Compose chrome tests for enabled first-page Back.
- `CLAUDE.md`, roadmap/backlog, review and QA workflow artifacts.
