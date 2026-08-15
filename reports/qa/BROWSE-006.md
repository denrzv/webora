# QA Report: BROWSE-006
Status: QA_PASSED

## Scope

Bounded session operations, safe restoration, independent tab observations/renderers, switcher
reachability from Home/regular/SiteSkin modes, browser-owned summaries, explicit tab limit,
renderer cleanup, and regression coverage across the existing application and core suites.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Create appends/selects fresh Home without changing prior state | `BrowserSessionTest` | PASS |
| 2 | Select keeps independent URL/mode/back-forward state | reducer tests and late-background negative control | PASS |
| 3 | Close active/inactive/final selects deterministic result | `BrowserSessionTest` | PASS |
| 4 | Ninth tab is refused without eviction and UI explains limit | JVM model + compiled `TabSwitcherTest` | PASS |
| 5 | Ordered tabs and selection restore safely | `BrowserSessionSnapshotTest` | PASS |
| 6 | Integrated state is not persisted as trusted configuration | integrated downgrade negative control | PASS |
| 7 | Manifest/editable text cannot label switcher actions | `TabSwitcherModelTest` | PASS |
| 8 | Switcher reachable in Home, regular and integrated surfaces | source wiring + compiled Compose tests | PASS |
| 9 | Background renderer cannot overwrite selected tab | `late background observation updates its owner` | PASS |
| 10 | Existing security, UI, inspector and SiteSkin behaviour | full pre-commit suite (294 JVM tests) | PASS |
| 11 | Live two-WebView switching and Activity recreation | connected instrumentation | NOT RUN — no device or `/dev/kvm`; test sources compile |
| 12 | Screenshot of perceptible switcher/Home change | connected Android capture | NOT RUN — no device or `/dev/kvm`; no software emulator provisioned per policy |

## Edge cases

- invalid manifest → regular browser mode: PASS — unchanged total validator; restored integrated
  pages explicitly downgrade to regular and rediscover.
- origin change / redirect: PASS — each renderer observation still passes through
  `BrowserState.observe` and exact `SiteOrigin`; callbacks carry an owning tab id.
- offline with cached manifest: PASS — manifest coordinator/cache behaviour is unchanged and remains
  covered by the full unit suite.
- oversized or malformed payload: PASS — manifest guards are unchanged; saved tab URLs over 2,048
  characters, malformed schemes/URLs, invalid ids and duplicate ids are rejected.
- accessibility (TalkBack, font scale): PASS at compile/static/unit level — switcher uses the
  project's 48 dp wrappers, selected semantics, ordered position/count descriptions and stable
  tags. Runtime TalkBack/font-scale inspection is unavailable without a device.
- empty session restoration: PASS — creates one fresh selected Home tab.
- corrupt/unsupported saved-state version: PASS — falls back to a fresh session.
- closing the selected final tab: PASS — creates a new id and Home state rather than exiting.
- closing a background tab: PASS — selection is unchanged and only that controller is destroyed.
- tab limit: PASS — creation returns the unchanged session; UI disables New tab and explains the
  eight-tab maximum.

## Result
Status: QA_PASSED

Notes: all runnable local gates and compiled Android test checks pass. Runtime instrumentation and
the requested screenshot evidence are explicitly unavailable in this managed checkout because
there is no connected device and `/dev/kvm` is absent; repository policy forbids provisioning a
software-only emulator in that environment.
