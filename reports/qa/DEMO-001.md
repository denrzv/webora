# QA Report: DEMO-001
Status: QA_PASSED

## Scope

The Bloom Flowers reference integration across two repositories: the manifest's `home`/`match`
correction and its guard in `denrzv/webora`, and the site, logo, adoption guide, offline route check
and deployment configuration in `denrzv/bloom-flowers`.

Not in scope, and not claimed: on-device rendering. `AGENTS.md` rules out provisioning a
software-only Android Emulator in managed cloud without `/dev/kvm`, so every scenario below is
verified against the JVM gate, the real validator, a local HTTP server or Chromium — never against a
device. Scenarios that require one are listed as blocked rather than passed.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | The reference manifest selects the right tab for every route the site serves | `ReferenceIntegrationNavTest`, 8 routes through `NavMatcher` from the real fixture | **Pass** — `/`→home, `/catalog`,`/catalog/`,`/catalog/roses`→catalog, `/cart`,`/cart/`→cart, `/account`,`/account/orders`→profile |
| 2 | A path the manifest does not describe leaves no tab active | same test, `/about` `/catalogue` `/carts` `/accounts` | **Pass** — all null, so scenario 1 cannot pass by matching everything |
| 3 | The route table covers every navigation item in the fixture | same test, set comparison | **Pass** — a shrunken fixture cannot narrow coverage silently |
| 4 | Before the fix, `/` selected nothing | ran the test against the pre-fix fixture | **Pass (as the finding)** — `expected:<home> but was:<null>` |
| 5 | All five pinned manifest copies agree | `cmp` fixture↔served copy; `sha256sum`; compare against `BLOOM_FLOWERS_SHA256` | **Pass** — byte-identical, `ed0ca884…` in all three places |
| 6 | A one-sided manifest edit is caught from the webora side | reverted the fixture alone, ran `:siteskin-core:test` | **Pass** — 3 failures: `ReferenceIntegrationNavTest`, `SpecCorpusTest` (SHA), `SecurityConformanceTest` (canonical result) |
| 7 | A one-sided edit is caught from the constant's side | reverted `BLOOM_FLOWERS_SHA256` alone | **Pass** — `SpecCorpusTest.bloomFlowersFixtureMatchesThePublishedCopy` fails |
| 8 | A one-sided edit is caught in the demo repository | changed one word of the served copy | **Pass** — `sha256sum --check` FAILED; restored, OK |
| 9 | Every path the manifest names is served | `tools/check-routes.py` | **Pass** — 11 paths resolve |
| 10 | A renamed page fails the route check | hid `catalog/index.html` | **Pass** — fails 3 ways (action URL + both match patterns); restored, exit 0 |
| 11 | A logo that is not really a PNG is refused | prefixed the file with 7 junk bytes | **Pass** — signature failure, exit 1; restored, exit 0 |
| 12 | A manifest path that climbs out of the site is refused | purpose-built traversal manifest, post-`TASK-FIX-1` | **Pass** — both paths reported unserved; before the fix it reported OK (FINDING-1) |
| 13 | The directory layout resolves the manifest's paths | `python3 -m http.server`, `curl` each path | **Pass** — `/`,`/catalog/`,`/cart/`,`/account/`, logo, manifest → 200; `/catalog`,`/cart`,`/account` → 301 to the trailing-slash form, which the published `match` arrays already cover |
| 14 | The landing page is mockup screen 3 | Chromium screenshot at 1280 and at phone width | **Pass** — hero, Best Sellers grid, `#D94F8A` palette, Home/Catalog/Cart/Account, Call action |
| 15 | The site is usable with no SiteSkin at all | loaded all four pages in Chromium, which does not implement SiteSkin | **Pass** — header navigation, layout and content are entirely independent of the manifest |
| 16 | The site makes no off-origin request | grep over every `src`/`href` in the four pages | **Pass** — none; the only external reference is a hyperlink to the guide on GitHub |
| 17 | The logo is inside the `NET-003` decode budget | `check-routes.py` + file inspection | **Pass** — 512×512, 5,702 bytes: 1.1% of 512 KiB, 25% of the pixel budget |
| 18 | The logo is reproducible from its generator | regenerated and compared | **Pass** — byte-identical (`a0fc2a9b…`), so binary and script cannot drift |
| 19 | Both `INTEGRATION.md` JSON snippets are valid | ran through `SiteSkinValidator` itself | **Pass** — accepted, zero diagnostics |
| 20 | All three workflows are well-formed | YAML parse | **Pass** — `checksum`/`routes`, `deploy`, `lint` |
| 21 | No protocol surface changed | `git diff` against the merge base | **Pass** — `spec/diagnostics.json`, `spec/versions.json`, `siteskin-1.0.schema.json` untouched |
| 22 | The full gate is green | `bash scripts/pre-commit-check.sh` | **Pass** — gitleaks, shellcheck, core-without-SDK, unit tests, inspector-absence, detekt |
| 23 | `siteskin-lint` exits 0 against the deployed origin | — | **Blocked** — `bloomflowers.webora.app` has no DNS record. Command recorded: `./gradlew :siteskin-lint:run --args="https://bloomflowers.webora.app"`; the workflow that runs it is `workflow_dispatch` only so it cannot report green early |
| 24 | The app renders the skin on-device | — | **Blocked** — no emulator per `AGENTS.md`; see Edge cases |

## Edge cases

- **Invalid manifest → regular browser mode.** N/A — no change to any validation or activation path.
  The manifest edit is additive within `1.0` (an OPTIONAL `match` array), and `ADR-010`'s fallback is
  untouched. Verified indirectly: the whole corpus of invalid fixtures still passes.
- **Origin change / redirect.** Two distinct redirect concerns. *In the browser:* unchanged,
  `SKIN-004` owns it, and there is no second origin to swap to until `DEMO-002`. *On the host:* the
  site's `/catalog` → `/catalog/` redirect was the one real risk this ticket carried, since it
  changes the URL `NavMatcher` sees. Scenario 13 confirms the redirect exists and scenario 1
  confirms both spellings select the same tab.
- **Offline with cached manifest.** N/A — no change to `NET-002`. Worth noting that the manifest's
  cache key is `origin + schemaVersion`, and the edit changed neither, so a client holding a cached
  `1.0` entry revalidates by `ETag`/`Last-Modified` in the normal way. No migration, no stale-entry
  hazard.
- **Oversized or malformed payload.** N/A — no parser change. The published manifest is 1,336 bytes
  against a 131,072-byte limit and nests 4 levels against a limit of 64.
- **Accessibility (TalkBack, font scale).** The browser's own contract is untouched, so `A11Y-001`'s
  gate still covers it. For the reference *site*, which that gate does not scan because it is not
  browser-owned Compose: semantic landmarks and headings, a skip link, `aria-current="page"` on the
  active navigation link with an underline and weight change so state is not carried by colour
  alone, `aria-hidden` on decorative emoji, `alt=""` on the logo beside its own text, 48px tap
  targets, `:focus-visible` never removed, tables with `scope`d headers and captions, and a palette
  whose body text clears 4.5:1 (the stylesheet darkens white-on-`#D94F8A` from 3.86:1 rather than
  shipping it). Verified by reading and by rendering at phone width; not verified with a screen
  reader.
- **Untrusted text in a display surface.** N/A — nothing in this ticket displays manifest-derived
  text in a new place. The guide's JSON snippets are static repository content.

## Result

Status: QA_PASSED
Notes: 22 scenarios pass, 2 blocked, 0 failed. Both blocked scenarios are the same external
dependency — `bloomflowers.webora.app` is not registered, and no emulator is available in this
environment — and neither is blocked on code in either repository. They are the ticket's honest
remainder rather than an outstanding defect: every artifact that names the origin (`CNAME`, the
catalogue entry, `INTEGRATION.md`, the lint workflow) becomes correct the moment the DNS record
exists, with no further change. `FINDING-1` from `/review` was fixed in `TASK-FIX-1` and re-verified
(scenario 12).

---

## Validation (`/validate`)

| Gate | Result |
|---|---|
| PRD `PRD_READY` | ✅ |
| Research `RESEARCH_READY` | ✅ |
| Plan `PLAN_APPROVED` | ✅ |
| Tasklist `TASKLIST_READY` | ✅ |
| QA `QA_PASSED` | ✅ |
| Review `RESOLVED` | ✅ |
| `scripts/gate-workflow.sh` | ✅ `[GATE] OK for DEMO-001` |
| Every task ticked | ✅ 10 of 10 (TASK-1..8 plus TASK-FIX-1 and TASK-FIX-2); none deferred |
| `bash scripts/pre-commit-check.sh` | ✅ green — gitleaks, shellcheck, core-without-SDK, unit tests, inspector absence, detekt |
| `CLAUDE.md` updated | ✅ "Reference integration (DEMO-001)" — the five pinned copies, recompute-never-transcribe, origin-rooted discovery, the route-layout decision and the `**`-zero-segment consequence |
| Every commit pushed | ✅ both branches; working trees clean |
| **CI green on the branch** | ✅ Both PRs green. `denrzv/webora#43` — `guardrails`, `core`, `detekt`, `android` (`test`, `assertInspectorAbsentFromReleaseVariants`, `assembleDebug`, `lintDebug`) and `deps-osv`. `denrzv/bloom-flowers#1` — `checksum` and `routes`. The first run was **red**: see below. |

### Remaining external dependencies

Neither is blocked on code, and neither requires a further change to either repository:

1. **DNS for `bloomflowers.webora.app`**, plus enabling Pages with the custom domain. Unblocks QA
   scenario 23 (`siteskin-lint` exit 0 against the live origin) and makes `CNAME`, the catalogue
   entry and `README.md`'s live link correct.
2. **A device or emulator.** Unblocks scenario 24 — on-device rendering of mockup screen 3, the
   first-use consent dialog, and the tab-highlight transitions this ticket's `home`/`match` fix
   exists to make correct.

### The gate and CI are not the same check

The first CI run failed `guardrails` on `end-of-file-fixer` — a scripted append had left two trailing
newlines in `docs/tasklist/DEMO-001.md`. Fixed in `TASK-FIX-2`; green on the re-run.

`scripts/pre-commit-check.sh` could not have caught it, and that is the durable finding. The script
invokes gitleaks, shellcheck, the Gradle tasks and detekt **directly**; CI's `guardrails` job runs
`pre-commit run --all-files`, which is what owns `end-of-file-fixer`, `trailing-whitespace`,
`check-yaml` and `check-json`. The two gates therefore check different sets, and a green local run
does not imply a green `guardrails`.

That contradicts the script's own header, which argues a gate you cannot tell apart from a passing
build is not a gate. Closing it means changing the shared gate script, which is outside this
ticket's scope — recorded here and in the tasklist for a ticket of its own.

Every tracked text file in both repositories was scanned for the same class of violation before the
fix was pushed; the tasklist was the only one this ticket introduced.
