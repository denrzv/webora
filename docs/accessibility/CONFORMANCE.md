# Accessibility conformance — Webora Browser

Implemented by `A11Y-001`. Every row names the code that enforces the guarantee and the test that
would fail if it stopped being true.

The **Enforced by** column distinguishes two things this document refuses to blur:

- **Gate** — proven by `bash scripts/pre-commit-check.sh`, which is JVM-only. If it regresses, the
  build goes red.
- **Evidence** — an instrumented test that needs a device. It is recorded in QA and re-run before a
  release. It is not a gate, and calling it one would misrepresent what the build actually checks.

Anything with no test at all is listed in *Known gaps* rather than left implied.

## Target size — WCAG 2.2 §2.5.8 (AA)

| Guarantee | Code | Test | Enforced by |
|---|---|---|---|
| Every browser-owned control is ≥ 48 dp on both axes | `browser/BrowserAccessibility.kt` — `MINIMUM_TOUCH_TARGET`, `Modifier.browserTouchTarget()`, `WeboraButton`, `WeboraTextButton` | `BrowserAccessibilityTest` | Gate |
| The minimum cannot be bypassed at a call site | `BrowserSurfaceConventionsTest` forbids importing Material 3 `Button`/`TextButton` in browser-owned Compose sources | `browser controls use the touch target wrapper` | Gate |
| The wrapper actually raises something | Asserted against `ButtonDefaults.MinHeight` (40 dp), so the wrapper fails the day it becomes decoration | `the browser minimum actually raises the Material default` | Gate |

Material 3 gives `Button` a 40 dp minimum height via `defaultMinSize` and — unlike `Switch`,
`Checkbox`, and `IconButton` — never applies `minimumInteractiveComponentSize()`. That is why the
raw component is out of bounds in browser-owned UI rather than merely discouraged.

## Resize text — WCAG 2.2 §1.4.4 (AA)

| Guarantee | Code | Test | Enforced by |
|---|---|---|---|
| The regular-chrome control row wraps instead of clipping | `BrowserScreen.AddressBar` — `FlowRow` | `BrowserFontScaleTest` | Evidence |
| The three consent choices stay reachable | `BrowserScreen.SiteSkinConsentDialog` — `FlowRow` | `consentChoicesStayReachableAtDoubleFontScale` | Evidence |
| Onboarding and privacy settings scroll | `OnboardingScreen`, `PrivacySettingsScreen` — `verticalScroll` | `BrowserFontScaleTest` | Evidence |
| SiteSkin labels truncate without overlapping | `SiteSkinChrome.BoundedLabel`, `SiteSkinTopBar` minimum (not fixed) height | `SKIN-002` / `SKIN-003` instrumented tests | Evidence |

Wrapping, not horizontal scrolling: a scrollable control row hides controls behind a gesture, which
trades one accessibility problem for another.

## Use of colour — WCAG 2.2 §1.4.1 (A)

| Guarantee | Code | Test | Enforced by |
|---|---|---|---|
| Transport security is stated as text, never colour alone | `strings.xml` `security_secure` / `security_not_secure`; `BrowserSecurityIdentity`, `SiteSkinTopBar.SecurityIdentity` | `SiteSkinTopBarTest` | Evidence |
| Navigation selection has a state description | `SiteSkinChrome.SiteSkinBottomNavigation` — `stateDescription` | `SiteSkinChromeTest` | Evidence |
| The SiteSkin global toggle has a state description | `PrivacySettingsScreen` | `PrivacySettingsScreenTest` | Evidence |

`NavigationBarItem` already exposes selection through `selectable(role = Role.Tab)`; the state
description makes it explicit rather than repairing an absence.

## Contrast — WCAG 2.2 §1.4.3, §1.4.11 (AA)

| Guarantee | Code | Test | Enforced by |
|---|---|---|---|
| Body pairs ≥ 4.5:1 and UI pairs ≥ 3:1, in **both** projections | `siteskin/SiteSkinTheme.kt` — `guardContainer` | `adversarial colours meet contrast in both projections` — 10 curated failure modes + a 120-case seeded sweep | Gate |
| The guard is load-bearing | — | **Negative control**: `guardContainer` returning its input unchanged fails 5 tests | Gate |
| The system dark theme selects the projection | `SiteSkinTheme.scheme(darkTheme)`, `BrowserScreen` — `isSystemInDarkTheme()` | `the system theme selects the projection and nothing else does` | Gate |

Core normalizes remote colours (`CORE-004`); the app parses only canonical trusted values and runs
the guard again over every newly formed pair. A manifest cannot influence which projection is used.

## Name, role, value — WCAG 2.2 §4.1.2 (A)

| Guarantee | Code | Test | Enforced by |
|---|---|---|---|
| No user-visible or accessible string is authored inline | `BrowserSurfaceConventionsTest` literal and named-argument rules; `SuggestedSite` and `OnboardingPage` hold `@StringRes` ids so a literal is a compile error | `browser copy resolves from resources`, `accessible names resolve from resources` | Gate |
| The privacy `Switch` has a name | `PrivacySettingsScreen` — `contentDescription` | `PrivacySettingsScreenTest` | Evidence |
| Reset actions are distinguishable per origin | `PrivacySettingsScreen` — the origin is in the button's visible label | `PrivacySettingsScreenTest` | Evidence |
| Decorative icons carry no accessible presence | `SiteSkinChrome.SiteSkinIcon`, `SiteSkinTopBar.BrandLogo` — `clearAndSetSemantics { }` | `SiteSkinTopBarTest`, `SiteSkinChromeTest` | Evidence |
| The error page title is a heading | `BrowserScreen.BrowserErrorPage` — `semantics { heading() }` | — | *Known gap* |

The origin is in the reset button's **visible label** rather than a `contentDescription` override
because Compose merges a parent description with its child text instead of replacing it; the
override would have produced a doubled announcement.

## Status messages — WCAG 2.2 §4.1.3 (AA)

| Guarantee | Code | Test | Enforced by |
|---|---|---|---|
| Load progress, completion, and failure are announced | `BrowserAccessibility.browserAnnouncement`, `BrowserScreen.BrowserStatusRegion` | `BrowserAccessibilityTest` (6 cases) | Gate |
| Failure interrupts; progress does not | `BrowserAnnouncement.liveRegionMode()` | `only failure interrupts` | Gate |
| Nothing is announced for a page that does not exist | `browserAnnouncement` returns `null` for Home and for an uncommitted page | `home announces nothing`, `a browser with no committed page announces nothing` | Gate |

The live region is a persistent 4 dp node rather than the progress indicator itself. Hanging it on
the indicator would destroy the node the moment loading finished, and a destroyed node cannot
announce that it finished.

## Accessibility as a security surface

Assistive technology reads the semantics tree, not the pixels. Every bound the browser places on
manifest content visually needs a counterpart there, or a manifest regains through semantics exactly
the impersonation surface `ADR-006` and `HARDEN-002` closed off visually.

| Guarantee | Code | Test | Enforced by |
|---|---|---|---|
| Accessible security identity is derived only from the committed `SiteOrigin` | `BrowserScreen.BrowserSecurityIdentity`, `SiteSkinTopBar.SecurityIdentity`, both fed by `securityPresentation(BrowserMode)` | `SecurityPresentationTest` | Gate |
| Regular and integrated mode publish the *same* node | Shared `security_description` string, one per mode | `SiteSkinTopBarTest` | Evidence |
| No origin yields no node, not a blank one | `securityPresentation` returns `null` | `SecurityPresentationTest` | Gate |
| Manifest text is bounded before reaching the semantics tree | `SiteSkinChromeModel.accessibleLabel`, reading `SiteSkinLimits.MAX_LABEL_LENGTH` from core so the two cannot drift | `manifest text reaching the accessibility tree is bounded` — **negative control**: removing the bound fails it | Gate |
| Manifest text is never concatenated with browser security wording | Structural: `security_description` takes only browser strings and `SiteOrigin.registrableDomain` | `SiteSkinTopBarTest` asserts the exact description | Evidence |

## Known gaps

- **Web page content is out of scope.** Accessibility inside the `WebView` is renderer-owned.
  Webora hardens the host and injects nothing; `addJavascriptInterface` remains forbidden.
- **`BrowserFontScaleTest` has not been executed.** It compiles, but no emulator or device was
  available when `A11Y-001` landed. It must run before release; see `reports/qa/A11Y-001.md`.
- **The error-page heading has no test.** The `heading()` semantic is asserted by neither a JVM nor
  an instrumented test today.
- **No end-to-end TalkBack run.** The semantics are tested; the resulting spoken output has not been
  verified against a real screen reader.
- **No in-app font-size or high-contrast preference.** Webora follows the platform settings by
  design; an in-app control is separate product scope.
