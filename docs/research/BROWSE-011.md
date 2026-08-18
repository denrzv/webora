# BROWSE-011: Research
Status: RESEARCH_READY

## Question

Three things the plan cannot decide without a map:

1. **Where does a browser command live inside a container a website paints?** The integrated header
   and dock are both site-coloured. Every existing browser control in them (Back, Forward, Tabs,
   More, the trust chip) answers this already; Refresh must answer it the same way rather than
   inventing a treatment.
2. **What does "refresh" mean when the current page failed?** `reload()` is obviously right for a
   page that loaded. For a tab showing an error page the browser has a *second* existing answer —
   `BrowserErrorPage`'s Retry, which re-issues `loadFailure.retryUrl` — and the plan has to pick one
   owner rather than leave two.
3. **Which layer can the JVM gate actually drive?** `BROWSE-009` and `BROWSE-010` both found that
   the decision worth testing was trapped inside a `@Composable`. Refresh has the same shape.

## Origins involved

- **The serving origin of the selected tab's committed main frame.** It is the only origin in play.
  Refresh issues no request to a new origin: `reload()` re-fetches the renderer's current page, and
  the failure path re-issues `loadFailure.retryUrl`, which `observeFailure` already restricted to
  the exact observed HTTP(S) URL (`resolveAddressInput(url)?.takeIf { it == url }`).
- **No asset origin.** Refresh reuses `ic_reload`, a bundled drawable. No image, font or manifest
  asset is fetched, so there is no same-origin argument to make.
- **A reload that redirects off-origin** is observed by the existing pipeline: `onPageStarted` →
  `PageStarted` → `observePage` → `mode.forObservedOrigin(...)`, which keeps `Integrated` only when
  `SiteOrigin` equality holds on the full canonical tuple. Integrated chrome — and therefore Refresh
  itself, since it is only composed in `PROTECTED_INTEGRATED` — tears down through that path with no
  new code.

## Manifest-controlled surface

If this ships as scoped, a website can influence exactly one thing about Refresh: **the colour of
the header behind it**, through `ExpressiveSiteSkinPresentation.from(configuration, …)`, which is
already the contrast-guarded `SiteSkinColorScheme` and nothing else.

It cannot influence the control itself. The mechanisms that prevent each attempt already exist and
are the reason no new one is needed:

| Attempt | What stops it |
|---|---|
| Hide or omit Refresh | It is composed unconditionally by `SiteSkinTopBar`, which `RegularBrowser` reaches only through `handoff.top == TopChrome.PROTECTED_INTEGRATED`. There is no model field to read. |
| Rename it | The label is `R.string.reload`, resolved by `stringResource`. `BrowserSurfaceConventionsTest` forbids a string literal reaching an accessible-name argument at all. |
| Supply its icon | `R.drawable.ic_reload`, a compiled id. `BrowserIconContractTest` forbids `getIdentifier(`, `Uri.parse(`, `File(`, `URL(` anywhere in the icon path. |
| Reorder or reposition it | Its row is a compiled sibling of the brand row inside the header, with no index, weight or slot derived from `SiteSkinConfiguration`. |
| Change its enabled state | Enablement comes from `BrowserState`, which is browser-observed. `refreshAction` (below) reads `displayedUrl` and `loadFailure` and no configuration at all. |
| Dispatch it | The callback is a `BrowserWebViewController` method reached by the selected tab's id. `ActionResolver` cannot produce it; `ResolvedAction.Refresh` is a *site* item and keeps its own separate dispatch. |
| Intercept it | `addJavascriptInterface` does not exist (`BROWSE-001`), and a reload is a renderer operation with no page-visible native seam. |

**A site may still publish its own `refresh` action**, and should be able to: `ResolvedAction.Refresh
-> controller.reload()` at `BrowserScreen.kt:299` is a legal manifest item today. The two coexisting
is correct — one is the site's item in the hub, one is Webora's chrome in the header — but they must
not be confusable in the semantics tree, and Webora's must not be the one that disappears when the
manifest stops offering it. Today it *is*: the site's item is the only refresh an integrated user
can see. That is the defect.

## Browser-owned remainder

- **Presence, position, order, icon, label, enabled state and callback of Refresh.** Enforced by the
  same three mechanisms `UX-015` uses for the dock's five commands: compiled composition, compiled
  resources, and a source scan that no manifest value reaches them.
- **Its sub-surface colour.** The header's Back already establishes the treatment — a browser-token
  tile (`MaterialTheme.colorScheme.surfaceContainer`) inside the site-painted header, which
  `UX-014` records as deliberate: *"the visual boundary is the ownership boundary"*. Refresh reuses
  it, and the two should reach it through one shared declaration rather than two copies.
- **Which renderer reloads.** The tab id, resolved at the call site the way `BROWSE-009` permits for
  a *user action* — `RegularBrowser` receives the selected tab's `controller` as a parameter, the
  same one hosting its renderer. Renderer *observations* are the ones that may not be
  active-addressed; a tap belongs to the tab on screen. The distinction is already visible in
  `RegularBrowser`'s seam (`onAddressEdited` vs `onRendererEvent`) and Refresh joins the first.

## The failure-path decision, with the evidence

`observeFailure` does not clear `displayedUrl`, so a failed tab still has one and Refresh is
correctly *enabled* there. What it should *do* is the open question, and the answer is not
`reload()`:

- `BrowserWebViewController.observed()` is deliberately not written from `onMainFrameFailed` —
  "the browser's last request stands" — precisely so an error tab does not reload on tab switch.
- `RendererMountAction`'s KDoc records the matching framework fact: after a failed navigation
  `WebView.getUrl()` "may be the failed URL, the previously committed URL or `about:blank`". A
  `reload()` on that renderer is a call whose target the browser cannot name.
- The browser already has a named answer for this exact situation: `BrowserErrorPage`'s Retry, wired
  to `failure.retryUrl?.let(controller::navigate)`. Both call sites exist; neither is new.

So the plan should introduce one pure decision — reload the committed page, re-issue the exact retry
URL, or refuse — and give **both** modes that one owner. `UX-021` records what the alternative costs:
regular chrome and the integrated chip each carried a verbatim copy of the transport→string `when`,
and "a re-pointed branch in one file drifted from the other with nothing failing". A reload rule
copied into two docks is the same shape.

This does change regular-mode dispatch on a *failed* page from `reload()` to the retry URL. That is
not a regression: it is the behaviour the error page's own button already has, arrived at from the
other direction, and it removes the one case where the regular dock's Reload had no nameable target.

## The placement measurement

Carried from the PRD because the plan's file list depends on it. On the 320 dp host this repository
treats as its floor:

| Candidate | Arithmetic | Outcome |
|---|---|---|
| Trailing icon in the brand row (the issue's sketch) | 320 − 40 gutters = 280. Back 48 + logo 40 + gaps 28 = 116 fixed → 164 for title + chip. Refresh 48 + gap 8 → **108**. The chip is unweighted and measures first, wanting ~121 for `denrzv.github.io`. | Chip truncates, weighted title measures **0**, at default font scale. Rejected. |
| Sixth dock slot | 320 − 24 inset − 16 padding = 280; six equal slots = **46.7 dp**. | Below `MINIMUM_TOUCH_TARGET`. Rejected, and separately forbidden by `UX-015`. |
| Replacing the header's Back | Width-neutral; the dock and the system gesture both still offer Back. | Reverses `UX-008`/`UX-014`. A navigation-contract decision, not a side effect of adding Reload. Rejected. |
| **Second browser-owned control row inside the header** | Costs ~48 dp of header height. The brand row is not edited. | **Chosen.** |

The chosen row leaves `SITESKIN_SECURITY_TAG`, the chip's ground, its width floor and the brand row's
declaration order untouched — so `SiteSkinTopBarContractTest`'s five structural assertions and
`SiteSkinTopBarTest`'s bounds assertions keep meaning exactly what they meant when they were written.

## Relevant code

| Path | Why it matters |
|---|---|
| `siteskin/SiteSkinTopBar.kt` | Where the control row goes. Its body is already near detekt's 40-line `LongMethod` ceiling, so the brand row extracts to a private composable. `BrowserBack`'s tile is the treatment Refresh reuses. |
| `siteskin/ExpressiveSiteSkinChrome.kt` | `ExpressiveSiteSkinHeader` takes `content: @Composable BoxScope.() -> Unit` and is `heightIn(min = 96.dp)`, so a two-row `Column` needs no geometry change. `UX-013` forbids the caller supplying geometry; it is not supplying any. |
| `browser/BrowserScreen.kt:699-716` | The `TopChrome.PROTECTED_INTEGRATED` arm that constructs `SiteSkinTopBar`. Refresh's enabled state and callback are wired here, from `state` and `controller` — both already the selected tab's. |
| `browser/BrowserScreen.kt:788-791` | Regular mode's `canReload = state.displayedUrl.isNotBlank()` and `onReload = controller::reload` — the copy the new pure owner replaces. |
| `browser/BrowserScreen.kt:299` | `ResolvedAction.Refresh -> controller.reload()`, the *site* action. Deliberately left alone; it is the manifest's item, not the browser's chrome. |
| `browser/BrowserState.kt` | `displayedUrl`, `loadFailure` and `observeFailure`'s `retryUrl` restriction — the only inputs the refresh decision may read. |
| `web/BrowserWebViewController.kt` | `reload()`, `navigate()`, `hostedUrl`. Reload must not write `hostedUrl`; it does not change the page the tab is on, and `rendererMountAction` compares that value against `state.displayedUrl` on every mount. |
| `browser/RendererOwnership.kt` | The events a reload produces route back through `routeRendererEvent` under the originating tab's id. Nothing here changes; the cross-tab criteria are satisfied by *not* adding a second path. |
| `browser/BrowserChrome.kt:132-193` | `BrowserNavigationShell`'s `canReload`/`onReload` seam. Its signature is unchanged; only what `BrowserScreen` passes into it changes. |
| `test/.../SiteSkinTopBarContractTest.kt` | The source-scan idiom this ticket's isolation test must follow, including `executableLines` — `BROWSE-009`'s "a scan reads code and not prose". |
| `androidTest/.../SiteSkinTopBarTest.kt` | Calls `SiteSkinTopBar(model, presentation, canGoBack, onBack)` positionally; new parameters break it and it must be updated, not defaulted around. |
| `test/.../BrowserIconContractTest.kt` | `BUDGET = 20` and `EXPECTED` already contain `ic_reload`. Reusing it means no budget change — and a *new* icon would need `UX-002`'s deliberate raise. |
| `test/.../evidence/ExpressiveBloomJourneyContractTest.kt` | `CI-009`'s frame inventory. No frame is added or removed; the integrated frames' header changes shape, so hosted acceptance is re-taken but the contract is not edited. |

## Prior art

- `ADR-006` / `HARDEN-002` — browser-owned chrome inside site-painted surfaces; why the chip's ground
  and the Back tile are compiled colours.
- `UX-008` / `UX-014` — the header's Back, and the rule that its sub-surface uses Webora tokens
  because "the visual boundary is the ownership boundary".
- `UX-015` — the dock's five fixed commands, and the rule that site items go through the typed hub.
  The reason a sixth dock slot is not on the table even before the 46.7 dp measurement.
- `UX-016` — the regular dock's six equal slots, and the 48 dp target contract this must also meet.
- `UX-021` — the brand row's current budget, `SECURITY_CHIP_MAX_WIDTH`'s inertness, and "one `when`,
  one owner", which is the argument for a single reload decision.
- `UX-023` — the standing backlog item that the brand row cannot hold four things at 200% scale.
  This ticket must not deepen it, which is why the row is not where Refresh goes.
- `BROWSE-009` — user actions may be active-addressed; renderer observations may not. Also the
  source-scan rules: executable lines only, and forbid the mechanism rather than a spelling.
- `BROWSE-010` — `rendererMountAction`, `hostedUrl`, and why a rule built on `WebView.getUrl()` after
  a failure is wrong. Directly decides the failure path here.
- `CI-003` / `CI-005` — `BROWSE_CONTENT_TAG` is the measured page rectangle. This is the reason a
  floating over-content refresh button was never a candidate: browser pixels inside that rectangle
  contaminate the rendered-content check.

## Risks

- **`CI-009` is pending hosted acceptance and photographs this header.** → The plan must state that
  the frame inventory and its tag assertions are unchanged, verify that nothing in
  `ExpressiveBloomJourneyContractTest` or `ScreenEvidenceGuard` needs editing, and record that
  hosted acceptance is re-taken rather than inherited. It must not touch a capture policy;
  `CI-005`'s constraint is that a change may only make the harness refuse *more*.
- **Regular-mode dispatch changes on the failure path.** → The plan owns an explicit test for both
  modes reaching the same decision, and a negative control that a second copy of the rule fails.
- **The control row is new browser surface.** → `BrowserSurfaceConventionsTest` scans every
  `@Composable` it discovers, so the row is covered the moment it exists — no string literal, no raw
  Material `Button`/`IconButton`. That is a gate, not a review item.
- **Detekt.** → `SiteSkinTopBar` gains parameters (ceiling 6) and lines (ceiling 40). The plan
  extracts the brand row rather than adding a suppression for length; a suppression is a reviewed
  exception with a ticket in this repo, and this needs neither.
- **A no-op default handler.** → `DEVX-003`: an offered browser command whose handler does nothing
  is the failure the offered list exists to prevent. `onRefresh` takes no default, so a call site
  that forgets it is a compile error.

## Open questions

- **Does the row belong to `SiteSkinTopBar` or to `ExpressiveSiteSkinHeader`?** Carried into `/plan`.
  The header is `UX-013`'s geometry-only container and takes content from its caller, which argues
  for the top bar owning the row; the counter-argument is that a future browser control in
  integrated mode would then have no obvious home. Not resolved here.
- **Should the site's own `refresh` action be suppressed from the hub now that the browser offers
  one?** Deliberately not answered. It is a manifest item the site is entitled to publish, and
  removing it would be a validation-surface change belonging to its own ticket.
