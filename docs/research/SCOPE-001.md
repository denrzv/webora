# SCOPE-001: Research
Status: RESEARCH_READY

## Question
Which artifacts assert something the owner's three decisions have made false, and which of them are
*records* that should keep saying what they said?

## Origins involved

| Origin | Status | Notes |
|---|---|---|
| `https://denrzv.github.io` | **Live and verified.** Serves the reference integration from the origin root. | A GitHub Pages *user* site, so it owns its root. `siteskin-lint` exits 0 against it; the served manifest is byte-identical to `spec/fixtures/valid/bloom-flowers.json`. |
| `https://bloomflowers.webora.app` | **Dead.** `webora.app` is registered by someone else. | Currently pinned by `CNAME` in bloom-flowers, which makes that repo's own Pages deployment 301 every request to a host with no DNS. Deployed and unreachable. |
| `https://pixelplay.webora.app`, `https://journal.webora.app` | Dead, and now also unwanted — `DEMO-002` is descoped. | Compiled into `SuggestedSite`. |
| `https://bloomflowers.example` | Unchanged and correct. | The conformance fixture's origin. RFC 2606 reserved; it must not follow the deployment. |

**The manifest needed no edit to change origins**, which is worth recording as the first real test of
a property `SPEC-001` designed for: every URL in the reference manifest is origin-relative, so one
byte-identical file serves as both the conformance fixture bound to `bloomflowers.example` and the
document served from `denrzv.github.io`. Had any URL been absolute, this ticket would have had to
move all five pinned copies.

**`github.io` is in the Public Suffix List's PRIVATE section**, so `denrzv.github.io` is its own
registrable domain. The browser's identity chrome therefore displays `denrzv.github.io` rather than
collapsing every GitHub Pages user into `github.io` — exactly the case `CORE-001` loads both PSL
sections to handle, and `PublicSuffixListTest` already pins it. The new hosting depends on a
property the project tested two milestones ago.

## What changed, and what each artifact currently claims

| Artifact | Current claim | True after this ticket |
|---|---|---|
| `app/.../browser/SuggestedSite.kt` | Three `*.webora.app` entries | One entry, `https://denrzv.github.io/` |
| `app/src/main/res/values/strings.xml` | Five suggestion strings | Two; the PixelPlay and Journal pairs are unreferenced |
| `docs/ROADMAP.md` | `DEMO-002` pending; `M5` is three Play tickets | Both descoped; `M5` is APK distribution |
| `docs/BACKLOG.md` | Same, with acceptance criteria | Descoped section with reasons; new `DIST-001` |
| `docs/DEVELOPMENT_PLAN.md` | A five-subdomain `webora.app` table; "you are buying a domain"; a Play compliance section | GitHub Pages reality; Play analysis retained as descoped |
| `CLAUDE.md` (DEMO-001 note) | "Hence `CNAME` and `bloomflowers.webora.app`" | The origin-root rule holds; the origin is now the user Pages site |
| `bloom-flowers/CNAME` | Pins a dead host | Removed |
| `bloom-flowers/README.md`, `siteskin-lint.yml` | Live site is `bloomflowers.webora.app` | `denrzv.github.io` |
| `reports/qa/DEMO-001.md` | Scenario 23 blocked | Addendum: passed, with evidence |

**`INTEGRATION.md` needs no origin change.** It teaches with `your-site.example` placeholders and
refers to the custom domain only through a relative link to `CNAME`. That link dies with the file,
so the sentence around it needs adjusting — but the guide's substance is origin-independent, which
was the right instinct when it was written.

## What must keep saying what it says

`DEMO-001`'s PRD, research, plan and review argued for `bloomflowers.webora.app` from
`DEVELOPMENT_PLAN.md`'s subdomain layout and from two `SuggestedSite` entries that already used it.
That reasoning was correct given what was known. Rewriting those files to read as though
`denrzv.github.io` were always the plan would destroy the record of a decision and its revision —
and this repository's whole convention is to keep the reasoning, not just the outcome.

The QA report is the exception, and only by addition: it records *verification state*, scenario 23
is now verifiable, and the honest move is an addendum dated to the new evidence rather than a silent
flip of a table cell.

## The two-publisher problem

Removing `CNAME` does not simply neutralise bloom-flowers' `pages.yml` — it changes what that
workflow publishes. Without a custom domain, that deployment lands at
`https://denrzv.github.io/bloom-flowers/`: reachable, and structurally incapable of hosting a
SiteSkin integration, because discovery requests `/.well-known/siteskin.json` at the origin root and
the manifest's paths are origin-absolute.

Two publishers of one site, one of which cannot work, is worse than one. The deployment belongs to
`denrzv/denrzv.github.io`, whose workflow checks out bloom-flowers at deploy time and strips the
`CNAME` — so `pages.yml` in bloom-flowers should go, with the reason recorded where someone would
look for it.

## Descoping: what the retained reasoning is worth

`DEMO-002` is not merely three more sites. It is the only live demonstration of two transitions —
skin *swap* (Bloom → PixelPlay) and skin *drop* (Bloom → News) — which are concept §46 steps 14–15
and `SKIN-004`'s acceptance criteria. The browser behaviour is implemented and unit-tested; what
lapses is the demonstration, not the capability. That distinction is the thing to record.

The Play analysis encodes three constraints that were expensive to establish: Policy 4.3 minimum
functionality and why a general-purpose browser clears it, Deceptive Behavior exposure as the reason
`ADR-006` is suspension-grade rather than cosmetic, and the targetSdk 36 deadline that put
`compileSdk`/`targetSdk` into `FOUND-002`'s first commit rather than a cleanup ticket. **The third
already shipped and must not be descoped with the rest** — targetSdk 36 is in the build today.

## Relevant code

| Path | Why it matters |
|---|---|
| `app/src/main/java/app/webora/browser/browser/SuggestedSite.kt` | The only compiled origin. `isSafeSuggestion` constrains shape; entries are resource-id keyed. |
| `app/src/main/res/values/strings.xml` | `suggested_pixelplay_*` and `suggested_journal_*` become unreferenced. |
| `app/src/test/.../browser/HomeModelsTest.kt` | Uses its own literals, not the catalogue — unaffected by the entry removal. |
| `app/src/test/.../browser/BrowserSurfaceConventionsTest.kt` | **Contains `webora.app.src`** — a Gradle property name, not the domain. A scripted replace corrupts the gate. |
| `docs/plan/A11Y-001.md` | Same `webora.app.src` string, same hazard. |
| `siteskin-core/.../origin/PublicSuffixListTest.kt` | Already pins `a.github.io` ≠ `b.github.io`; the new origin's identity display rests on it. |

## Prior art
- `DEMO-001` research established the origin-root rule that disqualifies project Pages sites; this
  ticket is that rule applied a second time, to choose the replacement.
- `CORE-001` loaded both PSL sections specifically so `denrzv.github.io` reads as its own registrable
  domain.
- `SPEC-001` made the reference manifest origin-relative, which is why moving origins costs nothing.
- `ADR-001` requires HTTPS for SiteSkin mode; GitHub Pages provides it on `*.github.io` with no
  configuration, which is why the stopgap works at all.

## Risks
- **`webora.app.src` collides textually with the dead domain.** → Inspect every match; never script
  the replacement.
- **Descoping reads as abandonment.** → Each descoped item records what it was for and what would
  revive it, and `SKIN-004`'s tested behaviour is stated as unaffected.
- **The QA addendum overstating what was proven.** → It covers manifest reachability, byte identity
  and validator acceptance from a real origin. On-device rendering stays blocked and must be
  restated as such in the same addendum.
- **Presenting a personal Pages root as permanent hosting.** → Artifacts must name it a stopgap, note
  it cannot supply the distinct origins `DEMO-002` would need, and note the archived prior contents.

## Open questions
None blocking. Both forks — debug APK on a GitHub Release, and retain-with-reasons over delete —
were decided by the owner. Renaming `denrzv/webora` and acquiring a domain stay outside this ticket.
