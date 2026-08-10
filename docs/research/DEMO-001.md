# DEMO-001: Research
Status: RESEARCH_READY

## Question
What must exist in `denrzv/bloom-flowers` for the published manifest to describe a site that is
actually served, on an origin where SiteSkin discovery can reach it — and which of the five pinned
copies of that manifest have to move together if the manifest itself turns out to be wrong.

Three sub-questions the plan cannot commit to a file list without:

1. **Route layout.** The manifest names `/catalog`, `/cart`, `/account` with no trailing slash and
   cannot be re-authored per host. Which static layout resolves those on every plausible host, and
   do the published `match` arrays still select the right item once the host's own redirect form is
   taken into account?
2. **Manifest correctness.** `home` declares no `match`. Is that a legal-but-wrong authoring choice
   this ticket fixes, or protocol behaviour the reference must live with?
3. **Origin.** Where is this deployed, given that `/.well-known/` is an origin-root path?

## Origins involved

| Origin | Role | Notes |
|---|---|---|
| `https://bloomflowers.webora.app` | The deployment. Serves the site, the manifest at `/.well-known/siteskin.json`, and the logo. | Does not exist yet. The name follows the `webora.app` subdomain layout in `DEVELOPMENT_PLAN.md` and matches the two entries already compiled into `SuggestedSite`. |
| `https://bloomflowers.example` | The **fixture** origin in `spec/fixtures/valid/bloom-flowers.expected.json`. | Reserved for documentation by RFC 2606 and correct where it is. It must not become the deployment origin, and the deployment origin must not leak into the fixture. |
| `https://denrzv.github.io` | Where a GitHub Pages *project* deploy would land, at `/bloom-flowers/`. | **Disqualified, structurally.** See below. |

**Asset origin is the deployment origin, and that is not a preference.** `NET-003` rechecks the
trusted configuration's `logoUrl` against the complete canonical HTTPS origin before fetching and
follows at most two exact-origin redirects. The manifest's `logoUrl` is `/assets/siteskin/logo.png`,
resolved against the serving origin by `UrlResolver`, so the logo is same-origin by construction.
There is no configuration in which a CDN-hosted logo would work, which is the point: the reference
integration cannot demonstrate a shortcut it does not have.

### Why a project-Pages URL cannot host this at all

`NET-001` requests `/.well-known/siteskin.json` at the origin root. On `denrzv.github.io` that path
belongs to whatever is deployed at the user-site root — a different repository, and in the general
case a different site owner. A project site at `denrzv.github.io/bloom-flowers/` can serve
`/bloom-flowers/.well-known/siteskin.json`, which no SiteSkin browser will ever request. The
manifest's own paths compound it: `internal_url: "/catalog"` resolves to
`https://denrzv.github.io/catalog`, outside the deployment entirely.

So this is not the `DEVELOPMENT_PLAN.md` argument about four demos sharing one origin arriving
early — it is stricter. That argument says a shared origin destroys the *skin-swap* demonstration,
which is `DEMO-002`'s concern. This one says a project-Pages path cannot serve a SiteSkin
integration at all, for one site, because discovery is origin-rooted. A custom domain (or a user/org
Pages site) is the only shape that works, and it needs a `CNAME` file in the repository plus a DNS
record outside it.

## Manifest-controlled surface

What Bloom Flowers gets by publishing this manifest, and nothing more:

| Surface | Manifest field | Bound |
|---|---|---|
| Top bar title / subtitle | `toolbar.title`, `toolbar.subtitle` | `SiteSkinLimits` length caps, single line |
| Palette | `branding.*Color` | Parsed by core, projected by `SiteSkinTheme.from`, corrected to 4.5:1 / 3:1 by the WCAG guard |
| Logo | `branding.logoUrl` | Same-origin, PNG/WebP with matching signature, ≤512 KiB, ≤1024 px/axis, ≤1,048,576 px, rendered into a clipped 40 dp slot regardless of intrinsics |
| Bottom navigation | `bottomNavigation[]` | Capped at 5 by `SiteSkinChromeModel`, labels re-bounded for the semantics tree |
| Quick actions | `quickActions[]` | Capped at 5 |
| Active item | `match[]` | Path only; query, fragment and authority cannot participate |
| Home destination | `site.homeUrl` | Origin-bound; falls back to the origin root |

The reference site deliberately uses one action type beyond `internal_url` — `phone` on the Call
quick action — because that is the case where a site owner most expects a permission and does not
get one. `ActionResolver` maps it to an inert `ResolvedAction`; the app dispatches `ACTION_DIAL`
behind browser-owned confirmation. `INTEGRATION.md` has to say that explicitly or the reference
teaches the wrong expectation by omission.

## Browser-owned remainder

Unchanged by this ticket, and the reference site must demonstrate rather than avoid it:

- **Registrable domain and TLS indicator** (`ADR-006`, `HARDEN-002`): always visible beside the
  branding, browser typography, dedicated semantics node, no manifest field can suppress or
  reorder them. Every screenshot in `INTEGRATION.md` shows `bloomflowers.webora.app`.
- **First-use consent** (`ADR-011`, `SKIN-004`): the chrome does not change until the user allows it
  for the complete canonical origin. The reference site cannot pre-authorize itself.
- **The logo slot is decorative** (`HARDEN-002`): 40 dp, clipped, cleared accessibility semantics.
- **Dispatch of every action** (`CORE-005`, `BROWSE-005`): external navigation and Android
  capabilities confirm through browser-owned UI first.
- **The global SiteSkin switch** (`PRIV-001`): turning it off returns the reference site to regular
  browsing immediately, without changing the committed page.

Nothing in this ticket adds a browser-owned contract, because nothing in it asks the browser for a
capability it does not already grant every origin.

## Route layout — the decision the manifest forces

Static hosts disagree about extensionless paths:

| Layout | `GET /catalog` on GitHub Pages | on Cloudflare Pages | on `python3 -m http.server` |
|---|---|---|---|
| `catalog.html` | serves it | serves it | **404** |
| `catalog/index.html` | 301 → `/catalog/` | 301 → `/catalog/` | 301 → `/catalog/` |

Directory layout is the only one that resolves everywhere, so it is the layout — and the redirect
form has to be checked against the published `match` arrays, because a redirect changes the URL
`NavMatcher` sees.

Traced through `NavMatcher.matches` by hand, then to be pinned by test:

| Item | `match` | `/catalog` | `/catalog/` | deep path |
|---|---|---|---|---|
| catalog | `["/catalog", "/catalog/**"]` | exact | `/catalog/**` → segments `["catalog",""]`, `**` absorbs the empty tail | `/catalog/roses` ✓ |
| cart | `["/cart/**"]` | `**` matches **zero** segments, so `/cart` matches | ✓ | ✓ |
| profile | `["/account/**"]` | ✓ | ✓ | ✓ |

The zero-segment case is the one that looks wrong and is not: `advancePath` seeds `next[0] =
reachable[0]` for `**` and then propagates forward, so a `**` at the end of a pattern is satisfied by
an already-complete path. `/cart/**` selecting `/cart` is therefore correct, and the published
manifest survives the redirect form on all three items without editing.

## The `home` finding

`bottomNavigation[0]` is:

```json
{ "id": "home", "label": "Home", "icon": "home",
  "action": { "type": "internal_url", "url": "/" } }
```

No `match`. `SecurityValidator` normalizes an absent `match` to an empty list
(`SiteSkinConfiguration.kt`, `NavigationItem.match: List<String>`), `NavMatcher` iterates
`item.match` and therefore never considers `home`, and `SPEC.md` §7.1 clause 4 forbids the browser
from falling back to the first item. On the landing page of the reference integration, the bottom
navigation shows **no** active item.

This is not a core defect. `match` is OPTIONAL by design and the browser behaves exactly as
specified. It is an authoring defect in the reference manifest, and the only reason it has survived
this long is that the corpus asks whether the manifest is *valid*, never whether it *describes its
site*. `OriginCorpusTest.theBloomFlowersManifestResolvesEndToEnd` walks every URL in the body and
asserts it resolves inside the origin — a test that would pass just as happily if `/catalog` were a
404.

`"match": ["/"]` fixes it: `^/([^/].*)?(?![\s\S])` in `$defs/matchPattern` accepts `/` (the optional
group is absent), and an exact literal beats every glob in `Candidate.precedes`, so `/` selects Home
and `/catalog` still selects Catalog.

**Cost of the change — five pinned copies, two repositories, two independent guards:**

| Copy | Guard that fails if it alone moves |
|---|---|
| `spec/fixtures/valid/bloom-flowers.json` | `SpecCorpusTest` SHA-256 assertion |
| `spec/fixtures/valid/bloom-flowers.expected.json` | corpus canonical-result comparison |
| `BLOOM_FLOWERS_SHA256` in `SpecCorpusTest.kt` | the same assertion, from the other side |
| `denrzv/bloom-flowers/.well-known/siteskin.json` | `manifest-guard.yml` → `sha256sum --check` |
| `denrzv/bloom-flowers/.well-known/siteskin.json.sha256` | the same check |

The design is deliberate — `manifest-guard.yml` documents why it is a checksum pinned in both
repositories rather than a cross-repo fetch — and it means a partial update is loud on whichever
side moved. The checksum must be **recomputed from the file**, never transcribed.

`SPEC.md` §6's illustrative example also shows `home` without `match`. It is an abridged two-item
excerpt, not a pinned copy, and it is the natural place to show that `match` is optional. Changing
the reference manifest does not oblige changing the spec example, but the excerpt is now the only
place in the repository that shows a matchless item, which is worth a sentence rather than a silent
divergence.

## Relevant code

| Path | Why it matters |
|---|---|
| `denrzv/bloom-flowers/.well-known/siteskin.json` | The served manifest. Byte-identical copy; one-way from the fixture. |
| `denrzv/bloom-flowers/.github/workflows/manifest-guard.yml` | Existing checksum + JSON guard. The route-conformance check belongs beside it, offline, in the same workflow. |
| `spec/fixtures/valid/bloom-flowers.json` / `.expected.json` | The source of the copy and the canonical result. |
| `siteskin-core/src/test/.../spec/SpecCorpusTest.kt` | `BLOOM_FLOWERS_SHA256`, the pin from the webora side. |
| `siteskin-core/src/test/.../origin/OriginCorpusTest.kt` | `theBloomFlowersManifestResolvesEndToEnd` — proves URLs resolve, not that pages exist. The gap this ticket closes on the other repository's side. |
| `siteskin-core/src/main/.../nav/NavMatcher.kt` | `**` zero-segment behaviour and `Candidate.precedes` decide every entry in the route table above. |
| `siteskin-core/src/main/.../model/SiteSkinConfiguration.kt` | `NavigationItem.match` defaults to empty — the mechanism behind the `home` finding. |
| `spec/siteskin-1.0.schema.json` | `$defs/matchPattern` accepts `/`; `navigationItem.required` is `id`/`label`/`action`. |
| `app/src/main/java/app/webora/browser/browser/SuggestedSite.kt` | Compiled browser-owned catalogue. Bloom is the only `.example` entry left. `isSafeSuggestion` requires HTTPS, a host, no user-info and no fragment. |
| `app/src/main/java/app/webora/browser/siteskin/BrandAsset.kt` | `MAX_BYTES` 512 KiB, `MAX_AXIS_PIXELS` 1024, `MAX_PIXELS` 1,048,576 — the budget the logo must fit, with the signature check that rules out a renamed file. |
| `app/src/main/res/values/strings.xml` | `suggested_bloom_name` / `_description` already read "Bloom Flowers" / "Fresh flowers delivered today" — the manifest's own toolbar strings. No change needed. |

## Prior art

- `ADR-006` browser-owned security chrome; `ADR-009` discovery never blocks rendering; `ADR-010`
  graceful failure; `ADR-011` first-use consent. All four are things `INTEGRATION.md` must explain
  from the site owner's side rather than restate from the browser's.
- `SPEC.md` §7.1 (glob grammar, active-item resolution), §8 (limits), §13 (the corpus is the
  contract).
- `SPEC-001` established the shared corpus and the two-repository checksum pin.
- `SPEC-003` gives the command `INTEGRATION.md` tells site owners to run, and its exit contract:
  exit 0 means a trusted configuration exists even with warnings or dropped items.
- `NET-003` sets the logo budget; `SKIN-002` sets the 40 dp slot that makes intrinsic size mostly
  irrelevant; `HARDEN-002` makes the slot decorative.
- `DEVELOPMENT_PLAN.md` "the reference integration" section: framework-free, one readable file,
  `INTEGRATION.md` as a deliverable rather than a README.

## Risks

- **A partial five-copy update.** → The plan puts the manifest change in one task per repository,
  with the checksum recomputed by `sha256sum` from the file, and requires both guards to be run.
- **Route conformance decaying into a hand-maintained list.** → The check must read the manifest
  JSON and the filesystem and compare them. A list of expected paths in a script is the same
  assertion the corpus already fails to make.
- **A host that serves `/catalog` differently from the one this was designed against.** → Directory
  layout resolves on every host examined; the `match` table above covers both the redirect target
  and the pre-redirect path, so a host that does not redirect is also covered.
- **The demo importing a dependency.** → An external font, icon set or analytics snippet in the most
  copied artifact in the project becomes a pattern. Nothing off-origin, enforced by review and
  stated as an NFR.
- **Reporting an unverifiable criterion as passing.** → `siteskin-lint` against the deployed origin
  cannot run before DNS exists. It stays a `workflow_dispatch` job and a blocked criterion, named as
  such in QA rather than quietly dropped.
- **Emulator unavailability being read as a test failure.** → `AGENTS.md` covers this: mockup screen
  3 is manual QA evidence, recorded as an environment limitation, not a gate.

## Open questions
Carried into `/plan` as explicit unknowns:

1. **The domain is not registered.** Everything naming `bloomflowers.webora.app` — `CNAME`, the
   catalogue entry, `INTEGRATION.md`, the lint command — is correct-on-paper until a DNS record
   exists. The plan must keep the origin in as few places as possible and mark criterion 8 blocked.
2. **Does the reference site get a fifth page?** The manifest's `menu` block is absent, so the
   SiteSkin menu shows only the browser-owned section. Adding one would exercise `menu[]`, which no
   demo currently does — deferred to `DEMO-002` unless it costs nothing, and not smuggled in here.
