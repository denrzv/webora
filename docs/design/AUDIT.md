# Browser-owned UI audit

Input to `UX-001`. Recorded after the `DIST-001` debug APK was run on an emulator and the browser
chrome was found to look unfinished next to the SiteSkin chrome it wraps.

This is not a taste document. Every finding below points at a line, and the constraints in §3 are
the reason a design direction can be rejected on something other than preference.

---

## 1. What the code actually says

### 1.1 There is no browser-owned design system

`MainActivity.kt:38` composes a bare `MaterialTheme {}`. No `colorScheme`, no `typography`, no
`shapes` argument is supplied anywhere in the app. Every browser-owned screen therefore renders in
**Material 3's default baseline palette** — the purple one every un-themed Compose app ships with —
in the default type scale.

The consequence worth stating plainly: **Webora has no visual brand in its own product.** The app
named in `DEVELOPMENT_PLAN.md`'s naming table looks like a scaffold.

`res/values/themes.xml` is four lines and inherits the *platform* theme
`android:Theme.Material.Light.NoActionBar` — not a Material Components theme, and with no
`values-night` counterpart. `MainActivity` never consults `isSystemInDarkTheme()`, so browser chrome
is light-only regardless of the system setting.

### 1.2 The navigation controls are the weakest surface

`BrowserScreen.kt:493-534` (`AddressBar`):

```
OutlinedTextField(label = "Search or enter address")   ← floating label, full width, no affordances
BrowserSecurityIdentity                                 ← unstyled Text row
FlowRow { Back  Forward  Reload  Home  More }           ← five filled, text-labelled buttons
```

Specifically:

- Five **filled** `Button`s in a row. Material's filled button is its highest-emphasis control; using
  it for every navigation action means nothing on screen has emphasis.
- The labels are **words**, not icons. `Back` `Forward` `Reload` `Home` `More` consume the full
  width, wrap unpredictably via `FlowRow`, and read as a debug harness.
- **No padding anywhere.** The `Column` at `BrowserScreen.kt:505` applies none, so the text field
  and the button row sit flush against the screen edges and against each other.
- No toolbar surface, no elevation, no separation between chrome and content.
- The address field carries a **floating label**, which is a form-field idiom. No shipping browser
  labels its address bar; the placeholder disappears on focus and the label is permanent chrome cost.

### 1.3 Error and settings screens are unstyled stacks

`BrowserScreen.kt:537` (`BrowserErrorPage`) — a `Surface` wrapping a `Column` with a heading, a
domain, a sentence and two buttons, with **no padding, no spacing, no alignment, no illustration**.

`PrivacySettingsScreen.kt:28` — a scrolling `Column` with the same absence. The global toggle is a
`Text` followed by a bare `Switch` on the next line (`PrivacySettingsScreen.kt:35-43`) rather than a
list row; the per-origin resets are full-width filled buttons whose label is the whole canonical
origin (`PrivacySettingsScreen.kt:52`).

### 1.4 Home and onboarding are the closest to presentable, and still untokenised

`HomeScreen.kt:31` at least sets `contentPadding = 20.dp` and `spacedBy(16.dp)`, and uses
`MaterialTheme.typography` roles. `OnboardingScreen.kt:41` pads 24 dp and centres. Both are
structurally fine and visually generic: default palette, `Card` with no accent, hard-coded `Spacer`
heights (`OnboardingScreen.kt:47,49,55,57`) instead of a spacing scale, and no page indicator beyond
a `Step 1 of 3` string (`OnboardingScreen.kt:56`).

### 1.5 There is no icon source on the classpath

No `material-icons-core` or `material-icons-extended` artifact is resolved by the build, and
`res/` contains no `drawable/` directory at all — only `values/`. The proof is
`SiteSkinChrome.kt:135`, where icons are **Unicode text glyphs**:

```kotlin
"home" -> "⌂"   "grid_view" -> "▦"   "shopping_cart" -> "▣"   "person" -> "●"   "call" -> "☎"
```

and `strings.xml`'s `siteskin_quick_actions_glyph` is the literal character `+`. That was a
reasonable placeholder for `SKIN-003`; it is not a shipping icon set, and it is why no
browser-chrome control can be icon-only today.

### 1.6 The manifest-driven surface is better specified than the browser's own

`SiteSkinTheme.kt` gives *websites* a six-role colour model with light and dark projections, a
derived dark surface, and a WCAG guard enforcing 4.5:1 body and 3:1 non-text contrast before any
colour reaches the screen (`SiteSkinTheme.kt:75-87`).

Webora gives itself none of that. **A site that publishes a manifest gets a better-specified visual
system than the browser rendering it.** That inversion, more than any individual screen, is what
M6 exists to correct.

---

## 2. What is already right and must survive a rebuild

A redesign that loses these regresses `A11Y-001` and `HARDEN-002`. They are not up for
renegotiation by a design direction.

| Guarantee | Where it lives |
|---|---|
| 48 dp minimum touch target, applied centrally rather than per call site | `BrowserAccessibility.kt:23,32` — `MINIMUM_TOUCH_TARGET`, `browserTouchTarget()` |
| All user-visible and accessible copy resolves from `strings.xml` | `BrowserSurfaceConventionsTest.kt:126,129` |
| Browser-owned security identity, derived only from the committed `SiteOrigin` | `BrowserScreen.kt:582` — `BrowserSecurityIdentity`, `BROWSER_SECURITY_TAG` |
| Persistent live region, so a finished load can still announce itself | `BrowserScreen.kt:605` — `BrowserStatusRegion` |
| Assertive on failure, polite on progress | `BrowserAccessibility.kt:113` |
| Switch state announced by `stateDescription`, not by switch position alone | `PrivacySettingsScreen.kt:39-42` |
| Consent choices wrap rather than shrink at large font scale | `BrowserScreen.kt:364` |
| Edge-to-edge, with insets handled at one point | `MainActivity.kt:33`, `BrowserScreen.kt:100` |

---

## 3. Constraints every direction must satisfy

### C1 — The domain and TLS state are browser-owned and non-suppressible

`ADR-006` and `HARDEN-002`. `BrowserSecurityIdentity` reads `securityPresentation(state.mode)`,
which derives from the **committed `SiteOrigin`** — never `state.addressText`, never document
content, never a manifest field. Assistive technology reads the semantics tree rather than the
pixels, so the dedicated node carrying `BROWSER_SECURITY_TAG` is half the guarantee and the visible
row is the other half.

**This is the sharpest constraint on any browser-native direction.** A Chrome-style omnibox that
shows `denrzv.github.io` inside the editable text field collapses browser identity into a field
whose value is also page-derived and user-editable. A direction may unify them visually only if the
displayed identity still comes from `securityPresentation`, and a separate browser-authored
semantics node survives.

### C2 — Browser tokens must not be manifest-influenceable

`SiteSkinColorScheme` (`SiteSkinTheme.kt:42`) is the *entire* website-influenceable colour surface,
and its docs say so. A new `WeboraTheme` must be a separately compiled palette with no path from a
manifest value into it. `UX-002` should carry a test asserting that, since the invariant is
currently maintained only by there being no browser palette at all.

### C3 — `BrowserSurfaceConventionsTest` is a hard gate with a sharp edge

`RAW_BUTTON_IMPORT` (`BrowserSurfaceConventionsTest.kt:137`) bans **any**
`androidx.compose.material3.\w*Button` import — `IconButton`, `OutlinedButton`, `FilledTonalButton`
and aliases included — in every source declaring `@Composable`, except the one file containing
`fun WeboraButton(`.

So an icon-only toolbar needs a **`WeboraIconButton` added to `BrowserAccessibility.kt`**, the file
that already declares `browserTouchTarget()`, `WeboraButton`, `WeboraTextButton` and
`WeboraFloatingActionButton`. Putting it in a new file turns the build red. The scan covers
`src/main/java`, `src/debug/java` and `src/release/java`, and asserts each root contributes.

### C4 — No string literals reach the screen

`TEXT_LITERAL` bans `Text("…")` and `Text(text = "…")`; `NAMED_LITERAL` bans a literal bound to
`label`, `text`, `title`, `description` or `contentDescription`. An icon-only control therefore
**requires** a new `strings.xml` entry for its accessible name — here an unnamed icon fails the
build rather than merely failing review. Decorative glyphs go in `strings.xml` too, marked
`translatable="false"`, as `siteskin_quick_actions_glyph` already is.

### C5 — `A11Y-001` holds unchanged

48 dp targets; 4.5:1 body and 3:1 non-text contrast; no meaning carried by colour alone; 200% font
scale without overlap or truncation of anything load-bearing.

`contrastRatio()` (`SiteSkinTheme.kt:89`) already implements the WCAG relative-luminance formula and
is reusable: `UX-002` should lift it somewhere both palettes can reach and assert the **browser**
palette against it in a JVM test, exactly as the SiteSkin projection already is. Any surface change
also updates `docs/accessibility/CONFORMANCE.md`, which maps each guarantee to its criterion, its
code and its test.

### C6 — Icons need a source, and there is none

Recommendation for `UX-002`: bundle the needed Material Symbols as **vector drawables in
`res/drawable`**. No new dependency, consistent with the reference site's no-dependency rule, and it
avoids `material-icons-extended` — a large artifact that upstream has deprecated.

Directions should therefore assume a **bounded set of roughly eight** icons (back, forward, reload,
stop, home, menu, lock, close), not an open-ended library. An icon a direction needs is an icon
someone has to draw and check at 200% font scale.

### C7 — Edge-to-edge is already on

`enableEdgeToEdge()` (`MainActivity.kt:33`) and `WindowInsets.safeDrawing` (`BrowserScreen.kt:100`).
Directions must show what happens in the status-bar and navigation-bar regions — a design that
assumes an opaque system bar will not survive contact — and `themes.xml` needs a dark counterpart
so the system bars follow the same setting the Compose palette does.

---

## 4. Out of scope

**SiteSkin integrated chrome** — top bar, bottom navigation, quick-action FAB, side menu, and the
placeholder glyphs of §1.5. Parked as `UX-005` in `BACKLOG.md` with its reasoning.

Worth naming honestly: the headline screen of a demo is Bloom Flowers in integrated mode, and that
screen keeps its Unicode glyphs through M6. The order is defensible — integrated chrome already has
a real `NavigationBar`, a real theme projection and a contrast guard, while the browser chrome has
none of the three — but the surface being fixed first is not the surface most demo-visible.
