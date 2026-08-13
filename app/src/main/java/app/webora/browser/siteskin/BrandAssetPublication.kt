package app.webora.browser.siteskin

import app.webora.browser.browser.BrowserMode
import dev.siteskin.core.model.SiteSkinConfiguration

/**
 * Whether a finished brand-asset load may still publish into the chrome.
 *
 * `NET-003`'s "superseded work cannot publish" rule, as a function a JVM test can drive. It lived
 * inside `BrowserScreen`'s `LaunchedEffect` — which is to say it lived where the gate cannot reach
 * it, while the one class that did have a test for it, `BrandAssetCoordinator`, was wired into
 * nothing. That is the repository's usual thin-wrapper-over-pure-function split applied the wrong way
 * round, and `NET-004` straightened it while it was already reading this path.
 *
 * The comparison is **identity**. A [SiteSkinConfiguration] can only be obtained from the validator,
 * so two instances are two separate acceptances — of possibly different bytes, for possibly different
 * origins — and a structural comparison would let a load started for one publish into the other.
 * `BrowserState.forObservedOrigin` deliberately returns the *same* `Integrated` instance while the
 * origin holds, so identity is exactly as stable as the activation is.
 *
 * Today `===` and `==` behave identically here, because [SiteSkinConfiguration] is a plain class with
 * no `equals` — a trusted configuration is an identity rather than a value, which is the same reason
 * `SiteSkinTraceNeutralityTest` compares descriptions of decisions instead of the configurations
 * themselves. `===` is written anyway, and said out loud here, because the day that type becomes a
 * `data class` is the day the difference starts mattering and nothing else would notice.
 */
internal fun publishesBrandAsset(mode: BrowserMode, loadedFor: SiteSkinConfiguration): Boolean =
    (mode as? BrowserMode.Integrated)?.configuration === loadedFor
