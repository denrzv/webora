# Roadmap

Milestone tracker. The reasoning behind it is in [`DEVELOPMENT_PLAN.md`](DEVELOPMENT_PLAN.md);
the recommended implementation order is in [`DEVELOPER_PLAN.md`](DEVELOPER_PLAN.md). This file
records what is done.

Legend: `[ ]` not started · `[~]` in progress · `[x]` done

## M0 — Foundation

- [x] `FOUND-001` AIDD scaffolding, templates, commands, governance docs
- [x] `FOUND-002` Gradle: `:app` + `:siteskin-core` + `:siteskin-lint`, SDK 36, JDK 25 / jvmTarget 21
- [x] `FOUND-003` Gates, CI, SessionStart hook
- [x] `FOUND-004` ADR-001..012

## M1 — SiteSkin API (spec first, then TDD against it)

- [x] `SPEC-001` Manifest v1.0 spec, JSON Schema, conformance fixture corpus
- [x] `SPEC-002` Versioning and compatibility policy
- [x] `CORE-001` Origin model and URL resolution
- [x] `CORE-002` DTOs and parsing
- [x] `CORE-003` Schema validation
- [x] `CORE-004` Security validation and normalization
- [x] `CORE-005` Action model and resolution
- [x] `CORE-006` Navigation active-state matching
- [x] `SPEC-003` `siteskin-lint` CLI — **last in M1**, it wraps what `CORE-001..006` build

## M2 — Browser foundation

- [x] `BROWSE-001` WebView host and hardening
- [x] `BROWSE-002` Browser state machine, URL bar, navigation
- [x] `BROWSE-003` Home screen and onboarding
- [x] `BROWSE-004` Regular-mode chrome, security indicator, error pages
- [x] `BROWSE-005` External navigation, downloads, uploads

## M3 — SiteSkin runtime

- [x] `NET-001` Manifest fetcher
- [x] `NET-002` Manifest cache
- [x] `NET-003` Brand asset pipeline
- [x] `SKIN-001` Dynamic theming and contrast guard
- [x] `SKIN-002` SiteSkin top bar
- [x] `SKIN-003` Bottom navigation, quick actions, menu
- [x] `SKIN-004` Mode transitions and origin-change deactivation

## M4 — Hardening, privacy, demo

- [x] `HARDEN-001` Adversarial manifest corpus
- [x] `HARDEN-002` Brand-impersonation controls
- [x] `PRIV-001` Privacy controls and per-site toggles
- [x] `A11Y-001` Accessibility
- [x] `DEVX-001` SiteSkin Integration Inspector
- [x] `DEMO-001` Bloom Flowers reference integration

## M5 — Distribution

- [x] `DIST-001` Debug APK on a GitHub Release, with install instructions
- [x] `CI-001` Manual GitHub-hosted Android screenshots against the live integration

## M6 — Design refresh

Browser-owned surfaces only. Opened after the `DIST-001` APK was run on an emulator: the browser
gives *websites* a six-role colour system with a contrast guard and gives itself a bare
`MaterialTheme {}`. Evidence in [`design/AUDIT.md`](design/AUDIT.md); the candidate directions are
in [`design/directions/`](design/directions/index.html).

- [x] `UX-001` Design direction sketches and selection — deliverable is a decision, plus `ADR-013`
- [x] `UX-006` Attributed, bounded SiteSkin preview in first-use consent
- [x] `UX-002` Design system foundations — `WeboraTheme` tokens, browser-owned vector icon set, dark theme
- [x] `UX-003` Browser-owned chrome rebuild — address bar, navigation controls, menu, error page
- [x] `UX-004` Home, onboarding and settings surfaces

## M7 — Visual quality, evidence & integration polish

Opened from the first successful live Pixel 6 / API 33 screenshot journey. The workflow proved the
integration end to end, but the evidence also made several quality defects impossible to ignore:
System UI ANR overlays contaminated otherwise-passing captures, screenshots were cumbersome to
review, the debug inspector dominated the demo frame, SiteSkin navigation still used placeholder
Unicode glyphs, and the consent actions were visually weak on a phone-sized viewport.

Evidence/DX track — can proceed without waiting for all M6 surfaces:

- [x] `CI-002` Deterministic clean Android screenshot capture — eliminate known System UI
  contamination without hiding Webora failures
- [x] `DEVX-002` Screenshot review experience — separate human/diagnostic artifacts and add one
  labelled contact-sheet preview
- [x] `CI-003` Capture must wait for rendered content — pixel check on the page region, not semantics
- [x] `CI-004` The one permitted dismissal must actually execute — run 13 produced zero frames twice
  because the only retryable-looking verdict was the one that could not be retried. The decision is a
  pure function now, zero candidates is patient while two or more still fails closed, and the lookup
  searches the classified window rather than whatever `rootInActiveWindow` returns — behind a check
  that the window is OS-owned, since `android:id/button2` belongs to every `AlertDialog`
- [x] `CI-005` The frame saved must be the frame validated — run 14 went green with a system dialog
  in the integrated frame, and the dialog's own pixels were what cleared the rendered check
- [x] `CI-006` Hosted screenshot readiness calibration — keep the strict `<= 50%` aggregate CPU gate
  with three consecutive quiet samples, provision the Pixel 6 / API 33 AVD with 2 cores and 4 GiB,
  and allow a hosted-only 300s settle budget. Consecutive cold runs #28 and #29 settled in 98s/96s
  and each produced three uncontested canonical frames.
- [x] `DEVX-003` Inspector isolation and canonical evidence mode — the affordance moved into the two
  browser menus, one per mode, and left canonical composition. It also **disproved** `CI-003`'s
  claimed residual hole: the rendered fraction is bit-identical with the overlay and without it, so
  `SiteSkinQuickActions` was always the only non-page chrome inside the measured region, and it was
  always excluded

Product-polish track:

- [x] `UX-005` SiteSkin integrated chrome & semantic icon set — trusted semantic tokens now map to
  browser-owned vector icons, including a meaningful quick-action preview
- [x] `UX-008` Browser navigation controls in integrated mode — integrated chrome now keeps a visible
  browser-owned Back control independent of manifest styling
- [x] `UX-007` Adaptive SiteSkin consent action hierarchy — clear primary/secondary/persistent-deny
  actions across narrow widths and large font scales
- [x] `UX-020` Tab switcher modal — the last browser-owned list surface predating the design refresh
  joins `UX-018`'s modal language. Eighteen filled buttons at the eight-tab limit become tonal
  selectable rows with a sibling icon close control, and the selected state becomes a colour role
  that survives the dark projection — which the obvious `surfaceVariant`/`primaryContainer` pairing
  does not, since both map from values that are identical in `WeboraColors.DARK`
- [x] `UX-021` Compact inline trust shield — integrated chrome's full-width `Secure · domain` row
  becomes a compact browser-owned chip beside the site title, and the signal behind it stops being
  `origin.scheme == "https"`. `TransportSecurity` grows to four browser-observed states written only
  by `routeRendererEvent`, so `SECURE` is earned by a successful main-frame completion rather than by
  a URL — a certificate that was rejected no longer reports secure. The registrable domain stays
  *inside* the chip: issue #104's layout drops it, and with the hub showing no origin either that
  would leave a coloured glyph as the only signal contradicting a manifest-supplied title and logo,
  which is exactly what `ADR-006` exists to prevent. `ADR-006` is cited unchanged, not amended
- [ ] `DEMO-003` Bloom Flowers visual fidelity & protocol showcase — exercise the polished icon and
  action vocabulary in the live reference integration

`UX-005` depends on `UX-002`; `UX-007` builds on the already-complete `UX-006`; `DEMO-003` is the
final product-facing evidence after `UX-005`, `UX-007`, and `DEVX-003`. The detailed parallel order
and gates are in [`DEVELOPER_PLAN.md`](DEVELOPER_PLAN.md).

### Why `UX-005` is no longer descoped

`UX-005` was parked while the browser itself had no design system. That sequencing was correct, but
its original risk is now visible in real evidence: the Bloom Flowers demo — the product's headline
integrated surface — shows `⌂ ▦ ▣ ● ☎` placeholders and an undifferentiated quick-action affordance.
The SiteSkin manifest already supplies semantic icon names; trusted chrome must continue mapping
those names to **browser-owned** assets rather than accepting arbitrary site-provided icons. M7
revives the ticket once `UX-002` supplies that asset foundation.

## M8 — Native browser UX & mode continuity

Starts **after `DEMO-003`**. M7 makes the SiteSkin story deliberate; M8 makes ordinary browsing feel
like an equally intentional product rather than the mode between Home and an integration. Android's
system navigation remains OS-owned — gesture navigation may show only the gesture handle, while
three-button navigation may show Back/Home/Recents. Webora does not recreate those controls; it owns
browser navigation, tabs, history and the transition between regular and integrated chrome.

- [x] `BROWSE-006` Multi-tab browsing and session model — independent tab history/mode, create/close/
  switch, browser-owned tab switcher and bounded session restoration
- [x] `UX-011` Persistent browser-owned navigation shell — reuse the existing Back/Forward/Reload/
  Home/More dock across Home and regular browsing, add tabs, and respect Android navigation insets
- [x] `BROWSE-007` Recents, history and favourites — replace the current Home placeholders with local,
  privacy-preserving browser data and integrate it with clear-browsing-data behaviour
- [x] `UX-012` Mode-aware chrome handoff — make Home → regular → SiteSkin → regular transitions
  explicit, preserve browser-owned escape/security controls, and prevent SiteSkin chrome leakage
- [x] `BROWSE-008` Back-to-Home fallback — when the active tab has no WebView history, browser Back
  returns that tab to native Home; visible regular/SiteSkin Back and Android system/predictive Back
  share the same history → Home → platform-exit contract. Detailed scope: [`backlog/BROWSE-008.md`](backlog/BROWSE-008.md)
- [x] `BROWSE-009` Per-tab renderer ownership and clean failure recovery — a WebView callback used to
  resolve the *selected* tab at delivery time, so a late completion or error from a background
  renderer rewrote another tab's URL, identity and error state; an un-keyed `AndroidView` served
  every tab from one composition slot; and a cancelled TLS handshake published nothing, leaving an
  indefinite spinner. Events now carry an immutable owner id through a pure router, the host is keyed
  by `BrowserTab.id` with a real detach, and a main-frame handshake failure settles its own tab. The
  ticket's negative control also found that `BROWSE-004`'s `isForMainFrame` filter had no test at all
- [x] `BROWSE-010` Leaving Home again shows the previous page under a permanent spinner — a retained
  renderer's mount was gated on the `WebView` being new, which stopped meaning "needs the page" when
  `BROWSE-006` made renderers outlive their hosts. The load decision is now a pure function over the
  tab's committed target and the browser's own record of where its renderer is — written from
  requests *and* reports, never from a failure, and deliberately not from `WebView.getUrl()`. Its own
  review removed a synthetic completion the first implementation reported. Issue
  [#106](https://github.com/denrzv/webora/issues/106); the Back exposure it uncovered is `BROWSE-012`
- [x] `BROWSE-011` A browser-owned Refresh for integrated pages — reload was reachable in integrated
  mode only through a *site* action, so it appeared at a website's discretion. Now a browser-owned
  control row inside the expressive header, with one `refreshAction` owning the decision for all
  three docks. The issue's brand-row placement was disqualified by measurement: it starves the trust
  chip and zeroes the site title at 320 dp and default font scale. Issue
  [#116](https://github.com/denrzv/webora/issues/116); `CI-009` hosted acceptance is re-taken
- [~] `CI-007` Canonical regular-browsing evidence — implementation landed, but hosted runs #30 and
  #31 stop after frames 01–03 because the first-page SiteSkin Back control cannot return to Home;
  acceptance resumes after `BROWSE-008`
- [x] `CI-008` Hosted Back-prerequisite diagnostic — the canonical journey now requires the
  browser-owned integrated Back control to be displayed and enabled before clicking it, so a
  regression fails at its cause rather than at the later Home address-field wait
- [~] `DEMO-004` Browser-first reference walkthrough — implementation/documentation is present, but
  its final hosted-evidence acceptance remains blocked on the `CI-007` four-frame journey

`BROWSE-006` is the foundation for `UX-011`; `BROWSE-007` can proceed once the tab/session model is
stable. `UX-012` follows the persistent shell. Hosted evidence exposed `BROWSE-008` after the rest of
M8 had landed: fix the shared Back contract first, then rerun `CI-007` until two consecutive cold
hosted runs publish all four frames; that evidence closes `DEMO-004` and M8.

M8 implementation has reached the browser-first walkthrough, but **M8 acceptance is reopened**.
Runs #30 (`31874095039`) and #31 (`31879413875`) both proved the same product gap rather than an
emulator failure: frames 01–03 are captured, then `captureRegularBrowsingEvidence()` times out
because Home is not a WebView history entry and the first-page integrated Back affordance is disabled.
`BROWSE-008` owns the product fix; `CI-007` owns the confirming four-frame hosted evidence.

## M9 — Expressive SiteSkin experience

Starts after M8 acceptance closes. M9 is the visual/product step that makes the integrated experience
feel like the site and browser were designed together rather than like a recoloured Material shell
around a WebView. The visual direction is based on the Bloom Flowers reference sketches: a soft curved
integrated header, a branded floating browser dock, richer storefront content, and a real product
journey.

The trust model does **not** change with the richer presentation. Webora owns geometry, browser
navigation, security identity, touch targets and callbacks. Sites continue to provide only validated
SiteSkin data: bounded text, normalized colours, semantic icon names, typed actions and bounded brand
assets. M9 deliberately does not add arbitrary remote layouts, SVG/CSS, Compose definitions or a new
appearance field to the SiteSkin manifest.

- [x] `UX-013` Expressive SiteSkin chrome primitives & ownership model — define browser-owned curved/
  floating primitives, motion/inset rules, light/dark projections and negative controls for manifest
  influence. GitHub: #84.
- [x] `UX-014` Curved branded integrated top chrome — compact browser-owned domain/TLS identity inside
  a soft branded top surface with deterministic curved lower edge, preserving Back/tabs/overflow and
  cross-origin teardown. GitHub: #85.
- [x] `UX-015` Branded browser dock & SiteSkin navigation hub — replace the persistent site bottom
  navbar with a fixed browser-owned Back/Forward/brand-hub/Tabs/More dock; project validated site
  navigation and quick actions into a native hub. GitHub: #86.
- [x] `DEMO-005` Expressive Bloom Flowers integrated showcase — integrate the Android surfaces with
  `denrzv/bloom-flowers#3` (`BLOOM-001` storefront refresh) and `denrzv/bloom-flowers#4`
  (`BLOOM-002` Happy Days product journey). GitHub: #87.
- [ ] `CI-009` Expressive SiteSkin visual acceptance — expand the hosted journey/contact sheet to
  cover storefront, product detail, navigation hub and final regular-mode teardown; require two
  consecutive cold accepted runs. GitHub: #88.

Recommended dependency order:

```text
M8 accepted
    │
    ▼
 UX-013
   ├────────► UX-014 ───────┐
   └────────► UX-015 ───────┤
                            │
Bloom BLOOM-001 ────────────┤
Bloom BLOOM-002 ────────────┤
                            ▼
                         DEMO-005
                            │
                            ▼
                          CI-009
```

The Bloom website work can proceed in parallel with `UX-013..015` because it remains a normal
responsive website with no Webora-specific DOM or user-agent behavior. `DEMO-005` is the integration
owner; `CI-009` is the evidence owner.

M9 intentionally revises one M8 presentation choice without weakening its ownership contract: site
navigation no longer has to occupy the persistent bottom slot. In M9 the persistent integrated dock
remains browser-owned, while validated SiteSkin navigation/actions move into the branded native hub.
Android system navigation remains OS-owned.

A SiteSkin appearance-preset/spec ticket is deliberately deferred. Revisit bounded presentation
presets only after a second materially different integration demonstrates that one browser-owned
expressive style is insufficient.

## Descoped

Not abandoned — parked, with the reasoning kept in [`BACKLOG.md`](BACKLOG.md) so reviving any of
them does not start from nothing. `SCOPE-001` records the decisions.

- `DEMO-002` PixelPlay, Daily Journal, Example News — one demo is enough for now. The browser
  behaviour they would have demonstrated live (skin swap, skin drop on origin change) is implemented
  and unit-tested under `SKIN-004`; what lapses is the demonstration, not the capability.
- `PLAY-001` Compliance sweep · `PLAY-002` Release signing, R8 keeps, versioning ·
  `PLAY-003` Store listing, Data safety, internal testing track — distribution is an APK handed to
  friends, not a store listing. **`targetSdk 36` is not descoped**: it shipped in `FOUND-002` and is
  in the build today.

---

## Definition of done for the first demonstrable prototype

From concept §62. All must hold:

- app installs and launches; arbitrary HTTPS browsing works
- regular websites remain fully usable
- manifests are discovered, and invalid ones fail safely
- Bloom Flowers renders with its own branding
- a site without a manifest stays in regular mode
- validated SiteSkin navigation opens the correct routes
- an origin change deactivates SiteSkin mode — covered by `SKIN-004`'s tests rather than by a second
  demo origin, which is descoped with `DEMO-002`
- **no manifest can hide the domain or the TLS indicator** (`ADR-006`)
- core validation and navigation logic is covered by automated tests

M7 adds a visual-quality bar on top of that functional prototype: canonical evidence must be clean,
cheap to review, free of debug/OS contamination, and representative of the intended SiteSkin UX.
M8 adds the browser-usability bar: ordinary sites must expose a coherent browser-owned shell and the
handoff into and out of SiteSkin must remain predictable without relying on Android system controls.
M9 adds the expressive-integration bar: an integrated site should feel deliberately native-like while
browser ownership/security remains structurally obvious and ordinary non-SiteSkin browsing remains
unchanged.
