# Review: DEVX-003
Date: 2026-08-12
Status: RESOLVED

## Summary

Removes the permanent `SiteSkin inspector` floating affordance from composition and puts the
inspector behind an entry in the two browser menus that already exist, one per mode.

Two problems wearing one shape, and the ticket separates them correctly. The affordance was in every
canonical frame, so Webora's own product evidence read as an internal build; and because it was a
full-screen sibling overlay, its pixels landed inside the rectangle `CI-003` measures for drawn page
content, giving that check a second piece of Webora's chrome to count as page. `CI-003` deferred here
rather than adding a second exclusion, and the bet paid: **removal closed the hole without touching
`chromeInsidePageRegion`.**

Verified on hosted run **12** (`31617251038`, `140d206e`): `test_status=0`, `png_count=3`,
`composed tiles=3 against png_count=3`. That run is itself the interesting evidence — see Security.

The most valuable thing produced by this ticket is not in the diff. `TASK-1`'s first negative control
**passed**, which meant the test could not distinguish the correct implementation from a broken one.
See `FINDING`-adjacent note under Test coverage.

## Architecture

| Concern | Assessment |
|---|---|
| Where the affordance went | Correct, and the count decided it rather than taste. `Settings` is already two interactions deep in both modes, so an entry in `PrivacySettingsScreen` would have cost three — in the very mode a site owner debugging a manifest is in. The menus cost two in both. |
| One decision, two readers | Held, and strengthened past the plan. `browserMenuCommands()` is the single expression; `browserMenuLabel()` was extracted alongside it, so the two menus cannot offer one command under two names. Regular mode now renders the same list instead of two hardcoded items, which it did not before this ticket. |
| Built from the constant, not filtered after | `buildList` appends `INSPECTOR` only when available. A release variant does not draw an item whose handler does nothing — an offered command a variant cannot service is a promise it cannot keep. |
| Enum is not the list | `browserMenuCommands()` deliberately does not derive from `BrowserMenuCommand.entries`. `SKIN-003` makes this section closed; a value added to the enum must not silently reach a menu. |
| Host's remaining job | `SiteSkinInspectorHost` is now the panel and nothing else — the `Box`, the FAB, the internal `open` state, `INSPECTOR_AFFORDANCE_TAG` and `AFFORDANCE_INSET` are gone. Visibility hoisted to `BrowserScreen`, which is what forced the release stub's signature to move in the same commit. |
| `DEVX-001` untouched | `rememberInspectorSnapshot` stays at its call site with its `remember` keys, so release folding and memoisation are unchanged. `SiteSkinTraceNeutralityTest` passes **unedited**, which research named as the signal the change had not reached too far. |
| Placement of `browserMenuLabel` | In `SiteSkinChrome.kt`, beside the menu that has always rendered these commands, and in the same package as `BrowserMenuCommand`. Regular mode importing it is the smaller evil; see Not findings. |

## Security

| Property | Assessment |
|---|---|
| Website influence on the affordance | None. The command is an enum value in browser code, the list is built from a compile-time constant, and the label is a `strings.xml` resource. No manifest field, page value or trusted configuration decides whether it appears, what it says, or where it sits. Manifest items populate a different section entirely. |
| `SKIN-003`'s closed section | Intact and now better expressed. The section was previously "the whole enum"; it is now an explicit list, so the closure is a property of one function rather than of what nobody happened to add to the enum. |
| Release absence | Two independent mechanisms, neither leaning on the other: the command is absent from the offered list, and the release `SiteSkinInspectorHost` ignores its visibility flag. `assertInspectorAbsentFromReleaseVariants` still checks compiled output for the panel's absence **and** the stub's presence. |
| `BuildConfig.DEBUG` | Not used, correctly. AGP derives it from `isDebuggable` and `debugRelease` sets it true while compiling against the release stub. |
| Panel's inputs | Unchanged. `inspectorValue`'s `MAX_SUBTITLE_LENGTH` bound on untrusted text is not reopened. |
| Evidence integrity | The mechanism refused is the load-bearing decision: **no screenshot mode.** Suppressing the affordance while the harness captures would make the photograph differ from the running build, which is the same failure `CI-002` refused when it declined a dismiss-whatever-is-in-the-way loop. |
| Run 12 as evidence | A green run *is* the finding here. `captureWhenRendered` fails when the page region never clears `MINIMUM_DIFFERING_FRACTION`, and the overlay's pixels are no longer in that region to help it. If the overlay had been load-bearing for that check, this is the run that would have gone red. |

## Findings

### FINDING-1 · Low (hygiene) · `inspector_open` is now an unreferenced resource
**File:** `app/src/debug/res/values/strings.xml:10`

The affordance's label lost its only call site. It survives as a debug resource with no reader, and
its value is identical to `inspector_title`, which *is* live — so the file now holds two identical
strings where one is dead. That is the shape in which someone later edits the wrong one and sees no
change.

Current:
```xml
<string name="inspector_open">SiteSkin inspector</string>
<string name="inspector_title">SiteSkin inspector</string>
```

Fix: delete `inspector_open`. The menu entry's label is `inspector_menu_entry` in `src/main/res`,
which is where it has to live — `main` compiles in every variant and cannot see a debug-only
resource.

### FINDING-2 · Low (correctness) · a defaulted `onInspector` can draw an entry that does nothing
**File:** `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt:458`

`RegularBrowser` declares `onInspector: () -> Unit = {}`, following `onSettings`'s existing default.
The plan's rule is that a variant must not offer a command it cannot service — that is why the list
is built from the constant rather than rendered-then-no-opped. A defaulted no-op callback
reintroduces exactly that failure one layer down: the entry is drawn, because availability said so,
and selecting it does nothing, because the caller forgot to wire it.

Not hypothetical — `BrowserSiteSkinLayoutTest` composes `RegularBrowser` without either callback, so
in that harness both entries are already inert.

Current:
```kotlin
onSettings: () -> Unit = {},
onInspector: () -> Unit = {},
```

Fix: drop both defaults and wire them explicitly at both call sites. A half-defaulted pair would be
arbitrary, and the compiler is a better guarantee than the convention that whoever adds a command
also remembers its handler.

## Not findings

- **`browserMenuLabel` lives in `SiteSkinChrome.kt` while regular mode also calls it.** The command
  enum already lives in that package (`SiteSkinChromeModel.kt`), because `SKIN-003` defined the
  closed browser section there. Moving the label to `browser/` would split the label from the command
  it names, across two packages, which is the drift the shared expression exists to prevent.
- **Two `when` dispatchers over `BrowserMenuCommand`, one per mode.** They do genuinely different
  things — regular calls hoisted callbacks, integrated sets `BrowserScreen` state and closes the
  drawer. The shared part is the *list* and the *labels*, and both are shared. Both `when`s are
  exhaustive, so a fourth command fails to compile until each mode decides what it means.
- **`PAGE_INFORMATION` is still a no-op in both modes.** Pre-existing and outside this ticket. It is
  now visibly a no-op — an explicit `-> Unit` branch rather than a dropped `if` — which is an
  improvement in honesty, not a regression.
- **`inspectorVisible` is hoisted in `main`, so release variants carry a state holder they never
  set.** One `Boolean` in composition. The alternative — a variant-specific `BrowserScreen` — would
  fork the browser's main surface per build type to save it, which is a far worse trade.
- **The release stub takes `open` and `onClose` and ignores both.** It has always ignored `snapshot`
  too. The signature exists to match the debug host, and `:app:assembleDebugRelease` is what proves
  the two have not drifted.
- **No test asserts that regular mode's dropdown renders the offered list.** It needs Compose UI
  testing; there is no Robolectric here by design. Compile-checked via
  `:app:compileDebugAndroidTestKotlin` and evidenced by run 12. Recorded as instrumented evidence,
  never promoted to a gate claim.
- **`WeboraFloatingActionButton` survives with one caller.** `SiteSkinQuickActions` still uses it.
  What left canonical composition is the last *browser-owned* floating button; the site-driven one
  remains, and `chromeInsidePageRegion` already excludes it.
- **Regular mode's `DropdownMenuItem` has no explicit `heightIn(MINIMUM_TOUCH_TARGET)` while the
  integrated `MenuItem` does.** Material 3 menu items carry a 48 dp minimum height of their own; the
  integrated wrapper states it because that menu is a `ModalDrawerSheet`, not a `DropdownMenu`.
  Unchanged by this ticket either way.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `SiteSkinChromeModel.kt` | 9 in `SiteSkinChromeModelTest` (3 new) | Membership at both availability answers; the default is wired to the constant; the closed section's floor at both answers; the manifest cannot change the section |
| `SiteSkinChrome.kt` | `BrowserSurfaceConventionsTest` | The new entry's label resolves from resources; no literal reaches `Text(` |
| `SiteSkinInspectorHost.kt` (both variants) | `assertInspectorAbsentFromReleaseVariants`, `:app:assembleDebugRelease` | Panel absent from release output, stub present; signatures compile together |
| `BrowserScreen.kt` | `:app:compileDebugAndroidTestKotlin`, run 12 | Wiring compiles; frames captured |
| `ManifestDiscoveryCoordinator` | `SiteSkinTraceNeutralityTest`, **unedited** | Discovery outcomes unchanged |

**Negative control, and the reason this section is worth reading.** The first control *passed*.
Replacing `browserMenuCommands()` with `BrowserMenuCommand.entries.toList()` changed nothing any test
could observe, because AGP 9.1 creates only `testDebugUnitTest`, where the constant is always `true`
— the correct and the broken implementation return the same list there. The test was decoration in
the only variant that runs it. Making availability a parameter with the constant as its default made
both answers reachable; the retried control now fails `a variant without a panel is not offered the
inspector` alone, on its `false` case. This is the same thin-wrapper-over-pure-function shape the
repository uses for Android-touching code, applied to a variant-gated decision for the same reason.

## Verdict

**Accept with two low-severity fixes.** `FINDING-1` is dead copy that invites a wrong edit;
`FINDING-2` is the ticket's own rule — do not offer what you cannot service — holding at the list
layer and not at the callback layer. Both are cheap, both are in scope, and both become `TASK-FIX`
micro-tasks with their own commits.

Both fixed after this review, each in its own commit. `TASK-FIX-2` carries a negative control: the
instrumented test's previous call now fails compilation on both missing parameters, so the compiler
enforces what the convention used to.

The ticket's premise held end to end: the overlay was the problem, removal was the fix, and
`CI-003`'s residual hole closed with no second exclusion list to maintain.
