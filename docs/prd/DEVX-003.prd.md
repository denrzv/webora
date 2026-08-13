# DEVX-003: Inspector isolation and canonical evidence mode
Status: PRD_READY

## Context / Problem

`DEVX-001` gave site owners the SiteSkin Integration Inspector: a debug-only panel showing a
rejection's diagnostics, the HTTP status of a refused response, and which of `NET-002`'s cache paths
served the navigation. It is genuinely useful and it is correctly excluded from release variants.

It is also **on screen in every canonical screenshot**, as a floating `SiteSkin inspector` pill. In
the M7 evidence frames it sits over the page in all three: over Home, over the consent dialog's
scrim, and over the Bloom Flowers integration. Webora's headline product evidence — the frames shown
to demonstrate what the browser looks like — reads as a developer tool.

Two distinct problems, and they want different arguments:

**1. It is presentation.** The demo frames are the artifact people judge the product by. A permanent
debug affordance in them says "internal build" louder than the integration says "this is what
SiteSkin does".

**2. It is an evidence-integrity hole, inherited from `CI-003`.** That ticket measures the page
region for drawn pixels and requires 1% differing from the modal colour. The inspector pill's pixels
fall inside that rectangle — roughly 0.84% of it on a Pixel 6 — alongside the quick action at a
similar size. Two chrome affordances together clear the threshold, so **a completely blank page could
still satisfy the rendered check on Webora's own UI**. Invisible today, because the page renders at
75%; real, and the reason `CI-003` recorded it here rather than adding a second exclusion.

`CI-003` deliberately deferred to this ticket instead of patching around the overlay, on the grounds
that deleting it from canonical composition closes the hole without a second exclusion list to
maintain. That bet is now due.

## Goals

- The canonical screenshots contain no inspector affordance, because the browser does not draw one
  there — not because the harness hid it.
- The inspector stays reachable in a debug build through a deliberate, discoverable affordance.
- With the overlay gone, a blank page in the integrated frame **fails** `CI-003`'s rendered check
  rather than passing on chrome.
- Release variants stay inspector-free, with the existing compiled-output assertion intact.

## Non-goals

- Changing what the inspector shows, how the trace is recorded, or `SiteSkinTraceSink`'s neutrality.
  `DEVX-001`'s contract is untouched.
- Removing the inspector, or making it harder to reach than a deliberate affordance warrants.
- Adding an exclusion to `CI-003`'s `chromeInsidePageRegion`. This ticket exists so that is not
  needed; if the approach here changes, that fallback is recorded in `docs/BACKLOG.md`.
- Any change to `ScreenEvidencePolicy`, `RenderedContentPolicy`, the readiness gate, or the artifact
  layout.
- The quick action, which is site-driven product UI and legitimately in frame.

## The mechanism this ticket must not choose

**A "screenshot mode" that hides the affordance while the harness is capturing.** It is the obvious
shortcut and it is disqualified on the same grounds `CI-002` refused a generic dismiss-whatever-is-in-
the-way loop: it makes the photograph differ from what a user sees. A frame is evidence precisely
because nothing arranged the screen for the camera. If the inspector is present in the product, it
belongs in the evidence; the fix is for it not to be present, not for it to duck.

This is stated in the PRD rather than left to the plan because it is the option most likely to look
attractive under time pressure.

## User stories

- As someone reviewing demo evidence, the frames show Webora and the integration, with no developer
  affordance competing for attention.
- As a site owner debugging my manifest against a debug build, I can still reach the inspector
  deliberately, without hunting.
- As a maintainer, `CI-003`'s rendered check now measures page pixels only, so a blank page fails it.
- As a release engineer, nothing about inspector absence in release variants changes.

## Acceptance criteria

1. In normal composition — every browser mode, debug included — no inspector affordance is drawn over
   the page, the chrome, or any dialog.
2. The inspector is reachable in a debug build in **no more than two deliberate interactions** from
   the browsing surface, through an affordance that is part of browser-owned UI.
3. The affordance and the panel are absent from release variants, and
   `assertInspectorAbsentFromReleaseVariants` still passes — including its second half, which fails
   if the release stub's class is missing rather than only if the panel's class is present.
4. No manifest value, page content, or website-controlled input can show, hide, label, or reach the
   inspector. A trusted `SiteSkinConfiguration` still reaches the panel as *displayed data*, exactly
   as `DEVX-001` bounded it; it never reaches the affordance.
5. `DEVX-001`'s trace neutrality is preserved: `SiteSkinTraceNeutralityTest` passes unchanged, and
   the discovery outcome is identical whether or not the inspector is ever opened.
6. After the change, the only non-page pixels inside `BROWSER_CONTENT_TAG` come from
   `SiteSkinQuickActions`, which `CI-003` already excludes. Verified by inspection of what composes
   into that region, and recorded so the claim can be re-checked rather than trusted.
7. `BrowserSurfaceConventionsTest` still passes over `src/main/java`, `src/debug/java` and
   `src/release/java`, with every root contributing — a debug-only screen is browser-owned UI.
8. A hosted screenshot run produces canonical frames with no inspector affordance, confirmed by a
   human opening `preview.png`. Recorded as instrumented evidence, never promoted to a gate claim.
9. `bash scripts/pre-commit-check.sh` passes.

## NFR

- **Security/privacy:** the inspector displays bounded untrusted text (`inspectorValue`, bounded by
  `SiteSkinLimits.MAX_SUBTITLE_LENGTH`) and that bound stays. The *affordance* must be browser-
  authored end to end — its label from `strings.xml`, its visibility from the variant source set,
  never from `BuildConfig.DEBUG`, which `debugRelease` sets true against the release stub.
- **Reliability/fallback:** removing the overlay must not change browsing, consent, activation or
  discovery behaviour in any variant. The inspector's absence is a UI decision, not a state change.
- **Performance:** no new work in composition. If the panel moves behind an affordance, the snapshot
  assembly must not become eager on a surface that previously did not assemble it.
- **Accessibility:** the new affordance is browser-owned UI and inherits `A11Y-001` — a 48 dp target
  through `WeboraButton`/`WeboraTextButton`, an accessible name from a string resource, and no string
  literal reaching `Text(`. `BrowserSurfaceConventionsTest` enforces all three.

## Risks

- **Hiding rather than removing.** Any mechanism where the affordance exists but is suppressed during
  capture is the failure mode described above. The plan must state where the affordance lives and be
  able to answer "would a user of a debug build see it in normal browsing?" with *no*.
- **Silently deleting the inspector.** The opposite failure. `DEVX-001` solved a real problem for site
  owners; making the panel unreachable would trade one ticket's value for another's.
- **Breaking the release-absence gate.** `assertInspectorAbsentFromReleaseVariants` asserts both the
  panel's absence *and* the stub's presence. Moving files between source sets is exactly the kind of
  change that can satisfy the first half while quietly voiding the second.
- **Widening what reaches the affordance.** The panel receives a trusted configuration by design; the
  affordance must receive nothing from the manifest, or a site gains a way to influence a developer
  surface.

## Open questions

For `/researcher`, before the plan commits to a mechanism:

1. Where does the affordance go? The browser menu already routes `BrowserMenuCommand.SETTINGS`, and
   `PrivacySettingsScreen` is a browser-owned surface reached in one interaction — a debug-only entry
   there would be two. Are there other browser-owned surfaces that would cost fewer?
2. Does moving the entry change when `rememberInspectorSnapshot` runs, and does that affect
   `DEVX-001`'s "the trace observes and never decides" guarantee or its neutrality test?
3. What exactly composes inside `BROWSER_CONTENT_TAG` today, and is the inspector's overlay a *child*
   of that box or a full-screen sibling whose pixels merely land inside it? The distinction decides
   whether criterion 6 is satisfied by removal alone.
