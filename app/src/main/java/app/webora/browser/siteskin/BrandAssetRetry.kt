package app.webora.browser.siteskin

import app.webora.browser.inspector.BrandAssetStage

/**
 * Whether a later same-origin page start should make the browser ask for the logo again.
 *
 * **`NET-004` fixed this once and left a boundary rather than removing it.** Its diagnosis was that a
 * transient network failure became permanent because *"nothing ever asked again"*: `BrowserScreen`
 * keys the load on the trusted configuration instance, and `BrowserState.forObservedOrigin`
 * deliberately returns the *same* `Integrated` instance for every same-origin page start, so the
 * effect never re-runs. Three attempts moved that boundary from 0 seconds to about 33 — and an
 * inspector reading from the field shows exactly what happens past it:
 *
 * ```
 * Produced by  TRANSPORT_UNAVAILABLE
 * HTTP status  —
 * Load took    32863 ms
 * Attempts     3
 * ```
 *
 * Three full 10-second call timeouts plus 1 s and 2 s of backoff. The burst was spent, and that
 * origin showed a monogram for the rest of the visit with the logo one request away.
 *
 * **A later opportunity, not a bigger burst.** Raising `MAX_ATTEMPTS` moves the boundary a third time
 * and lengthens a stall the user is already waiting through. What the browser lacked is a *natural*
 * retrigger, and it already observes one: a main-frame page start on the same origin. That is
 * user-driven, needs no timer, and cannot spin — no page start, no retry.
 *
 * **Only this stage retries, and the exclusions are `NET-004`'s, unchanged.** A rejection means the
 * server answered and the browser declined, so asking again hammers a site whose logo legitimately
 * 404s and changes nothing. The same bytes decode the same way. An undeclared logo has nothing to
 * request. `TRANSPORT_UNAVAILABLE` is the one outcome that is transient *by definition* — it is what
 * the source reports for an `IOException`, meaning no server answer was obtained at all.
 */
internal fun retriesBrandAsset(stage: BrandAssetStage?): Boolean =
    stage == BrandAssetStage.TRANSPORT_UNAVAILABLE
