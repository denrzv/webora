# BROWSE-011 plan — A browser-owned Refresh for integrated pages

Status: PLAN_APPROVED

Issue: [denrzv/webora#116](https://github.com/denrzv/webora/issues/116)
PRD: `docs/prd/BROWSE-011.prd.md`
Research: `docs/research/BROWSE-011.md`

## Overview

Four layers, each independently green, in an order where nothing is written against a name that is
about to move:

0. **Housekeeping.** The backlog's reserved `BROWSE-011` becomes `BROWSE-012` everywhere it is
   cited. Docs only, no code, and first — so no later artifact here cites a colliding id.
1. **Decision.** One pure `refreshAction(BrowserState): RefreshAction` — reload the committed page,
   re-issue the exact retry URL, or refuse. The JVM gate drives every row.
2. **Component.** A browser-owned control row inside `SiteSkinTopBar`, below the untouched brand
   row, holding Refresh in the same Webora-token tile the header's Back already uses.
3. **Wiring.** `BrowserScreen` gives both the integrated header and the regular dock that one
   decision, replacing the copy at `BrowserScreen.kt:788-791`.

Nothing in the renderer, event-routing, discovery, consent or capture layers is edited. That is the
plan's main structural claim: every cross-tab and lifecycle criterion in the issue is satisfied by
*not* adding a second path, and the tests exist to prove that the absence is real rather than
assumed.

## Flow

1. The user taps Refresh in the integrated header. The control is composed only under
   `TopChrome.PROTECTED_INTEGRATED`, so Home and regular mode never compose it at all.
2. `BrowserScreen` evaluates `refreshAction(state)` for the **selected** tab's state and dispatches
   to the **selected** tab's `controller` — the same object hosting that tab's renderer, received by
   `RegularBrowser` as a parameter. A tap is a user action, which `BROWSE-009` permits to be
   active-addressed; the rule it forbids is active-addressing renderer *observations*.
3. `RefreshAction.Reload` → `controller.reload()`. No `loadUrl`, so no history entry is appended and
   `hostedUrl` is not rewritten — `rendererMountAction` compares that value against
   `state.displayedUrl` on every mount and must keep seeing the same pair.
4. `RefreshAction.Retry(url)` → `controller.navigate(url)`, the same call `BrowserErrorPage`'s Retry
   already makes for the same tab and the same URL.
5. `RefreshAction.None` → the control is disabled and there is nothing to dispatch.
6. The renderer emits `PageStarted` → … → `MainFrameCompleted` or `MainFrameFailed`, each carrying
   the `tabId` fixed when the renderer was built. `routeRendererEvent` applies them to that tab,
   whichever tab is selected by the time they land. Loading state, history capability, transport and
   `loadFailure` all restart and settle through that unchanged pipeline.
7. `PageStarted` advances that tab's generation and starts discovery for the reloaded URL. Same
   origin → `forObservedOrigin` returns the *same* `Integrated` instance, so branding does not flash
   and the brand-asset effect does not re-run. Off-origin redirect → `Regular`, and integrated chrome
   including Refresh disappears with it.

## Data

- **Trust boundary.** `RefreshAction` is browser-owned data derived from browser-observed state.
  Its inputs are `BrowserState.displayedUrl` and `BrowserState.loadFailure` and nothing else — no
  `SiteSkinConfiguration`, no `BrowserMode`, no manifest-derived value reaches it. It is a closed
  sealed hierarchy: no generic "navigate to this" case, for the reason `RendererEffect` has no
  "run this lambda" case.
- **Storage / cache keys.** None. Refresh persists nothing and reads no store.

## Security

- **Origin binding.** Refresh introduces no origin decision. `Reload` targets the renderer's current
  page; `Retry` targets a URL `observeFailure` already restricted to the exact observed HTTP(S) URL
  via `resolveAddressInput(url)?.takeIf { it == url }`. Both re-enter the existing exact-origin
  activation and teardown rules through `observePage`.
- **Allow-lists.** Unchanged. `Retry`'s URL cannot be a non-HTTP(S) scheme because
  `resolveAddressInput` will not produce one that equals a non-HTTP(S) input.
- **Fallback on failure.** A reload that fails ends in the tab's existing error page. A tab with no
  reloadable target yields `None` and a visible, disabled control — `UX-016`'s treatment of an
  unavailable command, not an absent one.
- **The manifest's whole surface here is the header's background colour**, already contrast-guarded
  by `SiteSkinColorScheme`. The control's tile, icon tint and label are Webora tokens and compiled
  resources. Proven by a source scan, not by inspection.
- **No permission, scheme, capability or bridge is added.**

## Origin-boundary contract, stated before the file list

Refresh is browser chrome hosted inside a container a website paints, and the contract is the same
one `UX-014` wrote for the header's Back: **the site owns the ground, the browser owns the tile.**
Concretely, and each clause is a test below, not a promise:

- The row is composed unconditionally under `PROTECTED_INTEGRATED`. There is no model field, count,
  flag or list index through which a manifest can suppress, duplicate or reorder it.
- Its icon is `R.drawable.ic_reload` and its label `R.string.reload` — the same command name regular
  mode uses, because `DEVX-003`'s rule is that one command does not acquire two names.
- Its tile reads `MaterialTheme.colorScheme.surfaceContainer` and its content the matching `on` role.
  No `presentation.colors.*` value may appear inside the control's declaration.
- Its enabled state is `refreshAction(state) != RefreshAction.None`, computed from browser-observed
  state.
- Its callback reaches `BrowserWebViewController` by tab id, never `ActionResolver`.

The site's own `refresh` action keeps its existing, separate path at `BrowserScreen.kt:299`. Two
refreshes may legitimately be on screen; they are different things and must remain distinguishable
in the semantics tree, which the control's tag and browser-authored description give it.

## File-by-file plan

### New: `app/src/main/java/app/webora/browser/browser/PageRefresh.kt`

`RefreshAction` (sealed: `Reload`, `Retry(url)`, `None`) and `refreshAction(state)`. The one owner of
what refreshing means, for both modes.

Why a function and not `controller::reload` at two call sites: `UX-021` records the cost of the
alternative — regular chrome and the integrated chip each carried a verbatim copy of one `when`, and
"a re-pointed branch in one file drifted from the other with nothing failing". It is also the only
form the JVM gate can drive, which `BROWSE-009` and `BROWSE-010` both had to discover the hard way.

Rows, and the evidence for the second one:

| State | Action | Why |
|---|---|---|
| `loadFailure` with a non-null `retryUrl` | `Retry(retryUrl)` | `RendererMountAction` records that after a failed navigation `WebView.getUrl()` "may be the failed URL, the previously committed URL or `about:blank`" — so `reload()` here is a call whose target the browser cannot name. The retry URL is the target it *can* name, and `BrowserErrorPage` already navigates to exactly it. |
| `displayedUrl` blank | `None` | Pristine Home/new-tab. Nothing to reload. |
| otherwise | `Reload` | The committed page, re-fetched in place, appending no history entry. |

A failure whose `retryUrl` is null (the URL was not an exact HTTP(S) round-trip) falls through to the
`displayedUrl` rows rather than to `None`: the tab still has a committed page.

### Modified: `app/src/main/java/app/webora/browser/siteskin/SiteSkinTopBar.kt`

- `SiteSkinTopBar` gains `canRefresh: Boolean` and `onRefresh: () -> Unit`. **Neither is defaulted** —
  `DEVX-003`: an offered browser command whose handler does nothing is the failure the offered list
  exists to prevent, and a default no-op puts it one call site away.
- Its body becomes a `Column`: the existing brand row, extracted verbatim into a private
  `BrandRow(...)`, then `BrowserControlRow(...)`. Extraction rather than a `LongMethod` suppression —
  a suppression is a reviewed exception with a ticket in this repo and this needs neither.
- `BrowserBack`'s tile generalises into one private `BrowserControlTile(tag) { … }` used by both Back
  and Refresh, so the browser-owned sub-surface treatment inside a site-painted header has one
  declaration. Back's tag, icon, label, enabled source and callback are unchanged.
- The control row is `Arrangement.End` — **no `Modifier.weight(1f)`**, deliberately.
  `SiteSkinTopBarContractTest` locates the title column by the *first* `Modifier.weight(1f)` in the
  file, and a second weighted child would make that assertion depend on declaration order for a
  reason unrelated to what it is asserting.
- New tags: `SITESKIN_REFRESH_TAG`, `SITESKIN_CONTROLS_TAG`.
- Detekt: the function reaches 7 parameters against a ceiling of 6, so it carries
  `@Suppress("LongParameterList")` — the same treatment `SiteSkinDock` already documents for the same
  reason. Bundling four unrelated browser controls into a holder type to dodge a lint threshold would
  invent a model nothing else needs.

### Modified: `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`

- One `dispatchRefresh` lambda beside the existing `navigateBack`, applying `refreshAction(state)` to
  `controller`. Built where `controller` and `state` are already the selected tab's, so there is no
  new id resolution to get wrong.
- `RegularBrowser` gains `canRefresh` / `onRefresh` parameters and passes them to `SiteSkinTopBar`.
- The regular arm's `canReload = state.displayedUrl.isNotBlank()` and `onReload = controller::reload`
  become the shared decision. `BrowserNavigationShell`'s signature does not change.
- Home's shell keeps its literal `canReload = false`; Home composes no tab state to derive it from,
  and `refreshAction(BrowserState())` returning `None` is asserted separately so the two agree.
- `ResolvedAction.Refresh -> controller.reload()` is **not** edited. It is the manifest's item.

### Modified: `app/src/main/res/values/strings.xml`

Nothing. `R.string.reload` already exists and is the name this command keeps in both modes.

### Modified: `app/src/androidTest/.../SiteSkinTopBarTest.kt`

Its helper calls `SiteSkinTopBar(...)` positionally and must pass the new arguments. Updated, not
defaulted around.

### Docs, in TASK-1 and TASK-6

`docs/BACKLOG.md`, `docs/ROADMAP.md`, `docs/tasklist/BROWSE-010.md`, `CLAUDE.md` — the `BROWSE-012`
renumbering, then the ticket's own `CLAUDE.md` note at the end.

## Tests

Every security-relevant assertion carries a negative control, and the control's *result* is recorded
in the tasklist. Controls that fail nothing are the finding, per `UX-021`'s `PageStarted` case.

| Test | Layer | Covers | Negative control |
|---|---|---|---|
| `PageRefreshTest` | JVM | every `refreshAction` row: committed page → `Reload`; failure with retry URL → `Retry` with that exact URL; failure without one → `Reload`; blank `displayedUrl` → `None`; `BrowserState()` → `None` | re-point the failure row at `Reload` and assert the retry case fails |
| `PageRefreshTest` (manifest row) | JVM | an `Integrated` state with a hostile manifest produces the same action as the identical `Regular` state | make `refreshAction` read `mode` and watch the pair diverge |
| `SiteSkinTopBarContractTest` (new cases) | JVM source scan | the refresh control's declaration contains no `presentation`/`colors.*`/`model.*` value; its tile grounds on `surfaceContainer`; `testTag(SITESKIN_REFRESH_TAG)` is *applied*, not merely declared; the brand row's chip assertions still hold | ground the tile on `presentation.colors.secondary` and watch the isolation case fail |
| `BrowserChromeContractTest` (new case) | JVM source scan | exactly one file names the reload decision, and `BrowserScreen` reaches it for both modes rather than calling `controller::reload` in the regular arm | reintroduce `onReload = controller::reload` and watch it fail |
| `SiteSkinTopBarTest` (new cases) | Compose | Refresh displayed, enabled, ≥48 dp, dispatches once; disabled state when `canRefresh` is false; its description distinct from the trust chip's; at 320 dp × 200 % font scale the control and the chip are both inside the host | assert the chip's existing 140 dp floor still holds, which the row must not have taken width from |
| `TabRendererIsolationTest` (new case) | Instrumented | refreshing tab A leaves tab B's displayed URL, loading flag and mode untouched, including after switching to B mid-flight | drive the refresh through `session.activeId` at delivery time |
| existing `RendererMountActionTest`, `RendererMountContractTest`, `BrowserStateTest`, `BrowserChromeTest`, `SiteSkinDockTest`, `ExpressiveBloomJourneyContractTest` | — | unchanged and must stay green: the dock's five commands, the mount rule, the frame inventory | — |

The instrumented cases are **evidence, never a gate claim** — `CI-002`, `CI-003`, `CI-004` and
`CI-005` each recorded theirs the same way, and `./gradlew test` does not compile `androidTest`. The
tasklist runs `:app:compileDebugAndroidTestKotlin` explicitly for that reason.

## Rollout / versioning

No protocol change. `SPEC.md`, the schema, `spec/diagnostics.json`, the fixture corpus and the
`bloom-flowers` digest are all untouched — this ticket adds no manifest field and reads none.

`CI-009`'s eight-frame inventory, its per-frame checks and `ScreenEvidenceGuard` are untouched. The
integrated frames photograph a header that is one row taller, so **hosted acceptance is re-taken, not
inherited**. No capture policy, deadline, threshold, exclusion or dismissal is edited; `CI-005`'s
standing constraint — a change here may only make the harness refuse more — is satisfied vacuously
by editing none of it.

## Open questions

Resolved from research:

- **The row belongs to `SiteSkinTopBar`, not `ExpressiveSiteSkinHeader`.** `UX-013` made the header a
  geometry-only container that takes content from its caller and forbids the caller supplying
  geometry; putting a specific browser command inside it would reverse that. A future browser control
  in integrated mode joins the same row, in the same file, beside Back and Refresh.
- **The site's own `refresh` action stays.** Suppressing it is a validation-surface change and would
  need its own ticket; it is also the wrong direction — a site is entitled to publish the item, and
  the defect this ticket fixes is that the *browser* had none.

None open.
