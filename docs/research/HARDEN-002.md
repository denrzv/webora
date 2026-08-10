# HARDEN-002: Research
Status: RESEARCH_READY

## Question
Which ADR-006 and ADR-011 brand-impersonation controls are already enforced, and what narrow gaps
remain in provenance, accessibility semantics, consent identity, and regression evidence.

## Origins involved
- The active page, manifest response, trusted configuration, consent candidate, and stored decision
  all use canonical `SiteOrigin` equality over scheme, complete ASCII host, and effective port.
- The registrable domain is intentionally a compact browser-owned top-bar display value, not an
  identity or trust key. Sibling origins can share it, and private suffix rules can retain more
  labels. It must never be read back from manifest branding.
- First-use consent authorizes one full origin. Showing only the registrable domain in that prompt
  hides the subdomain and non-default port even though the persisted grant distinguishes them. The
  prompt should render a browser-formatted canonical origin so the visible choice matches its key.
- Discovery generation and current-origin checks prevent stale or cross-origin candidates from
  activating. The top bar derives security presentation from the current `BrowserMode` origin.

## Manifest-controlled surface
A trusted manifest may influence the brand title/subtitle, corrected theme colours, a same-origin
bounded bitmap or monogram fallback, and closed navigation/action labels. Those values may sit near
security identity but must remain presentation-only. The manifest cannot provide domain/TLS text,
accessibility descriptions for security chrome, consent decisions/copy, sizes outside browser caps,
or an alternative interaction target that replaces browser controls.

## Browser-owned remainder
The full origin, registrable-domain calculation, transport state, identity semantics and typography,
logo bounds/decorative semantics, contrast correction, consent copy/actions, exact-origin storage,
activation generation, overflow/security/settings reachability, and regular-mode fallback remain
browser-owned. No manifest field can hide them, and unknown `toolbar.showDomain` is already outside
the schema/trusted model.

## Relevant code and evidence
| Path | Finding |
|---|---|
| `docs/adr/ADR-006-browser-owned-security-chrome.md` | Requires always-visible registrable domain/TLS, bounded logo beside it, corrected contrast, and reachable browser controls. |
| `docs/adr/ADR-011-first-use-consent.md` | Requires pre-branding Allow/Not now/Never consent keyed to full origin and a browser-owned, non-editable origin string. |
| `app/.../browser/SecurityPresentation.kt` | Purely derives domain/TLS from current `BrowserMode`; existing JVM tests cover integrated and regular provenance. |
| `app/.../siteskin/SiteSkinTopBarModel.kt` | Accepts security identity as a separate required value; hostile brand text cannot structurally replace it. |
| `app/.../siteskin/SiteSkinTopBar.kt` | Always renders identity after bounded brand content with browser-authored content description. Bitmap content is decorative, but monogram `Text` is not explicitly cleared from semantics. |
| `app/.../siteskin/SiteSkinTheme.kt` | Uses validator-corrected colours and selects readable foregrounds; focused JVM tests cover contrast. |
| `app/.../siteskin/SiteSkinRuntime.kt` | Rejects stale generation/origin/configuration mismatches and returns Ask before activation when no decision exists. |
| `app/.../siteskin/SiteConsentStore.kt` | Persists only Allow/Never using encoded canonical origin, with tests for canonicalization and isolation. |
| `app/.../browser/BrowserScreen.kt` | Keeps regular mode while consent is pending and rechecks current origin/generation on Allow; currently passes only `registrableDomain` to the dialog. |
| `app/src/androidTest/.../SiteSkinTopBarTest.kt` | Pins visible security semantics and 40dp logo width, but does not prove logo semantics are decorative or hostile branding cannot merge/replace identity. |
| `app/src/androidTest/.../SiteSkinConsentDialogTest.kt` | Exercises Never only; it does not assert exact visible origin, explanatory copy, or all three decisions. |

## Prior art
- `SKIN-001`, `SKIN-002`, and `SKIN-004` implemented theme correction, security top chrome, and
  origin/generation-bound consent activation. HARDEN-002 should reinforce those seams, not create a
  parallel identity model.
- HARDEN-001 pins Unicode/punycode canonicalization and mixed-script presentation signals. A
  mixed-script flag is not an ADR-006 top-bar requirement and must not become a trust decision here.
- `PRIV-001` owns reversible settings and global/per-site toggles; this ticket retains the existing
  persisted Never decision without building settings UI early.

## Risks
- Using registrable domain as consent identity can visually collapse different grants; display the
  canonical origin while retaining registrable domain in compact active chrome.
- Clearing logo semantics at the wrong node may leave descendant text merged into the top bar; use
  `clearAndSetSemantics` on the bounded logo container and assert no accessible logo node exists.
- A test that finds `example.co.uk` anywhere may pass because hostile brand text repeats it. Pin the
  dedicated browser-owned security test tag and full content description.
- Instrumentation runtime may be unavailable in managed cloud. Compile its APK/tests and report the
  runtime limitation; do not add Robolectric.
- Changing consent identity copy is perceptible UI and requires a screenshot if a runnable device
  is available. Do not provision a software-only emulator without KVM.

## Open questions
None. The narrow implementation is to render the canonical consent origin, explicitly clear brand
logo semantics, strengthen pure and Compose negative controls, and document/validate the existing
ADR contract. No protocol or core change is required.

## Question
What the plan needs decided before it can commit to a trust boundary and a file list.

## Origins involved
- serving origin(s)
- asset origin(s), and why they are same-origin

## Manifest-controlled surface
What a website can influence if this ships as scoped.

## Browser-owned remainder
What must stay browser-controlled, and the affordance that enforces it.

## Relevant code
| Path | Why it matters |
|---|---|

## Prior art
ADRs, spec sections, fixtures and tickets that already decided part of this.

## Risks
- risk → the plan's obligation in response

## Open questions
Carried into `/plan` as explicit unknowns, not silently resolved here.
