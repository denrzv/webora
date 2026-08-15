# DEMO-004: Browser-first reference walkthrough
Status: PRD_READY

## Context / Problem

Webora now has a persistent browser shell, isolated tabs, local recents and favourites, deterministic
regular/SiteSkin handoff, and canonical evidence for both modes. Those capabilities are documented
separately by their implementation tickets, while the install guide still starts its product tour at
Bloom Flowers. A first-time reviewer can therefore mistake SiteSkin for the whole product rather than
an optional enhancement inside a complete browser.

## Goals
- Publish one concise, reproducible browser-first journey from Home through ordinary browsing,
  tabs/local browsing data, Bloom Flowers consent/integration, and return to regular chrome.
- Explain the separate ownership of Android system navigation, Webora browser navigation, and
  manifest-bounded SiteSkin navigation.
- Connect each observable journey state to the existing four-frame hosted evidence without claiming
  that the evidence automates steps it does not capture.
- Make the journey discoverable from install and screenshot documentation.

## Non-goals
- Adding product UI, demo-only navigation, screenshot staging, or another browser implementation.
- Expanding the four-frame canonical journey to capture the tab switcher, recents, or favourites.
- Adding another SiteSkin origin or reviving `DEMO-002`.
- Changing manifest trust, consent, tab/session, browsing-record, or evidence policy.

## User stories
- As a first-time reviewer, I can demonstrate ordinary and SiteSkin browsing using only visible,
  user-facing controls.
- As a reviewer of screenshots, I can tell which parts of the walkthrough the canonical frames prove
  and which interactive steps I must exercise on an installed build.
- As a site owner, I understand that SiteSkin may request bounded site navigation but cannot replace
  browser security, escape controls, or Android system navigation.

## Acceptance criteria
1. A documented journey covers Home/new tab, ordinary HTTPS browsing, two-tab switching, a real
   Recent or Favourite entry, Bloom Flowers consent/integration, and deterministic return to the
   ordinary tab and regular chrome.
2. Every step uses the shipped M8 shell and visible user controls; no developer-only control, hidden
   gesture, seeded state, or demo-only path is required.
3. Documentation distinguishes Android system, Webora browser, and SiteSkin site navigation and
   states that the manifest cannot suppress browser-owned security identity or escape.
4. The walkthrough maps the existing Home, consent, integrated, and ordinary canonical frames
   honestly, including the fact that tabs and local browsing records require interactive inspection.
5. Install, screenshot, and roadmap documentation link to or accurately summarize the browser-first
   walkthrough while `DEMO-002` remains descoped.
6. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: use only browser-observed origins and local records; never describe manifest or
  page labels as trusted browser state.
- Reliability/fallback: choose the reserved `https://example.com` ordinary destination already used
  by canonical evidence and explain that unavailable/invalid manifests remain regular browsing.
- Performance: documentation-only; no runtime, build, or hosted-job cost.
- Accessibility: the journey uses labelled visible controls and does not require gesture-only or
  developer-only interaction.

## Risks
- Documentation could overstate the hosted frames by implying they show tabs/recents; map evidence
  per frame and label interactive-only observations explicitly.
- Browser and site navigation can look similar in a screenshot; explain ownership and the fixed
  security/escape controls before the steps.
- Live Bloom availability can fail; preserve ordinary-browser fallback as the expected safe result,
  not a reason to add fixture injection or weaken evidence.

## Open questions
None. The backlog fixes the journey, and CI-007 already fixes the ordinary destination and evidence
inventory.
