# DEVX-003: Plan
Status: PLAN_APPROVED

Input: [`../research/DEVX-003.md`](../research/DEVX-003.md) (`RESEARCH_READY`).

## Flow

```
before                                    after
──────                                    ─────
BrowserScreen                             BrowserScreen
  …                                         …
  SiteSkinInspectorHost(snapshot)            SiteSkinInspectorHost(snapshot, open, onClose)
    ├─ Box(fillMaxSize)                        └─ if (open) SiteSkinInspectorPanel(…)
    │    └─ FAB  ← in every frame
    └─ if (open) Panel                       inspectorOffered = SITESKIN_INSPECTOR_AVAILABLE
                                             ├─ regular:    AddressBar DropdownMenu entry
                                             └─ integrated: BrowserMenuCommand.INSPECTOR
                                                            in SKIN-003's closed browser section
```

The affordance moves from a permanent overlay into the two menus that already exist, one per mode.
The panel is unchanged. The snapshot call stays exactly where it is, so its `remember` keys and its
release-variant folding are untouched.

**Two interactions in both modes**, which is what forced the menus rather than settings: research
measured `Settings` as already two deep in each mode, so an entry inside `PrivacySettingsScreen`
would have cost three — in the very mode a site owner debugging a manifest is in.

## Trust boundary

No origin is contacted and no manifest is parsed. The rule this ticket must not weaken is
`SKIN-003`'s, and the new entry joins the section it protects:

> The integrated menu always appends a **separately labelled closed browser section** for page
> information and settings, which manifest entries cannot suppress or replace.

The contract for the new command, stated before the file list:

- **Presence is decided by `SITESKIN_INSPECTOR_AVAILABLE`**, a `const val` declared in each variant's
  own source set beside its panel — **never** `BuildConfig.DEBUG`, which AGP derives from
  `isDebuggable` and `debugRelease` sets true while compiling against the *release* stub. `main`
  already reads this constant in `inspectorRecorder()`, so this is the existing mechanism, not a new
  one.
- **The offered list is built from the constant, never rendered-then-no-opped.** A release build must
  not draw an entry whose handler does nothing; the command must not be in the list at all.
- **The label is a browser-owned string resource.** `A11Y-001` and `BrowserSurfaceConventionsTest`
  both require it, and the second fails a literal reaching `Text(`.
- **Nothing website-controlled reaches the entry.** No manifest field, page value or trusted
  configuration decides whether it appears, what it says, or where it sits. Manifest items populate a
  different section entirely; this one is closed.
- **The panel's own inputs are unchanged.** It keeps receiving the trusted configuration as displayed
  data with `inspectorValue`'s `MAX_SUBTITLE_LENGTH` bound intact. `DEVX-001`'s contract is not
  reopened.

**And the mechanism this plan does not choose**, restated from the PRD because it is the one that
looks attractive later: no "screenshot mode" that hides the affordance while the harness captures.
The frames must show what a user of that build sees. The affordance leaves canonical composition
because it is not there, not because it ducked — the same reason `CI-002` refused a generic
dismiss-whatever-is-in-the-way loop.

## Security / integrity

| Rule | Mechanism |
|---|---|
| Release variants have no panel and no affordance | `SITESKIN_INSPECTOR_AVAILABLE = false` folds the entry out; `assertInspectorAbsentFromReleaseVariants` still checks compiled output for the panel's absence **and the stub's presence** |
| The closed browser section stays closed | `INSPECTOR` is an enum value in browser code, filtered by a browser constant; manifest items never reach it |
| No new eager work | `rememberInspectorSnapshot` stays at its current call site with its current keys |
| Discovery is unaffected | `ManifestDiscoveryCoordinator` and `SiteSkinTraceSink` are untouched; `SiteSkinTraceNeutralityTest` must pass **unedited** — needing to edit it is the signal the change reached too far |
| `CI-003`'s measured region | The affordance was a full-screen sibling, so removal takes its pixels out of the rectangle entirely. `chromeInsidePageRegion` is not touched |

## Files

### `app/src/main/java/app/webora/browser/siteskin/SiteSkinChromeModel.kt`

`BrowserMenuCommand` gains `INSPECTOR`. The enum is the closed set of browser-owned commands; adding
a value here keeps the section's ownership where `SKIN-003` put it.

A single shared expression decides whether it is offered — `browserMenuCommands()` returning the list
for the current variant — so the two call sites read one decision rather than repeating a condition.
That is the answer to research's first open question: **one expression, two readers.**

### `app/src/main/java/app/webora/browser/siteskin/SiteSkinChrome.kt`

The integrated menu renders `browserMenuCommands()` instead of `BrowserMenuCommand.entries`, and maps
`INSPECTOR` to its string resource beside `PAGE_INFORMATION` and `SETTINGS`.

### `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`

- Hoists `inspectorVisible` state and passes it to the host with an `onClose`.
- `AddressBar`'s `DropdownMenu` gains the same debug-only entry, from the same `browserMenuCommands()`
  decision.
- The integrated menu's `onBrowserSelect` handles `INSPECTOR` by setting `inspectorVisible = true`,
  beside the existing `SETTINGS` branch.

### `app/src/debug/java/app/webora/browser/inspector/SiteSkinInspectorHost.kt`

Loses the `Box`, the `WeboraFloatingActionButton`, the `open` state, `INSPECTOR_AFFORDANCE_TAG` and
`AFFORDANCE_INSET`. Becomes:

```kotlin
@Composable
internal fun SiteSkinInspectorHost(snapshot: InspectorSnapshot?, open: Boolean, onClose: () -> Unit) {
    if (snapshot == null || !open) return
    SiteSkinInspectorPanel(snapshot, onClose)
}
```

### `app/src/release/java/app/webora/browser/inspector/SiteSkinInspectorHost.kt`

Signature moves with it, or `:app:assembleDebugRelease` stops compiling. The stub's body stays empty
and `SITESKIN_INSPECTOR_AVAILABLE = false` stays declared here — the file whose *presence* the second
half of the release gate asserts.

### `app/src/main/res/values/strings.xml`

`inspector_open` already exists as the affordance label. It is reused as the menu entry label if it
reads correctly in a menu; otherwise a new `inspector_menu_entry`. Decided at implementation by
reading the existing string, not guessed here.

### Docs

`CLAUDE.md` (a `DEVX-003` paragraph), `docs/ROADMAP.md` (tick), `docs/SCREENSHOTS.md` if the frames'
description changes.

## Tests

| Test | Asserts |
|---|---|
| `browserMenuCommandsOffersInspectorWhenAvailable` | the list contains `INSPECTOR` exactly when `SITESKIN_INSPECTOR_AVAILABLE` — one assertion that reads the constant rather than hardcoding a variant |
| `browserMenuCommandsAlwaysOffersPageInformationAndSettings` | the closed section keeps its existing members in every variant; a coverage floor so the list cannot silently shrink |
| `SiteSkinChromeModel` existing tests | unchanged. If an expectation needs **loosening**, that is a finding, not a fix — the section is closed on purpose |
| `BrowserSurfaceConventionsTest` | unchanged and green over all three source roots; the new entry's label must come from `strings.xml` |
| `SiteSkinTraceNeutralityTest` | unchanged and green |
| `assertInspectorAbsentFromReleaseVariants` | unchanged and green — both halves |

**Negative control:** make `browserMenuCommands()` return the full enum unconditionally.
`browserMenuCommandsOffersInspectorWhenAvailable` must fail in the release-variant reading while the
closed-section test still passes — proving the second cannot stand in for the first.

**Not verifiable here:** that the canonical frames no longer contain the affordance. That needs a
hosted run and a human opening `preview.png`; recorded as instrumented evidence, never promoted to a
gate claim, exactly as `CI-003` recorded its own.

## Out of scope

The panel's contents, the trace, `inspectorValue`'s bound, `CI-003`'s policy or exclusion list, the
quick action (site-driven product UI, legitimately in frame), and `UX-008`.
