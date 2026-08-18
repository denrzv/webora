package app.webora.browser.siteskin

import dev.siteskin.core.model.HubPresentation
import dev.siteskin.core.model.SiteSkinConfiguration

/**
 * The hub surface the browser will actually compose.
 *
 * Deliberately a different type from core's [HubPresentation]. That one is what a *site asked for*
 * and is closed by the validator; this one is what the *browser decided* and is closed by the set
 * of components that exist. Collapsing them into one enum would make the two questions
 * indistinguishable at every call site, and the interesting bug is a surface being composed because
 * a manifest named it rather than because the browser chose it.
 */
internal enum class HubSurface {
    /** [SiteSkinHubDrawer] — a start-side list presenting every group. */
    DRAWER,

    /** The compact radial arrangement of quick actions. */
    BOUQUET,
}

/**
 * The one browser-owned mapping from a site's hint to the surface Webora composes.
 *
 * A hint is a preference and not an instruction, which is why this is a function and not a cast:
 * the browser stays free to compose something other than what was named. Today only [AUTO] is
 * redirected, but a future device, locale or accessibility condition overriding an explicit
 * `BOUQUET` belongs here — one place a reviewer can read to learn what a manifest can and cannot
 * cause — and not at a call site.
 *
 * `AUTO` resolves to [HubSurface.DRAWER] because the drawer is the only surface that can guarantee
 * `SPEC.md` §8's twenty permitted `menu` entries are all reachable. A site that says nothing gets
 * the presentation that cannot silently hide its own navigation.
 *
 * Pure and total. It reads only the closed enum, so no site-authored string, colour, count, label
 * or action can influence it; it takes no `Context`, no configuration object and no callback.
 */
internal fun resolveHubPresentation(hint: HubPresentation?): HubSurface = when (hint) {
    HubPresentation.BOUQUET -> HubSurface.BOUQUET
    HubPresentation.DRAWER -> HubSurface.DRAWER
    // An absent hint is `AUTO`, which core's own accessor already collapses. The `null` arm is here
    // so a caller holding a nullable value cannot be tempted to write its own `?: AUTO` and put a
    // second copy of that default beside this one.
    HubPresentation.AUTO, null -> HubSurface.DRAWER
}

/** The surface for a trusted configuration, reading core's collapsing accessor. */
internal fun SiteSkinConfiguration.hubSurface(): HubSurface = resolveHubPresentation(hubPresentation)
