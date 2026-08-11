# SCOPE-001: Single-demo scope, GitHub Pages hosting, APK distribution
Status: PRD_READY

## Context / Problem
Three decisions by the project owner invalidate assumptions that are currently written into the
roadmap, the development plan, the backlog and one compiled browser value.

**`webora.app` is taken.** `DEVELOPMENT_PLAN.md` builds its entire hosting section on that domain —
a table of five subdomains, the argument for per-demo origins, and the claim that HTTPS comes free
with a custom domain. `DEMO-001` shipped a `CNAME` pinning `bloomflowers.webora.app`, and
`SuggestedSite` compiles three `*.webora.app` URLs into the browser. None of those hosts can ever
resolve. A separate consequence has already been observed in production: because Pages honours a
`CNAME` for a domain with no DNS record, the bloom-flowers project deployment 301s every request to
an unreachable host, so the site is deployed and unreachable at every URL.

The replacement is live and verified. `denrzv.github.io` — a GitHub Pages **user** site, which owns
its origin root — serves the reference integration today: the manifest is reachable at
`/.well-known/siteskin.json` with `application/json`, its bytes are identical to the pinned fixture,
every route resolves, and `siteskin-lint https://denrzv.github.io` exits 0. That was the last
non-device criterion `DEMO-001` had to leave blocked.

**One demo is enough.** `DEMO-002` planned three further origins — PixelPlay, Daily Journal and
Example News — to prove skin-swap and skin-drop transitions. The owner has decided Bloom Flowers
alone is sufficient for now. Keeping three unstarted demos in the roadmap misrepresents what the
project intends to build.

**Google Play is not the distribution target.** `M5` is three tickets of Play compliance work —
policy 4.3 evidence, release signing with R8 keeps, store listing, Data safety, an internal testing
track. The owner intends to hand friends an APK. Everything in `M5` is therefore work the project
is not doing, sized and sequenced as though it were next.

The risk in all three is the same: a plan that describes work nobody intends to do is worse than no
plan, because it is read as commitment. The opposite risk is erasure — deleting the Play tickets
loses the reasoning that produced them, and that reasoning (Policy 4.3, Deceptive Behavior,
targetSdk deadlines) becomes expensive to reconstruct if distribution changes again.

## Goals
1. Make `denrzv.github.io` the reference integration's hosting reality across every artifact that
   names an origin, and stop `bloomflowers.webora.app` appearing anywhere as though it worked.
2. Narrow the demo scope to Bloom Flowers alone, recording what `DEMO-002` was for and why it is
   not being built, rather than deleting it.
3. Replace the Play Store milestone with APK distribution sized for handing a build to friends, and
   preserve the Play analysis as descoped work with its reasoning intact.
4. Record `DEMO-001`'s newly unblocked criterion against real evidence, so the QA record reflects
   what is now verified rather than what was unverifiable at the time.
5. Leave the protocol, the browser's security behaviour and the reference site's content untouched.

## Non-goals
- Renaming `denrzv/webora` or buying a domain. Both are the owner's, later, and neither is needed
  for anything in this ticket.
- Reviving Play work in any form: no signing config, no keystore handling, no R8 keep verification,
  no store assets. Those stay descoped with their analysis.
- Building the other three demo sites, or removing the browser capability they would have exercised.
  `SKIN-004`'s origin-change deactivation is implemented and unit-tested; what lapses is only the
  live cross-origin demonstration.
- Any change to `:siteskin-core`, the spec, the schema, the diagnostics registry or the conformance
  corpus. The reference manifest is origin-relative and needs no edit to move origins — that
  property is why one file can be both fixture and served copy, and this ticket is its first real
  test.
- Rewriting `DEMO-001`'s PRD, research, plan or review to pretend the domain was always
  `denrzv.github.io`. Those record decisions made with the information available then; the QA record
  gains an addendum instead.

## User stories
- As the project owner, I read `ROADMAP.md` and see only work I intend to do.
- As the project owner, I hand a friend one link, they install an APK, and Bloom Flowers renders
  with its branded chrome.
- As a future contributor, I can find out why there is one demo instead of four, and why the app is
  not on Play, without asking anyone.
- As a developer, `SuggestedSite` offers a site that actually resolves, so the browser's home screen
  stops advertising a dead host.

## Acceptance criteria
1. No artifact presents `*.webora.app` as a working origin. The reference integration's origin is
   `https://denrzv.github.io` in `SuggestedSite`, `CLAUDE.md`, `DEVELOPMENT_PLAN.md`, the
   bloom-flowers `README.md` and its lint workflow. `DEMO-001`'s own PRD/research/plan/review keep
   their original text as a record of the decision made at the time.
2. `denrzv/bloom-flowers` no longer pins a custom domain: `CNAME` is removed, so its own Pages
   deployment cannot redirect to an unreachable host. Exactly one publisher serves the site.
3. `defaultSuggestedSites` contains Bloom Flowers alone, pointing at `https://denrzv.github.io/`,
   with the PixelPlay and Daily Journal entries and their unused string resources removed.
4. `ROADMAP.md` shows `DEMO-002` and `PLAY-001..003` under a descoped heading rather than as
   pending work, and `M5` is APK distribution.
5. `BACKLOG.md` and `DEVELOPMENT_PLAN.md` record, for each descoped item, what it was for and why it
   is not being built — enough that reviving it does not start from nothing.
6. A `DIST-001` backlog entry defines APK distribution: `:app:assembleDebug` published on a GitHub
   Release with install instructions, explicitly no signing config, no R8 keep verification and no
   store assets.
7. `DEVELOPMENT_PLAN.md`'s hosting section states the origin-root rule that makes a user Pages site
   work and a project Pages site impossible, and records that the multi-origin argument is deferred
   with `DEMO-002` rather than refuted.
8. `reports/qa/DEMO-001.md` gains an addendum recording scenario 23 as passed against
   `https://denrzv.github.io`, with the evidence: live bytes matching `BLOOM_FLOWERS_SHA256`, the
   observed `/catalog` → `/catalog/` redirect on real GitHub Pages, and `siteskin-lint` exit 0.
9. `spec/`, `:siteskin-core` and the reference site's pages, stylesheet and logo are unchanged.
10. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: no change to any trust boundary. The origin moves, and the browser treats
  `denrzv.github.io` exactly as it treated any other origin — discovery, validation, consent and
  origin binding are untouched. Being in `defaultSuggestedSites` still confers no trust. Worth
  noting that `github.io` is in the Public Suffix List's PRIVATE section, so `denrzv.github.io` is
  its own registrable domain and the browser's identity chrome displays it correctly rather than
  collapsing every GitHub Pages user into one displayed identity — a property `CORE-001` already
  tests for and this ticket now depends on.
- Reliability/fallback: unchanged. A dead suggestion URL was already handled by `BROWSE-004`'s error
  path; after this ticket the suggestion resolves.
- Performance: none affected.
- Accessibility: none affected. The removed catalogue entries take their string resources with them,
  which the `A11Y-001` conventions scan continues to enforce for what remains.

## Risks
- **Blind find-and-replace on `webora.app` breaks the build.** `webora.app.src` is a Gradle property
  name in `BrowserSurfaceConventionsTest` and `A11Y-001`'s plan — the same characters, unrelated to
  the domain. Every replacement must be inspected, not scripted.
- **Erasing rather than descoping.** The Play analysis encodes real constraints (Policy 4.3
  enforcement, Deceptive Behavior exposure, the targetSdk 36 deadline) that were expensive to work
  out. Losing them is a worse outcome than a slightly longer backlog.
- **Removing `CNAME` changes what bloom-flowers' own Pages serves.** With two publishers of one site
  the demo origin becomes ambiguous, and a project-path copy cannot host a SiteSkin integration at
  all — so the deployment path has to end up with exactly one publisher, not two.
- **The QA addendum could overstate.** `siteskin-lint` exit 0 proves the manifest is reachable and
  accepted from a real origin over real TLS. It does not prove on-device rendering, which stays
  blocked; the addendum must not quietly upgrade that.
- **`denrzv.github.io` is a stopgap presented as a home.** It cannot serve the four distinct origins
  `DEMO-002` would need, and it is the owner's personal Pages root. Artifacts must say so.

## Open questions
None. The APK deliverable is `:app:assembleDebug` on a GitHub Release, and descoped work is retained
with its reasoning — both chosen by the owner. Renaming `denrzv/webora` and acquiring a domain remain
the owner's, outside this ticket.
