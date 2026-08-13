# DEVX-003: Research
Status: RESEARCH_READY

Input for `/plan`. Answers the PRD's three open questions, and the third one changes what the ticket
has to do.

## 1. What the inspector is today

| File | Source set | Role |
|---|---|---|
| `inspector/SiteSkinInspectorHost.kt` | `src/debug/java` | `SITESKIN_INSPECTOR_AVAILABLE = true`, the floating affordance, and the panel when open |
| `inspector/SiteSkinInspectorHost.kt` | `src/release/java` | `SITESKIN_INSPECTOR_AVAILABLE = false`, an empty composable. Shared into `debugRelease` |
| `inspector/SiteSkinInspectorPanel.kt` | `src/debug/java` | The panel itself |
| `inspector/InspectorState.kt` | **`src/main/java`** | `inspectorRecorder()` and `rememberInspectorSnapshot(...)` |

The call site is `BrowserScreen.kt:287`, at the **top level of `BrowserScreen`**:

```kotlin
SiteSkinInspectorHost(rememberInspectorSnapshot(recorder = traceRecorder, version = traceVersion, …))
```

and the debug host is:

```kotlin
Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
    WeboraFloatingActionButton(onClick = { open = true }, modifier = Modifier.align(BottomEnd)…)
}
if (open) { SiteSkinInspectorPanel(snapshot, onClose = { open = false }) }
```

**Note that `InspectorState.kt` is in `main`, not in a variant**, and already reads
`SITESKIN_INSPECTOR_AVAILABLE`:

```kotlin
internal fun inspectorRecorder(): SiteSkinTraceRecorder? =
    if (SITESKIN_INSPECTOR_AVAILABLE) SiteSkinTraceRecorder() else null
```

That is the load-bearing fact for the whole plan: **`main` can read the variant constant, and it
folds at compile time.** A debug-only menu entry does not need a new mechanism; it needs the one that
already exists.

## 2. Open question 3, answered — and it makes the fix smaller

**The inspector overlay is a full-screen sibling, not a child of `BROWSER_CONTENT_TAG`.**
`SiteSkinInspectorHost` is called from `BrowserScreen`, while `BROWSER_CONTENT_TAG` is on a `Box`
inside `RegularBrowser`'s `Column`. The host's own `Box(fillMaxSize())` overlays the entire screen,
so its pixels *land inside* the measured rectangle without the composable being in it.

The distinction matters exactly once, and in this ticket's favour: because the affordance is a
sibling overlay, **removing it removes its pixels from the measured region completely.** There is no
residual child to exclude.

What then remains inside `BROWSER_CONTENT_TAG`:

| Composable | In the region? | Status |
|---|---|---|
| `HardenedWebView` | yes, fills it | the page — what the check is measuring |
| `BrowserErrorPage` | yes, when `loadFailure` | browser-owned, deliberately counts as rendered (`CI-003` plan) |
| `SiteSkinQuickActions` | yes, a **child** of the box | already excluded by `chromeInsidePageRegion` |
| inspector affordance | today by overlay; **gone after this ticket** | — |

So PRD criterion 6 is satisfied by removal alone. No change to `CI-003`'s exclusion list, which is
what that ticket bet on when it deferred here.

## 3. Open question 1 — where the affordance goes, and the count that rules out the obvious answer

The PRD allows **two deliberate interactions** from the browsing surface. Measured from the code:

| Route | Regular mode | Integrated mode |
|---|---|---|
| Menu → `Settings` → *(entry in `PrivacySettingsScreen`)* | `AddressBar` menu (1) → Settings (2) → entry (3) | SiteSkin menu (1) → Settings (2) → entry (3) |
| Menu → *(entry in the menu itself)* | menu (1) → entry (2) ✅ | menu (1) → entry (2) ✅ |

**Settings is already two interactions deep in both modes**, because there is no direct settings
button — `AddressBar` reaches it through its own `DropdownMenu` (`BrowserScreen.kt:574-577`), and
integrated mode reaches it through the SiteSkin menu's browser section. An entry inside
`PrivacySettingsScreen` therefore costs **three**, and misses the criterion in the very mode a site
owner debugging a manifest is in.

So the affordance belongs in **the menus**, which are also the browser-owned surfaces that already
exist in both modes:

- Regular: the `DropdownMenu` in `AddressBar`, beside `Settings`.
- Integrated: the SiteSkin menu's browser section, which `SKIN-003` already defines as a closed,
  separately labelled section that *manifest entries cannot suppress or replace*. Its commands are
  the `BrowserMenuCommand` enum (`SiteSkinChromeModel.kt:38`) — `PAGE_INFORMATION`, `SETTINGS`.

Adding a third command, offered only when `SITESKIN_INSPECTOR_AVAILABLE`, puts the debug entry inside
a section whose browser-ownership is already specified and already tested.

## 4. Open question 2 — does moving it disturb `DEVX-001`?

No, provided the snapshot stays where it is.

`rememberInspectorSnapshot` is called at `BrowserScreen.kt:288` and passed *into* the host. Its cost
is already folded out in release variants: `inspectorRecorder()` returns null, `recorder?.let { … }`
yields a null snapshot, and the release host returns immediately. Moving the *affordance* into a menu
does not touch that path.

Two things the plan must keep:

- **The snapshot must not become eager on a surface that did not assemble it before.** It is a
  `@Composable` with `remember` keys on `(recorder, version, origin)` and `(recorder, record, state)`.
  Leaving the call exactly where it is preserves both the timing and the memoisation.
- **`SiteSkinTraceNeutralityTest` must pass unchanged.** It asserts the discovery matrix produces the
  same `ManifestDiscoveryOutcome` and `CandidateDisposition` with a recording sink and a discarding
  one. Nothing here touches `ManifestDiscoveryCoordinator` or the sink, so the test should be
  untouched — and if it needs editing, that is a signal the change reached further than intended.

## 5. Trust boundary

No origin is contacted and no manifest is parsed by this change. The SiteSkin-specific rule is
inherited from `SKIN-003` and must survive intact:

> The integrated menu always appends a **separately labelled closed browser section** for page
> information and settings, which manifest entries cannot suppress or replace.

A debug-only inspector entry joins that closed section. Consequences the plan must hold:

- The entry's label comes from `strings.xml`, like every other browser-owned string (`A11Y-001`, and
  `BrowserSurfaceConventionsTest` fails a literal reaching `Text(`).
- Its presence is decided by `SITESKIN_INSPECTOR_AVAILABLE` — a variant constant — and **never** by
  `BuildConfig.DEBUG`, which `debugRelease` sets true while compiling against the release stub.
- No manifest field, page value or website-controlled input may add, remove, relabel, reorder or
  reach it. The command is an enum value in browser code; the manifest contributes items to a
  different section entirely.
- The panel keeps receiving the trusted configuration as *displayed data* with `inspectorValue`'s
  bound intact. That is `DEVX-001`'s contract and is not reopened here.

## 6. Risks the plan must answer

1. **The release-absence gate has two halves.** `assertInspectorAbsentFromReleaseVariants` fails if
   the panel class is present *or if the stub's class is absent*. Any file movement between source
   sets can satisfy the first half while quietly voiding the second — the second half exists because
   renaming the panel would otherwise make the check pass while proving nothing.
2. **A menu that offers a command the variant cannot service.** If `BrowserMenuCommand.INSPECTOR` is
   added to the enum in `main` but the filtering is wrong, a release build could render an entry that
   does nothing. The safe shape is to build the offered list from the constant, not to render the
   whole enum and no-op the handler.
3. **Existing tests assert the browser section's contents.** `SiteSkinChromeModel` and the chrome
   tests cover the closed browser section; adding a command may require updating an expectation. An
   expectation that needs *loosening* is a warning sign — the section is closed on purpose.
4. **Hoisting the `open` state.** The debug host currently owns `var open by remember`. Menu-driven
   opening means the state moves to `BrowserScreen`, and the release stub's signature must move with
   it or the release variant stops compiling — a failure the gate *will* catch, since
   `:app:assembleDebugRelease` is in the build, but only if the signatures are changed together.
5. **Two menus, one behaviour.** Regular and integrated reach the inspector through different menu
   implementations. Two call sites is the cost of meeting the interaction budget in both modes; the
   plan should make the *decision* to offer it a single shared expression rather than duplicating the
   condition.

## 7. Open questions for `/plan`

- Does the regular-mode `DropdownMenu` entry and the integrated `BrowserMenuCommand` entry share a
  single "is the inspector offered" expression, or are they two reads of the same constant?
- Does `SiteSkinInspectorHost` keep its name once it renders only the panel, or does the panel become
  the thing `BrowserScreen` calls directly with a visibility flag?
- Is there any value in keeping a debug-only *gesture* (long-press) as a second route, or is one
  deliberate affordance per mode the whole answer? The PRD asks for reachability, not redundancy.
