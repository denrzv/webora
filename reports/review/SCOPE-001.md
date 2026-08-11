# Review: SCOPE-001
Date: 2026-08-11
Status: RESOLVED

## Summary

A decision-recording ticket rather than an engineering one: three owner decisions had made four
artifacts assert things that were false, and one compiled value point at hosts that can never
resolve. The work is almost entirely prose, with one small code change.

The thing worth reviewing is what was *kept*. Descoping badly means either leaving dead work in the
plan as though it were pending, or deleting it and losing the reasoning. Both were avoided, and
three carve-outs the descoping would otherwise have swallowed are named explicitly.

No findings.

## Architecture

| Concern | Assessment |
|---|---|
| Blast radius | One production file (`SuggestedSite.kt`), one resource file, five documents, two workflow files, two deletions. No `:siteskin-core`, no spec, no corpus, no schema — verified by diff. |
| The origin move cost nothing | Because `SPEC-001` made the reference manifest origin-relative. All five pinned copies stay byte-identical and the fixture keeps `bloomflowers.example`. This ticket is the first real exercise of that property, and it held. |
| Hosting reasoning | `DEVELOPMENT_PLAN.md` now argues from the origin-root rule rather than from a domain, which is correct: that rule is what selected the hosting both times, and it survives the domain changing. |
| Descoping shape | Each parked item keeps its definition, a why-not-now and a what-would-revive-it. `DEMO-002`'s entry states the revival is impossible on current hosting — a single Pages user site is a single origin — so nobody plans it in optimistically. |
| Deferred ≠ refuted | The multi-origin security argument is preserved verbatim in a blockquote. It was never wrong; it is unaffordable right now. |

## Security

| Property | Assessment |
|---|---|
| Trust boundary | Untouched. `denrzv.github.io` is validated on the same path as any origin; discovery, consent, origin binding and the cache are unchanged. Catalogue membership still confers no trust. |
| Identity display | `github.io` is in the PSL PRIVATE section, so the new origin is its own registrable domain and the browser shows `denrzv.github.io` rather than merging GitHub Pages users. Already pinned by `PublicSuffixListTest`. |
| HTTPS | `ADR-001` satisfied by `*.github.io` with no configuration. |
| Impersonation controls | Explicitly carved out of the descoping. `ADR-006` and `HARDEN-002` were partly justified by Play's Deceptive Behavior policy; they are load-bearing security regardless of distribution, and the plan now says so in two places. |
| `CNAME` removal | A fix, not a regression. Pages honours a custom domain with no DNS behind it, so the reference integration was 301ing to an unreachable host — deployed and unreachable at every URL. |
| Debug APK as the deliverable | Accepted with eyes open. `DIST-001` ships a debuggable build carrying the `DEVX-001` inspector. That is appropriate for handing to friends and is recorded as a deliberate trade, not an oversight. It is not a build to distribute more widely. |

## Findings

None.

## Not findings

- **`SuggestedSite` now has a single entry, so the catalogue looks over-engineered.** Kept as-is:
  `SuggestedSite.create` validation, the resource-id keying and `isSafeSuggestion` are the
  `BROWSE-003` contract that keeps remote content out of this surface. Collapsing it to one string
  would trade a type-level guarantee for a saving of a few lines.
- **The Play risk table survives in `DEVELOPMENT_PLAN.md` under a descoped heading.** Deliberate.
  It encodes Policy 4.3 reasoning, the R8/kotlinx.serialization hazard and permission decisions that
  still describe the app accurately; the heading, not deletion, is what marks it inactive.
- **`bloomflowers.webora.app` still appears in `DEMO-001`'s PRD, research, plan and review.** Those
  are records of a decision made when `webora.app` was assumed available. Rewriting them would erase
  the revision this ticket exists to document. Only the QA report changed, and only by addendum.
- **`siteskin-lint.yml` remains `workflow_dispatch`-only.** Its blocker changed rather than cleared:
  it is no longer waiting for DNS, it is waiting on `denrzv/webora` being private, which fails the
  checkout. The comment now says the true reason — correcting a claim my `DEMO-001` review got
  wrong.
- **`denrzv.github.io` is a personal Pages root serving a fictional flower shop.** Accepted by the
  owner, with the prior contents preserved on `archive/colmar-academy` and restorable with one push.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `SuggestedSite.kt`, `strings.xml` | `:app:test`, `HomeModelsTest` | `HomeModelsTest` builds its own suggestion, so it is unaffected by the entry removal; the resource deletion would fail compilation if a reference survived |
| `BrowserSurfaceConventionsTest` | existing | Untouched — and its `webora.app.src` property was the ticket's main scripted-replace hazard, inspected rather than swept |
| Documentation | `bash scripts/pre-commit-check.sh` | Green; whitespace/EOF scanned across both repositories before pushing, after `DEMO-001`'s CI lesson |
| Live origin | manual | Manifest `200` at root, live bytes match `BLOOM_FLOWERS_SHA256`, redirects observed on real Pages, `siteskin-lint` exit 0 |

**No negative control applies.** The ticket removes no protection and adds no guard.

## Verdict

**RESOLVED.** No findings. The one behavioural change — a suggestion URL that now resolves — is
covered by existing tests, and the live origin is verified.
