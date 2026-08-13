# QA Report: DEVX-003
Status: QA_PASSED

## Scope

The `SiteSkin inspector` affordance leaves canonical composition and reappears as an entry in the two
browser menus that already exist, one per mode. The panel, the trace, `SiteSkinTraceSink`'s
neutrality and `inspectorValue`'s bound are untouched; so is `CI-003`'s policy and its exclusion
list.

What this ticket is answerable for, in QA terms:

1. A debug build draws no inspector affordance in normal browsing, in any mode.
2. A debug build reaches the inspector in two interactions, in both modes.
3. A release build offers nothing and contains nothing.
4. No website-controlled value shows, hides, labels or reaches any of it.
5. With the overlay gone, `CI-003`'s rendered check measures page pixels — the frames still pass, and
   they pass on the page.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | The offered list contains `INSPECTOR` exactly when the variant has a panel | `a variant without a panel is not offered the inspector`, driving both `true` and `false` | PASS — 9 tests in `SiteSkinChromeModelTest`, 0 failures, 0 errors |
| 2 | The default argument is the variant constant, not a second copy of the decision | `the default reads this variant's constant` | PASS |
| 3 | The closed browser section keeps `PAGE_INFORMATION` and `SETTINGS` in every variant | `browser menu always offers page information and settings`, looped over both answers | PASS |
| 4 | A manifest cannot change the browser section | `selection retains trusted item and browser menu remains immutable` — two very different configurations compared against each other | PASS |
| 5 | Negative control: `browserMenuCommands()` returns the full enum unconditionally | Reverted the body, ran the suite, restored | FAILS scenario 1 alone (1 of 9), on its `false` case. **The first attempt at this control passed** — see Notes |
| 6 | Release variants contain no panel and do contain the stub | `assertInspectorAbsentFromReleaseVariants`, both halves, over `release` and `debugRelease` compiled output | PASS |
| 7 | The two host signatures moved together | `./gradlew :app:assembleDebugRelease` — the variant that compiles `src/release/java` against debug's caller | BUILD SUCCESSFUL |
| 8 | The menu entry's label is a resource, not a literal | `BrowserSurfaceConventionsTest` over `src/main/java`, `src/debug/java`, `src/release/java`, every root contributing | PASS |
| 9 | Discovery outcomes are unchanged | `SiteSkinTraceNeutralityTest`, **unedited** | PASS |
| 10 | The instrumented journey still compiles after the tag's removal | `./gradlew :app:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| 11 | Negative control: a `RegularBrowser` caller that omits a command's handler | Restored the pre-fix call in `BrowserSiteSkinLayoutTest` | FAILS to compile — `No value passed for parameter 'onSettings'` / `'onInspector'`. Restored |
| 12 | The debug resource set has no dangling reference after deleting `inspector_open` | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| 13 | The full journey captures three frames on a device | Hosted run **12** (`31617251038`, `140d206e`) | PASS — `test_status=0`, `png_count=3`, `composed tiles=3 against png_count=3` |
| 14 | Frame 03 clears `CI-003`'s rendered check with the overlay gone | `rendered-03-siteskin-integrated.txt`, run 12 | PASS — `PASSED differing=0.7530481592174976 after 1699ms`, 75× the 1% bar |
| 15 | No inspector affordance is visible in any canonical frame | `preview.png` from `webora-screenshots-140d206e…`, opened by the owner | PASS — all three frames clean. Frame 03's only floating control is the quick action at top-left, which is site-driven product UI |
| 16 | Nothing new needed excluding from the measured region | Same file: `excluded=[Rect(0, 370 - 126, 496)]` | PASS — one rect, `126×126`, the quick action. `chromeInsidePageRegion` is untouched and complete |
| 17 | The overlay's removal changed the measurement | Run 11 (with affordance) vs run 12 (without): `0.7530481592174976` both | **No change, bit-identical** — which refutes `CI-003`'s `FINDING-1` rather than confirming it. See Notes |
| 18 | Whole gate | `bash scripts/pre-commit-check.sh` after every task and both fixes | `[checks] OK` each time |

## Edge cases

- **invalid manifest → regular browser mode.** N/A — no logic change. Nothing in this ticket touches
  validation, discovery or activation. The inspector entry is in the browser-owned section of both
  menus and appears independently of whether any manifest validated; that is the point of a tool for
  diagnosing *rejections*.
- **origin change / redirect.** N/A — no logic change. `SKIN-004`'s deactivate-then-rediscover on
  every main-frame page start is untouched. `inspectorVisible` is browser state and survives a
  navigation exactly as `settingsVisible` does; the panel it opens re-reads the snapshot for the
  current origin, which is `DEVX-001`'s behaviour and unchanged.
- **offline with cached manifest.** N/A — no logic change. `NET-002`'s cache paths, freshness bound
  and conditional revalidation are not touched, and `SiteSkinTraceNeutralityTest` passing unedited is
  the evidence that the discovery matrix behaves identically.
- **oversized or malformed payload.** N/A — no logic change. The 128 KiB sentinel, the 131,073-byte
  core bound and the nesting scan are untouched. The panel still displays untrusted text only through
  `inspectorValue`, bounded by `SiteSkinLimits.MAX_SUBTITLE_LENGTH`.
- **accessibility (TalkBack, font scale).** Covered, and the entry is better off than the affordance
  it replaces. Its label is `inspector_menu_entry` in `src/main/res`, enforced by
  `BrowserSurfaceConventionsTest`'s literal scan over all three source roots. In integrated mode it
  renders through `MenuItem`, which applies `heightIn(min = MINIMUM_TOUCH_TARGET)`; in regular mode
  through `DropdownMenuItem`, whose Material 3 minimum height is already 48 dp — the same component
  `Settings` and `Page information` have always used there. Under a large font scale a menu row grows
  and scrolls, where the removed floating affordance was a fixed pill pinned over the page.
  Instrumented TalkBack/font-scale evidence is `A11Y-001`'s and is not re-derived here.

## Result
Status: QA_PASSED
Notes:

**Scenario 5 is the finding worth carrying forward.** The first attempt at that negative control
*passed*, which meant the test could not tell the correct implementation from a broken one: AGP 9.1
creates only `testDebugUnitTest`, where `SITESKIN_INSPECTOR_AVAILABLE` is always `true`, so
`browserMenuCommands()` reading the constant inline returned the same list as
`BrowserMenuCommand.entries.toList()`. Making availability a parameter with the constant as its
default made both answers reachable. Any future variant-gated decision has the same trap: if the only
unit-test variant fixes the flag, read it through a parameter or the test is decoration.

**Scenario 15 is owner-confirmed instrumented evidence, not a gate claim.** This session cannot
download artifacts (`403`); the owner opened `preview.png` from
`webora-screenshots-140d206ebc51761eb4a3efe43dbb5b2706320af5` and confirmed all three frames. The
SHA matches the commit being judged, which `DEVX-002`'s naming exists to make checkable after three
contaminated frames from run #5 were once read as current.

**Scenario 17 is the one that changes a documented claim.** `CI-003`'s `FINDING-1` held that the
inspector overlay sat inside the measured page region unexcluded, so two ~0.84% chrome buttons could
clear the 1% bar over a blank page. Removing the overlay moved the fraction by **nothing**:
`0.7530481592174976` in run 11 with it, `0.7530481592174976` in run 12 without. A rect that
contributes differing pixels cannot be deleted with no effect on the count, so it was never in the
rectangle — it sat below `y=2127`, where the region ends and the SiteSkin bottom navigation begins.
The claim was structural inference (a full-screen sibling's pixels *can* land inside the region) that
nobody checked against the rects, which were recorded in a `rendered-*.txt` at the time it was
written. Corrected in `CLAUDE.md`, `docs/BACKLOG.md`, `docs/ROADMAP.md`, both review reports and this
report; the PRD, plan and research keep the belief they were written under.

The margin was 42 pixels, so the *rule* survives its wrong instance intact: anything composed into
`BROWSER_CONTENT_TAG` or drawn over it must be excluded or kept out.

**Elapsed time is not a regression.** 1699 ms here against 696 ms in run 11 — both far inside the
20 s deadline, and both records exist only because `CI-003` writes the measurement on success as well
as on failure. Without that habit this scenario could not have been checked at all.
