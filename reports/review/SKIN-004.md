# SKIN-004: Review

Status: RESOLVED

## Scope reviewed

Commit `e74b33a` and the SKIN-004 PRD, research, plan, tasklist, runtime state/origin attribution,
consent persistence, live Compose wiring, Android capability adapters, and tests.

## Findings

### 1. Site menu bypasses the complete closed dispatcher; external HTTPS lacks confirmation

The bottom-navigation/quick-action callback handles all `ResolvedAction` variants, but the menu
callback handles only internal navigation and refresh. Separately, `NavigateExternal` invokes the
Android external-URL callback immediately rather than presenting a browser-owned confirmation.
This makes identical trusted actions behave differently by surface and violates the planned effect
boundary. Resolve in `TASK-FIX-1` by routing every SiteSkin item through one dispatcher and adding a
browser-owned confirmation before external HTTPS leaves Webora.

## Architecture and security assessment

- Core remains the only manifest validation/action-resolution boundary.
- Discovery results now carry canonical origin and generation; runtime publication independently
  checks both, so cancellation is not relied on for security.
- Consent decisions are full-origin keyed and only the closed durable Allow/Never values are stored.
- Cross-origin page observation drops integrated mode before discovery begins, while exact same
  origin retains it.
- Browser-owned domain/TLS presentation is derived from the active `SiteOrigin`, not manifest data.
- The current `BrowserScreen` complexity suppressions are localized technical debt rather than a
  trust-boundary bypass, but future browser-runtime work should extract an orchestration holder
  instead of expanding this function.

## Test assessment

JVM tests cover exact-origin and generation rejection, consent persistence isolation, discovery
attribution, and browser-mode retain/drop behavior. Both security comparisons have demonstrated
negative controls. Compose instrumentation covers the browser-owned three-choice consent surface
and compiles without a device. Public demo-origin instrumentation remains unavailable until the demo
tickets and a device environment exist.

## Not findings

- **SharedPreferences instead of Preferences DataStore:** not a defect. The repository already uses
  synchronous SharedPreferences for a small Boolean store; the consent values are bounded, local,
  and atomically replaced. PRIV-001 can add management APIs without changing their exact-origin key.
- **Same-origin page starts retain active chrome while discovery reruns:** intentional. The already
  consented configuration remains bound to the same authority; an origin change still drops it
  synchronously.
- **Registrable domain in consent copy:** display only. Persistence and activation use the complete
  canonical `SiteOrigin` even when multiple origins share that human-readable label.
- **No runtime instrumentation screenshot:** environment limitation, not missing compile coverage;
  no Android device is connected and managed cloud must not provision a software-only emulator.
