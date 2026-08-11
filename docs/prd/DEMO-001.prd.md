# DEMO-001: Bloom Flowers reference integration
Status: PRD_READY

## Context / Problem
Every milestone from `SPEC-001` to `DEVX-001` was verified against fixtures, MockWebServer and unit
tests. Not one of them was verified against a website. `denrzv/bloom-flowers` currently holds three
files — the manifest, its checksum, and the guard workflow that pins the two together — and no site
at all. There is nothing to serve, nothing to navigate, and nothing for a site owner to read.

That gap hides two different kinds of unknown.

**The protocol has never been exercised against a document tree.** The manifest is a conformance
fixture: `SpecCorpusTest` proves it validates and `OriginCorpusTest` proves every URL in it resolves
inside its origin. Neither test knows whether the paths those URLs resolve *to* exist. `/catalog`,
`/cart`, `/account` and `/assets/siteskin/logo.png` are asserted to be well-formed and same-origin,
and asserted by nothing to be reachable. A reference integration whose Catalog tab 404s would pass
the entire corpus.

The first real consequence is already visible in the published manifest. Its `home` entry declares
no `match` array. `match` is OPTIONAL in `SPEC.md` §7.1 and `NavigationItem.match` defaults to
empty, so `NavMatcher` can never select `home` — on the site's own landing page, the reference
integration's bottom navigation would show no active item at all. `SPEC.md` §7.1 clause 4 forbids
the browser from papering over that by selecting the first item. Nothing in the corpus can catch
this, because "the manifest is valid" and "the manifest describes the site correctly" are different
questions and only the first one has ever been asked.

**The protocol has never been read by a site owner.** `DEVELOPMENT_PLAN.md` states the deliverable
plainly: `INTEGRATION.md` is the artifact that proves SiteSkin is adoptable by someone who is not
us. There is no such document. `SPEC.md` is 700 lines of normative text written for an implementer
of a browser, not for someone who owns a flower shop and has fifteen minutes.

Hosting is the third unknown, and it is a design constraint rather than an operational detail.
SiteSkin discovery requests `/.well-known/siteskin.json` at the **origin root** (`NET-001`), and the
manifest's paths are origin-absolute. A GitHub Pages *project* site is served from
`denrzv.github.io/bloom-flowers/`, where the well-known path belongs to a different site owner
entirely and every manifest path points outside the deployment. The reference integration therefore
cannot be demonstrated on a project-Pages URL at all — not as a matter of taste, but because the
protocol's discovery rule makes it unreachable. This is the same origin-separation argument
`DEVELOPMENT_PLAN.md` makes for the demo fleet, arriving one ticket earlier than expected.

`SuggestedSite` already carries `pixelplay.webora.app` and `journal.webora.app` as compiled
browser-owned catalogue entries, while Bloom Flowers alone still points at
`https://bloomflowers.example/` — a reserved-for-documentation name that will never resolve. The
browser's own home screen currently suggests a site that cannot exist.

## Goals
1. Build `denrzv/bloom-flowers` into a real, self-contained, framework-free website that serves every
   path its published manifest names, so that the reference integration is a site rather than a
   fixture with a checksum.
2. Make the manifest describe the site accurately, including an active-state contract that works on
   the landing page, and carry any manifest change through every place the byte-identical copy is
   pinned.
3. Write `INTEGRATION.md` as the adoption document — what a site owner adds, in what order, what the
   browser does with it, and what it refuses to do — with the reference site as the worked example.
4. Prove routes and manifest agree mechanically, in CI, offline: every `internal_url`, every
   `match` pattern and the logo must resolve to something the deployment actually serves.
5. Make the deployment real: a custom-domain origin root, valid TLS, an automated Pages deploy, and
   a browser catalogue entry that points at the origin the site is actually served from.
6. Keep the logo inside the `NET-003` decode budget as a published property of the file, not an
   assumption about it.

## Non-goals
- The other three demo origins. PixelPlay, Daily Journal and Example News are `DEMO-002`; this ticket
  ships one origin and the skin-swap and skin-drop transitions stay unproven until there is a second.
- Any change to `:siteskin-core`, `:siteskin-lint` or the SiteSkin runtime in `:app`. If the
  reference integration cannot be expressed within the shipped protocol, that is a finding for a new
  ticket, not a licence to widen the protocol from a demo.
- New protocol surface: no new action type, icon name, manifest field or diagnostic code.
- A build step, framework, bundler or package manager in `denrzv/bloom-flowers`. The value of the
  reference is that the diff between "a responsive site" and "a SiteSkin-integrated site" is one
  readable file; a toolchain destroys that.
- Server-side behaviour. Cart and account are static demonstrations of navigation and branding, not
  a commerce backend. Nothing collects input, stores data or issues a network request off-origin.
- Registering the domain or configuring DNS. Those are account operations outside a repository; this
  ticket ships everything that becomes correct the moment the record exists, and says so explicitly.
- Emulator screenshots as a gate. Per `AGENTS.md`, runtime instrumentation is unavailable in managed
  cloud without `/dev/kvm`; on-device rendering of mockup screen 3 is recorded as manual QA evidence.

## User stories
- As a site owner, I read `INTEGRATION.md`, copy one JSON file to `/.well-known/siteskin.json`,
  publish it, and get native chrome — without reading `SPEC.md`.
- As a site owner, I run `siteskin-lint https://my.site` and get exit 0, having been told in
  `INTEGRATION.md` exactly what a non-zero exit means and which of my fields caused it.
- As a Webora developer, I open the reference site in the browser and see mockup screen 3: the pink
  top bar beside the domain and TLS indicator, Home/Catalog/Cart/Profile in the bottom bar, and the
  Call quick action.
- As a Webora developer, I tap Catalog and see the Catalog tab become active, tap Home and see Home
  become active, and land on a deep path under `/catalog/` and still see Catalog active.
- As a reviewer, I can see from CI alone — with no deployment and no network — that every path the
  manifest names is a file this repository serves.
- As a security reviewer, I confirm the reference integration demonstrates the trust boundary rather
  than tiptoeing around it: the domain and TLS indicator are visible in every screenshot, and the
  site asks for nothing the protocol does not already grant every other site.

## Acceptance criteria
1. `denrzv/bloom-flowers` serves a framework-free static site at `/`, `/catalog`, `/cart` and
   `/account`, each page carrying the Bloom Flowers branding, shared stylesheet and cross-links, and
   `/` matching mockup screen 3 (hero, Best Sellers grid, `#D94F8A` palette).
2. `/assets/siteskin/logo.png` exists, is a valid PNG whose bytes begin with the PNG signature, is
   512×512, and is under 64 KB — inside `NET-003`'s 512 KiB, 1024-per-axis and 1,048,576-pixel
   decode budget with margin.
3. Every URL in the published manifest resolves to content this repository serves, and every
   `match` pattern selects the page whose route it describes. A CI job asserts both offline, from
   the manifest file rather than from a hand-written list, and fails if a page is renamed or removed.
4. The manifest's `home` entry carries a `match` array, so `NavMatcher` selects Home on the landing
   page. The change is applied to `spec/fixtures/valid/bloom-flowers.json`, its `.expected.json`,
   the `BLOOM_FLOWERS_SHA256` constant in `SpecCorpusTest`, the served copy in
   `denrzv/bloom-flowers` and that repository's `.sha256` — and the checksum guard in both
   repositories passes afterwards, proving the five did not drift.
5. `INTEGRATION.md` exists in `denrzv/bloom-flowers` and covers, with the reference site as the
   worked example: where the manifest goes, the minimum viable manifest, each optional block and
   what it changes on screen, active-state matching, how to validate with `siteskin-lint`, what the
   browser will refuse (non-HTTPS, cross-origin assets, unknown action types, unknown major version)
   and what it never grants (Android permissions, chrome that hides the domain or TLS state).
6. A GitHub Pages deployment workflow publishes the repository root to the custom-domain origin, and
   a `CNAME` file pins that origin, so the site is served from an origin root where
   `/.well-known/siteskin.json` is reachable.
7. `defaultSuggestedSites` points Bloom Flowers at the origin the site is deployed to, consistent
   with the existing `pixelplay.webora.app` and `journal.webora.app` entries. The `.example`
   placeholder remains only in `spec/fixtures/`, where a reserved documentation name is correct.
8. Running `siteskin-lint` against the deployed origin exits 0. Until DNS exists this is recorded as
   a blocked criterion with the exact command, not as a passing one; the CI job that runs it is
   manually triggered so it cannot report green before there is anything to check.
9. No new protocol surface: `spec/diagnostics.json`, `spec/versions.json`, the JSON Schema, the
   action allow-list and the icon allow-list are unchanged.
10. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: the reference site is a demonstration of the trust boundary and must not depend
  on trust it has not been granted. No third-party origin, no analytics, no external font or CDN, no
  cookie, no form submission, no service worker. The site publishes exactly what any site publishes
  — a manifest — and receives exactly what the protocol grants: colours, a title, a bounded logo
  slot, allow-listed actions. `INTEGRATION.md` states what a manifest cannot do as plainly as what
  it can, because a document that lists only capabilities teaches site owners to expect more.
- Reliability/fallback: the site must remain fully usable in a browser that has never heard of
  SiteSkin. Its own in-page navigation, layout and content do not depend on the manifest being
  fetched, parsed or accepted; the manifest adds native chrome and removes nothing.
- Performance: static documents with no build step, no runtime dependency and no blocking request.
  The logo stays under 64 KB so a cold discovery decodes well inside the `NET-003` budget.
- Accessibility: the reference site is a public web page and is authored to the same standard the
  browser holds itself to — semantic landmarks, a skip link, focus-visible affordances, labelled
  controls, 4.5:1 text contrast in the published palette, and no meaning carried by colour alone.

## Risks
- Changing the manifest breaks five pinned copies at once, and the guard in each repository fails
  only for its own side. The five updates must land in one commit per repository with the checksum
  recomputed from the file rather than transcribed, or one repository ships a manifest the other
  repository's tests reject.
- Route conformance drifts silently the moment it is a hand-maintained list. The CI check must read
  the manifest and the filesystem and compare them, so that renaming a page fails the build rather
  than the deployment.
- Static hosts disagree about extensionless URLs: some serve `catalog.html` for `/catalog`, others
  only `catalog/index.html` for `/catalog/`. The manifest names `/catalog` with no trailing slash
  and cannot be edited per host, so the layout must be the one that resolves on any static server,
  and the `match` patterns must cover the redirect form the host actually serves.
- The origin does not exist yet. Every artifact that names it — `CNAME`, the catalogue entry,
  `INTEGRATION.md`, the lint command — is correct-on-paper and unverifiable today. The ticket must
  mark that boundary explicitly rather than let an unrunnable check be reported as passing.
- A demo is the easiest place for a security exception to enter: an external font, a hosted icon
  set, an analytics snippet added "just for the demo". The reference integration is the artifact
  most likely to be copied verbatim, so anything it does becomes a pattern.

## Open questions
None blocking. The deployment origin is taken as `bloomflowers.webora.app`, following the
`webora.app` subdomain layout in `DEVELOPMENT_PLAN.md` and the two catalogue entries already
compiled into `SuggestedSite`. Registering the domain and pointing the DNS record remain account
operations outside this repository; criterion 8 stays blocked until they are done.
