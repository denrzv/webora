# QA Report: DEMO-004
Status: QA_PASSED

## Scope

The browser-first reference journey, its discoverability from install/screenshot documentation, the
three-authority explanation, mapping to existing canonical evidence, and workflow completeness. This
ticket changes documentation only; it does not add a runtime or hosted screenshot behavior.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Start from Home and browse ordinary HTTPS | walkthrough/source comparison with Home address resolution and CI-007 destination | PASS — visible address/Go route to `example.com` |
| 2 | Create a real local record | walkthrough comparison with regular More/Add favourite and `BrowsingRecordStore` | PASS — no seed data or manifest label |
| 3 | Maintain at least two tabs | walkthrough comparison with Tabs/New tab and `BrowserSession`/`TabSwitcher` | PASS — retained ordinary tab plus Bloom Home tab |
| 4 | Observe Recent or Favourite on Home | walkthrough comparison with BROWSE-007 Home sections | PASS — naturally created `example.com` record |
| 5 | Consent to Bloom SiteSkin | complete origin, ordinary-first discovery, Allow, integrated expected state | PASS |
| 6 | Browser escape/security survive integration | guide requires fixed Back and secure domain identity | PASS |
| 7 | Return to ordinary chrome | select retained example tab; require regular identity/shell and no Bloom bottom/action chrome | PASS by documented shipped seam |
| 8 | Navigation authorities are unambiguous | review of three-kind primer and Back clarification | PASS |
| 9 | Hosted evidence claims are exact | compare walkthrough table with `docs/SCREENSHOTS.md` and CI-007 report | PASS — four states mapped; tabs/records explicitly uncaptured |
| 10 | Install and screenshot docs expose the journey | relative links and terminology check | PASS |
| 11 | Documentation hygiene and complete repository gate | `git diff --check`; `bash scripts/pre-commit-check.sh` | PASS |
| 12 | Run installed walkthrough locally | connected device or `/dev/kvm` | NOT RUN — neither is available; repository policy forbids provisioning a software-only emulator |

## Acceptance mapping

| PRD criterion | Evidence |
|---|---|
| Complete ordinary → tabs/record → Bloom → ordinary journey | Four numbered walkthrough stages use visible production controls and naturally created state. |
| No hidden/developer/demo-only seam | Intro excludes the Inspector; steps use Home, More, Tabs, New tab, suggestion, consent and tab selection. |
| Three navigation owners and invariant browser controls | Dedicated primer identifies Android, Webora and SiteSkin authority and fixes security/escape ownership. |
| Honest four-frame map | Table maps frames 01–04 and immediately states tabs/records are interactive checks not captured pixels. |
| Discoverability and no second SiteSkin origin | Install and screenshot docs link the guide; only ordinary example.com and existing Bloom are used. |
| Pre-commit gate | PASS for both task checkpoints. |

## Edge cases

- invalid manifest → regular browser mode: PASS — guide names invalid Bloom as a safe regular-mode
  outcome and forbids forcing integration through the Inspector.
- origin change / redirect: PASS — example and Bloom are presented as distinct exact origins;
  browser-observed selected-tab state owns chrome rather than page/manifest claims.
- offline with cached manifest: PASS by unchanged behavior — the walkthrough makes no new cache
  promise; unavailable integration remains regular and can be retried when live.
- oversized or malformed payload: PASS by unchanged behavior — complete validation remains required
  and malformed input cannot reach the documented integrated state.
- accessibility (TalkBack, font scale): PASS for scope — steps use visible labelled controls and do
  not require gesture-only or developer-only interaction. Runtime TalkBack/font-scale inspection was
  not rerun for documentation-only work.
- fewer than two tab slots available: PASS — the session is bounded; close an existing tab before
  selecting New tab if already at the visible eight-tab limit.
- existing browsing records: PASS — guide says existing records do not invalidate the flow and uses
  a newly created Favourite to identify its result deterministically.
- consent already stored: PASS operationally — reset the per-site decision in Settings when the
  purpose is to demonstrate first-use consent; ordinary fallback/security behavior remains safe if
  the dialog does not recur.
- live `example.com` decoration changes: PASS — no page-authored title, prose, colour, or layout is
  used as a mode or identity verdict.
- Bloom outage: PASS fail-safe — guide requires regular fallback, not fixture injection or weakened
  consent/evidence.
- OS navigation mode differs: PASS — system gesture/three-button controls are explicitly outside the
  Webora walkthrough and must remain usable; the reference steps use labelled browser controls.

## Result
Status: QA_PASSED

Notes: All documentation and repository checks runnable in this managed-cloud checkout are green.
CI-007 already records the four-frame hosted evidence this guide maps. DEMO-004 does not claim a new
hosted or local device run; interactive execution remains an environment-limited rollout activity.
