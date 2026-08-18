# BROWSE-011: A browser-owned Refresh for integrated pages
Status: PRD_READY

## Context / Problem

Reload is a fundamental browser command, and in Webora it is one everywhere except the one mode
where the browser has given the most screen area away.

`UX-016` fixed the regular/Home dock at Back / Forward / Reload / Home / Tabs / More. `UX-015` fixed
the protected integrated dock at Back / Forward / Brand Hub / Tabs / More — five commands, and
Reload is not among them. So a user reading Bloom Flowers has no one-tap way to reload the page they
are looking at. The command exists (`BrowserWebViewController.reload`) and is reachable from
integrated mode only by a route the user cannot be expected to find: `ResolvedAction.Refresh`, which
is a *site* action a manifest may or may not publish.

That last detail is the sharp edge. Today the only refresh a user sees on an integrated page is one
the **website** decided to offer. A browser command that is present exactly when a site chooses to
offer it is not a browser command. `BROWSE-011` adds Webora's own.

Issue [#116](https://github.com/denrzv/webora/issues/116) is the source of record.

## Ticket-id collision, resolved here

`docs/BACKLOG.md` had already reserved `BROWSE-011` for *Back after a Home round trip reaches a page
the tab forgot*, filed by `BROWSE-010`'s research. Issue #116 then used `BROWSE-011` for this work.
Ids are coined per work theme and registered nowhere (`workflow.md`), so there is no authority to
appeal to — but two artifacts under one id is exactly the confusion the id exists to prevent.

The tracked, opened issue keeps the id. The backlog entry is renumbered to `BROWSE-012`, and every
citation of it moves with it: `docs/BACKLOG.md`, `docs/ROADMAP.md`, `docs/tasklist/BROWSE-010.md`
and `CLAUDE.md`'s `BROWSE-010` note. That renumbering is TASK-1, ahead of any code, so no later
artifact is written against an id that is about to move.

## Goals

- A directly visible, browser-owned Refresh control in protected integrated mode that reloads the
  active tab's retained `WebView` and nothing else.
- Reuse the existing controller reload path. No second navigation mechanism.
- Keep every frozen contract this touches intact: `UX-015`'s five dock commands and their order,
  `UX-021`'s brand row and trust chip, `UX-008`/`UX-014`'s browser Back, `UX-016`'s regular dock.
- Keep the command unreachable by a manifest — it cannot be hidden, renamed, reordered, restyled,
  re-iconed, re-enabled or dispatched from remote data.

## Non-goals

- Pull-to-refresh, automatic/periodic refresh, hard reload, cache-bypass, cookie/storage clearing.
- Redesigning the regular browser dock, or changing what regular-mode Reload does.
- Making the integrated header responsive at 200% font scale — that is `UX-023` and stays there.
- Deciding whether Home is a history root — that is the renumbered `BROWSE-012`.

## Placement, and the measurement that chose it

Issue #116 sketches Refresh as a trailing icon in the integrated brand row and pre-authorises
another visible browser-owned placement "if implementation evidence shows that the top-bar placement
conflicts with the accepted `UX-021` title/shield layout". It does. The arithmetic, on the 320 dp
host this repository treats as its floor:

| Placement | Measurement | Verdict |
|---|---|---|
| Trailing icon in the brand row | 280 dp of row after the 20 dp expressive gutters. Back 48 + logo 40 + three gaps 28 = 116 fixed, leaving **164 dp** for the weighted title and the trust chip. Refresh (48) plus its gap (8) takes that to **108 dp**. The chip is unweighted, so it measures first and wants ~121 dp for a domain like `denrzv.github.io` — the chip truncates and the weighted title measures to **zero**, at *default* font scale. | Rejected. It regresses `UX-021` and deepens `UX-023`. |
| Sixth dock slot | 280 dp of dock after the 12 dp inset and 8 dp padding. Six equal slots are **46.7 dp**, below `MINIMUM_TOUCH_TARGET`. | Rejected — and separately forbidden by `UX-015`'s fixed five. |
| Replacing the header's Back | Width-neutral: the dock already carries Back, as does system Back. | Rejected. Reversing `UX-008`/`UX-014`'s "top chrome always carries a leading Back" is a navigation-contract decision, not a side effect of adding Reload. |
| **A second browser-owned control row inside the header** | Costs header height, not row width. The brand row is untouched. | **Chosen.** |

The chosen row is browser-owned space below the brand row and inside `ExpressiveSiteSkinHeader`,
holding Refresh at the trailing edge — the position the issue's sketch asks for, on the line that has
room for it. The brand row is not edited at all, so `SITESKIN_SECURITY_TAG`, the chip's geometry and
`CI-009`'s pending hosted frames keep exactly the meaning they were accepted with.

## User stories

1. As someone reading a SiteSkin-integrated page that loaded stale or failed, I tap one visible
   browser control and the page I am on reloads.
2. As someone with two tabs open, refreshing tab A leaves tab B's page, loading state and identity
   untouched — including when I switch to B while A is still reloading.
3. As a site owner, I cannot make Webora's Refresh disappear, wear my icon, carry my label, or run
   my action, whatever my manifest says.
4. As a screen-reader user, Refresh announces itself as a browser action, distinctly from the trust
   shield beside it, on a target I can hit.

## Acceptance criteria

1. Protected integrated mode composes a browser-owned Refresh control that is visible without
   opening the SiteSkin hub, the browser menu, or any site surface.
2. Activating it calls `reload()` on the controller owned by the **selected** tab, and on no other.
3. It creates no tab, replaces no renderer, and issues no `loadUrl` — the retained `WebView` reloads
   in place and `hostedUrl` is not rewritten by the act of refreshing.
4. Loading and completion state restart and settle through the existing `WebViewEvent` pipeline,
   routed by `routeRendererEvent` under the originating tab's id.
5. A refresh in tab A cannot change tab B's URL, address text, loading flag, history capability,
   `loadFailure`, transport security or `BrowserMode`, including when the callbacks land after the
   user has switched to B.
6. Reload of the same validated origin leaves the integrated configuration and branding active;
   a reload that redirects off-origin tears integrated chrome down through the existing exact-origin
   rules, with no new deactivation path.
7. The control is enabled only when the selected tab has a reloadable page, and is visible-and-
   disabled rather than absent when it does not — matching how the regular dock treats an
   unavailable command. Home/new-tab composes no integrated chrome at all and therefore no Refresh.
8. Refresh remains available after a recoverable main-frame failure, and refreshing then retries the
   tab's current target.
9. `UX-015`'s dock still has exactly Back / Forward / Brand Hub / Tabs / More, in that order.
10. `UX-021`'s brand row is unchanged: Back, logo, title/subtitle column, trust chip, in that order,
    with `SITESKIN_SECURITY_TAG` on the chip.
11. No manifest value reaches Refresh's presence, order, icon, label, enabled state, colour role or
    callback. Proven by a runtime sweep over manifests that try, not by inspection.
12. Refresh carries a browser-authored accessible name from `strings.xml`, a 48 dp target via
    `WeboraIconButton`, and semantics distinct from the non-interactive trust chip.
13. The integrated header remains operable at 320 dp width and 200% font scale, and Refresh is
    reachable at both.
14. Regular-mode Reload behaviour and its tests are unchanged.
15. The backlog's former `BROWSE-011` is `BROWSE-012` in every file that cites it, and no file cites
    `BROWSE-011` for the Home/Back defect.
16. `bash scripts/pre-commit-check.sh` passes.

## NFR

- **Security/privacy:** Refresh is browser chrome inside a container a manifest paints. The manifest
  supplies the row's background colour through the already contrast-guarded
  `ExpressiveSiteSkinPresentation` and nothing else; the icon is a bundled drawable, the label a
  compiled string, the callback a controller method reached by tab id. No new capability, permission
  or scheme is introduced, and reload re-enters the unchanged discovery/consent/validation
  lifecycle rather than bypassing it.
- **Reliability/fallback:** a reload that fails ends in the existing error page for that tab. A
  refresh with no reloadable target is not dispatchable, because the control is disabled.
- **Performance:** no new network, decode or main-thread work; one extra composed row and one
  bundled vector.
- **Accessibility:** 48 dp target, browser-authored `contentDescription`, disabled state carried by
  semantics and not by colour alone, usable at 320 dp and 200% font scale.

## Risks

- **Header height.** The row adds roughly 48 dp of permanent chrome above the page. Accepted: the
  measurement above shows the alternative is starving browser identity and the site's title on the
  row that carries them. `UX-023` owns making that row responsive and may later move controls into
  this one.
- **Screenshot evidence.** `CI-009` is pending hosted acceptance on integrated frames. This changes
  the header those frames photograph, so its inventory and tag assertions must be re-checked and
  hosted acceptance re-taken. This ticket must not weaken any capture policy to accommodate itself —
  `CI-005`'s standing constraint holds: a change here may only make the harness refuse more.
- **A second refresh on screen.** A manifest may publish its own `refresh` action, which the hub can
  show beside the browser's. That is legal and correct — one is the site's item, one is Webora's
  chrome — but the two must not be confusable in the semantics tree.

## Open questions

None blocking. The placement question the issue delegated is answered above with its measurement.
