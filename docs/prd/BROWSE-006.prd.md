# BROWSE-006: Multi-tab browsing and session model
Status: PRD_READY

## Context / Problem

Webora currently owns exactly one `BrowserState` and one `WebView` controller inside
`BrowserScreen`. Navigating Home replaces that single context, so users cannot keep two sites open,
compare pages, or return to an independent history. More importantly, SiteSkin mode and its
browser-owned security identity currently have no tab boundary to prevent a future tab UI from
accidentally reusing one tab's chrome for another.

M8 needs a real session foundation before it adds a persistent shell. This ticket introduces the
browser-owned model and a usable tab switcher while preserving the existing rule that the active
page's observed origin—not remote configuration—selects its security and SiteSkin presentation.

## Goals

- Model a browsing session as an ordered, non-empty, bounded set of independently identified tabs.
- Give each tab its own URL, navigation capability, load state, mode, and live WebView history.
- Let users create, select, and close tabs through a browser-owned switcher.
- Restore the ordered tab set and selected tab after Activity or process recreation without
  persisting page pixels, trusted manifest objects, or website-controlled chrome.
- Make the maximum of eight tabs visible and explain why creation is unavailable at the limit.

## Non-goals

- Persisting WebView back/forward entries across process death; restoration reloads the committed
  URL while live tabs retain their independent in-memory histories.
- Adding recents, history, favourites, incognito, tab groups, cross-device sync, or background tab
  loading (`BROWSE-007` and later work).
- Redesigning Home/regular/SiteSkin chrome or the broader handoff animation (`UX-011`, `UX-012`).
- Allowing a manifest to name, order, close, select, style, or otherwise influence tabs.

## User stories

- As a user, I can open a fresh Home tab, browse independently in it, and return to my previous tab
  with its URL and back/forward history intact.
- As a user, I can see which tab is selected, switch to another one, and close tabs with predictable
  neighbour selection.
- As a user, closing my final tab gives me one fresh Home tab instead of exiting or showing an
  invalid empty session.
- As a user returning after recreation, I recover the same ordered tabs and active selection at
  their last committed URLs.
- As a security-conscious user, switching between a SiteSkin tab and a regular tab never carries
  across the other tab's branding, navigation selection, or domain/TLS identity.

## Acceptance criteria

1. A session always contains between one and eight uniquely identified tabs and exactly one active
   tab; a new tab is appended, selected, and starts in `BrowserMode.Home`.
2. Selecting a tab restores that tab's own displayed URL, load state, back/forward capability,
   `BrowserMode`, and live WebView history without copying state from the previously active tab.
3. Closing an inactive tab keeps the current selection; closing the active tab selects the tab that
   follows it, or the preceding tab when it was last; closing the only tab creates one fresh Home
   tab.
4. At eight tabs the switcher visibly communicates the limit and its new-tab action is disabled;
   the model neither evicts an existing tab nor silently creates a ninth.
5. The ordered tab metadata and active id survive Activity/process recreation. Home tabs restore as
   Home and browsed tabs restore from their committed HTTP(S) URL in regular mode; trusted SiteSkin
   configuration and page-rendered content are rediscovered rather than serialized.
6. One tab can remain SiteSkin-integrated while another is regular, and switching cannot leak
   SiteSkin chrome, active navigation, brand asset, consent prompt, pending action, security domain,
   or TLS presentation between them.
7. The tab switcher, create action, tab selection, and close action use browser-authored labels,
   stable semantics, and at least 48 dp interactive targets; untrusted page or manifest text cannot
   become an action label.
8. State/model tests cover create/select/close ordering, the final-tab invariant, the tab cap,
   snapshot restoration, malformed persisted metadata, independent navigation state, and a
   SiteSkin-to-regular negative control.
9. Relevant instrumentation tests compile; when a connected Android device is available, they
   demonstrate switching between two live WebViews and restoration across Activity recreation.
10. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: tabs and persistence are browser-owned. Persist only bounded identifiers,
  ordering, selection, Home-vs-page kind, and committed HTTP(S) URLs; never persist manifest JSON,
  `SiteSkinConfiguration`, form/page content, pending external actions, or brand assets.
- Reliability/fallback: corrupt, duplicate, over-limit, unsupported-scheme, or empty restoration
  input fails closed to a valid bounded session, with one fresh Home tab when nothing valid remains.
- Performance: keep at most eight live WebViews, dispose a closed tab promptly, and do not preload a
  page for a fresh Home tab. Switching an existing tab must not initiate navigation.
- Accessibility: every tab announces a browser-bounded title/domain fallback, selected state, and
  position/count; close and create affordances have browser-owned descriptions and 48 dp targets.

## Risks

- Compose identity mistakes can reuse or destroy a `WebView` across tab switches, collapsing live
  history or showing the wrong page beneath the selected chrome.
- Serializing `BrowserMode.Integrated` would turn stale remote configuration into trusted restored
  state; restoration must deliberately downgrade browsed tabs to regular rediscovery.
- Per-tab asynchronous discovery, consent, asset, and external-action state can publish after a tab
  switch unless callbacks are keyed by tab id and generation.
- Eight live WebViews have a meaningful memory cost. The cap is deliberately conservative for an
  MVP browser and must remain an explicit product limit rather than an eviction policy.

## Open questions

- None. Session history persistence beyond the committed URL is intentionally deferred; Android's
  opaque WebView state is retained for live Activity recreation only when it can be associated with
  the same browser-owned tab id.
