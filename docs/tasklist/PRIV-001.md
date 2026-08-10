# PRIV-001: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/PRIV-001.prd.md`
- Plan: `docs/plan/PRIV-001.md`

## Tasks
- [x] TASK-1: Persist and enforce global/per-origin privacy controls
  - Modify: privacy preference/store, consent store, runtime/state, settings model, tests.
  - Acceptance: global preference defaults enabled and persists; disabling fails closed before
    prompt/activation and deactivates integrated mode; recognized decisions can be listed and one
    exact canonical origin removed.
  - Tests: `PrivacySettingsStoreTest`, `SiteConsentStoreTest`, `SiteSkinRuntimeTest`,
    `BrowserStateTest` including a global-off negative control.
  - Negative control: removing the `siteSkinEnabled` early return makes
    `global disable ignores even current allowed candidate` fail with Activate instead of Ignore.
  - Gate repair: restored `invalid/malformed-json.json` as intentionally malformed after baseline
    commit `378f1c0` made the negative fixture valid and broke three existing conformance tests.
- [x] TASK-2: Add confirmed browsing-data deletion and settings UI
  - Modify: browser/privacy composables, WebView controller, manifest cache/coordinator, strings,
    instrumentation compilation, tests.
  - Acceptance: browser menus open settings; clearing requires confirmation; complete deletion
    includes cookies, Web Storage, live-WebView cache/form state, manifest cache, and consent while
    preserving global/onboarding preferences; incomplete clearing is reported honestly.
  - Tests: `BrowsingDataCleanerTest`, `ManifestCacheTest`, Compose settings test; full gate.
  - Environment: instrumentation tests are compiled; runtime execution and screenshots require a
    connected Android device, which this managed-cloud checkout does not provide.
- [x] TASK-3: Record privacy and Data safety contract
  - Modify: `docs/privacy/DATA_SAFETY.md`, `CLAUDE.md`, `docs/ROADMAP.md`.
  - Acceptance: zero-default-telemetry and current data flows/dependencies are mapped; roadmap marks
    PRIV-001 complete; downstream review/QA/validation remain the next workflow phases.
  - Tests: documentation consistency checks and `bash scripts/pre-commit-check.sh`.
- [x] TASK-FIX-1: Keep consent settings and active state consistent
  - Source: `/review finding 1`
  - Acceptance: new decisions appear immediately; clearing consent deactivates integrated chrome.
  - Tests: existing state/store tests and pre-commit gate.
- [x] TASK-FIX-2: Report deletion adapter completion truthfully
  - Source: `/review finding 2`
  - Acceptance: no-cookies is success; thrown adapter failures are incomplete; all adapters run.
  - Tests: `BrowsingDataCleanerTest` and pre-commit gate.
