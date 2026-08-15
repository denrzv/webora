# BROWSE-006: Research
Status: RESEARCH_READY

## Question

Where does Webora currently bind browser state, renderer lifetime, SiteSkin work and restoration,
and what must change to add bounded independent tabs without letting remote state cross those
boundaries?

## Current ownership map

- `BrowserScreen.kt` remembers one `BrowserState`, one `BrowserWebViewController`, one discovery
  generation, one consent candidate, one brand asset and one set of transient dialogs. Home returns
  before `RegularBrowser` is composed; every browsed page therefore uses the same conditional
  `HardenedWebView` slot.
- `HardenedWebView.kt` creates a `WebView` in `AndroidView.factory`, hardens it before loading, attaches
  it to exactly one controller, and loads `initialUrl` once. Disposal only detaches the controller;
  it does not currently destroy the renderer. Compose slot identity, rather than an explicit tab
  identity, owns its lifetime.
- `BrowserWebViewController.kt` is a one-renderer adapter. Back, forward, reload and clearing data
  target whichever `WebView` is attached. A separate controller per tab is required; a global
  controller registry would make accidental cross-tab dispatch possible.
- `BrowserState.kt` already contains the right per-tab observable fields: mode, committed/displayed
  URL, editable address, loading, navigation capabilities and failure. `BrowserMode.Integrated`
  holds a trusted `SiteSkinConfiguration` and must not be serializable or reconstructed from stored
  metadata.
- `MainActivity.kt` has no browser saved-state seam. Compose `rememberSaveable` is already available
  and used by Home/onboarding, but a session snapshot needs explicit primitive serialization and
  validation rather than automatic persistence of runtime objects.
- `BrowsingDataCleaner.android` accepts one controller. Once several renderers exist, clearing must
  fan out over all live tab controllers so a background tab cannot retain cache/form/history while
  the UI reports completion.

## Origins and manifest-controlled surface

Every tab can observe a different HTTP(S) origin. `SiteOrigin` remains the only canonical origin
type and `BrowserState.observe` remains the only page-observation transition. The tab/session layer
does not compare domains and must never infer that two tabs share trust because their hosts or
registrable domains resemble one another.

A trusted manifest may continue to influence only its own tab's validated integrated title,
subtitle, bounded brand asset, theme, navigation and actions. It cannot influence tab id, order,
selection, count, switcher labels/actions, close behaviour, persistence, or another tab's state.
Switcher summaries derive from browser-observed committed URLs and browser-authored Home fallback,
with bounded display text.

Browser-owned per-tab state includes the committed origin/TLS presentation, mode, consent prompt,
discovery generation, asset publication guard, navigation capability and pending native actions.
Global browser-owned state remains global: the SiteSkin enabled preference, stored per-origin
consent decisions, settings, inspector recorder and data-clear operation.

## Restoration boundary

Two restoration levels are materially different:

1. **Live composition/Activity lifetime:** each tab keeps an identity-keyed `WebView`, so its opaque
   in-memory back/forward list remains attached to that tab when another tab is selected. Hidden
   renderers must not be navigated merely because selection changes.
2. **Saved-state/process recreation:** persist a bounded versioned snapshot containing tab id,
   ordered position, active id, and either Home or a committed canonical HTTP(S) URL. Restore a page
   as `BrowserMode.Regular` and let normal discovery/consent validation reactivate SiteSkin. Do not
   store a `SiteSkinConfiguration`, manifest body, page title/content, error, editable address,
   loading flag, brand asset, pending prompt/action, or WebView bundle.

Invalid ids, duplicate ids, blank/oversized values, unsupported schemes, more than eight entries,
or an absent active id are untrusted local input after process restoration. Decode totality must be
tested. Invalid entries are dropped; ordering is preserved for valid entries; selection falls back
deterministically; no valid entries produces one fresh Home tab.

## UI and accessibility seam

`BrowserNavigationDock` currently offers Back, Forward, Reload, Home and More. `UX-011` owns its
larger persistent-shell redesign, so this ticket should add a real browser-owned Tabs entry rather
than restructure all chrome. A modal/sheet-style switcher can list the bounded eight tabs, selected
state and close action, plus a new-tab action that becomes visibly disabled with an explanatory
limit label. Existing `WeboraButton`/`WeboraIconButton` wrappers enforce 48 dp targets.

The UI needs stable tags for open switcher, list, tab row, close and new actions. Labels must be
browser-authored and can interpolate only a bounded observed registrable domain or a generic
browser-authored “Page” fallback; raw address edits and manifest branding are excluded.

## Async and renderer risks

- Discovery callbacks currently compare only active origin and one global generation. A late
  background-tab result could match a newly selected same-origin tab. Each callback/publication
  needs tab id plus that tab's generation.
- Consent, menu/dialog visibility, brand asset and native external actions currently live globally.
  They must be cleared/rebound on selection or, where continuity is required, stored inside the
  owning tab runtime. They must never render against a different active id.
- Rendering only the active tab destroys inactive `AndroidView` slots. Keeping all browsed tabs in
  an identity-keyed container and changing visibility preserves live history; closed tabs must call
  `WebView.destroy()` promptly.
- WebViews share Android's profile/cookie/storage by design; “independent history” here means each
  renderer's navigation list and browser state, not incognito storage isolation.

## Tests and environment

- Pure JVM tests can exhaustively prove the session reducer, cap, deterministic neighbour, snapshot
  codec and cross-tab state independence without Android.
- Existing BrowserState tests remain the negative controls for exact-origin integrated retention.
- Compose instrumentation should verify semantics and two-renderer switching/recreation, but this
  managed checkout has no `/dev/kvm` or connected device. Per repository policy, compile those
  tests and report runtime execution/screenshots unavailable rather than provisioning a software
  emulator.
- The full `scripts/pre-commit-check.sh` gate covers JVM tests, detekt, APK builds and compiled
  instrumentation sources.

## Likely affected files

- New: `browser/BrowserSession.kt`, `browser/BrowserSessionSnapshot.kt`,
  `browser/TabSwitcher.kt`, their JVM tests, and focused Android tests.
- Modified: `BrowserScreen.kt`, `BrowserChrome.kt` or its navigation dock, `BrowserState.kt`,
  `HardenedWebView.kt`, `BrowserWebViewController.kt`, `BrowsingDataCleaner.kt`, strings and
  associated tests/contracts.
- Documentation after implementation: tasklist results, review/QA reports, `CLAUDE.md`, and roadmap
  status. No protocol schema, core module or ADR change is anticipated.
