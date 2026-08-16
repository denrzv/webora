# QA Report: DEMO-005
Status: QA_PASSED

## Scope

Validated the M9 live-journey contract, public Bloom upstream state/source alignment, same-origin
product/history checkpoints, trusted hub vocabulary, regular-origin teardown, instrumentation
compilation, and repository gate for `5e632f3..dda8d55`.

## Acceptance criteria evidence

| Criterion | Evidence | Result |
|---|---|---|
| 1. Upstreams complete and product/manifest testable | Webora UX-013..015 complete; GitHub API reports Bloom #3/#4 closed; public source contains `catalog/happy-days/index.html`; manifest bytes equal fixture | PASS |
| 2. Expressive checkpoints replace retired navigation | JVM source contract requires header/dock and rejects legacy wait; instrumentation compiles | PASS |
| 3. Real product and Back/Forward | UiAutomator selects Happy Days; dock Back/Forward enabled interactions are followed by identity/dock assertions | PASS with runtime limitation |
| 4. Hub route/action vocabulary | Hub opens through fixed tag and asserts Home/Catalog/Cart/Profile/Call; fixture/corpus pin identical values | PASS with runtime limitation |
| 5. Complete regular teardown | Test requires regular identity/shell and absence of expressive header, dock, hub, and legacy site layers | PASS with runtime limitation |
| 6. Compile/runtime policy | `compileDebugAndroidTestKotlin` passes; `adb devices` empty and `/dev/kvm` absent, so no prohibited emulator was provisioned | PASS |
| 7. No schema/site-only hook; CI-009 retained | Production source/schema unchanged; UiAutomator test dependency only; plan/guidance leave final visual runs to CI-009 | PASS |
| 8. Mandatory gate | `bash scripts/pre-commit-check.sh` after the review fix | PASS |

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Public Bloom manifest matches Webora source of truth | GitHub Contents API base64 bytes compared with local fixture | PASS |
| 2 | Canonical route vocabulary normalizes and selects correctly | Focused `SpecCorpusTest` and `ReferenceIntegrationNavTest` | PASS |
| 3 | Stale persistent-bottom journey is rejected | Unsafe fixture in `ExpressiveBloomJourneyContractTest` | PASS |
| 4 | M9 product/history/hub/teardown markers remain total | Positive JVM source contract | PASS |
| 5 | Black-box WebView accessibility API compiles | Debug Android-test Kotlin compilation with AndroidX UiAutomator | PASS |
| 6 | Full security/style/unit gate | Gitleaks, shellcheck, readiness self-tests, core/app tests, release inspection, detekt | PASS |
| 7 | Runtime Pixel 6 journey | Connected instrumentation | NOT RUN — no device or `/dev/kvm`; CI-009 hosted runner owns final evidence |

## Edge cases

- invalid manifest → regular browser mode: PASS — no discovery/validator change; full core/app suites pass.
- origin change / redirect: PASS at compiled contract — regular destination requires complete branded teardown.
- offline with cached manifest: N/A — no cache or transport logic change.
- oversized or malformed payload: PASS — no parser change; core guard suites pass.
- missing product link: PASS — bounded UiAutomator wait fails the journey rather than injecting navigation.
- manifest label drift: PASS — byte digest, normalized corpus, explicit hub labels, and source contract fail.
- unknown action/icon: PASS — no resolver/allow-list change; full unit gate passes.
- Back/Forward unavailable: PASS — instrumentation requires enabled current dock controls before click.
- stale hub after departure: PASS at compiled contract — hub absence is mandatory at regular origin.
- accessibility: PASS for black-box accessible link/label selectors and compiled hub semantics; runtime
  TalkBack/large-text inspection is deferred to the connected CI-009 acceptance environment.
- light/dark/compact/reduced motion: PASS through unchanged UX-013..015 focused suites in the full
  gate; runtime visual comparison is unavailable locally.

## External checks

- GitHub API: Bloom issues #3 and #4 are closed; product source and manifest exist on the default branch.
- Deployed `https://denrzv.github.io` live lint: not runnable from this environment because the
  outbound proxy returns HTTP 403. Public source bytes and offline validators pass; hosted runtime
  remains CI-009's required final acceptance, not silently claimed here.

## Result

Status: QA_PASSED

All locally runnable and source-verifiable criteria pass, review is `RESOLVED`, and the one review
finding was fixed in its own task commit. Runtime instrumentation/screenshots are unavailable in
this managed-cloud checkout and remain explicitly owned by CI-009; DEMO-005 does not claim M9's two
cold visual runs.
