package dev.siteskin.core.nav

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.model.NavigationItem
import dev.siteskin.core.model.SiteSkinConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Drives the **published** reference manifest through [NavMatcher] for the routes
 * `denrzv/bloom-flowers` actually serves.
 *
 * `NavMatcherTest` asserts the glob grammar with patterns written for the purpose, and
 * `OriginCorpusTest.theBloomFlowersManifestResolvesEndToEnd` asserts that every URL in this
 * manifest resolves inside its origin. Neither one asks the question a site owner asks: *given my
 * manifest and my site, which tab lights up?* That gap is why `bottomNavigation[0]` shipped with no
 * `match` array — a manifest can be valid, origin-bound and entirely well-formed while describing
 * its own site incorrectly, and until this test existed nothing in the corpus could tell.
 *
 * The routes come from the deployment's directory layout, which is the only static layout that
 * resolves on every host examined: `catalog/index.html` is served at `/catalog/`, and a request for
 * `/catalog` is redirected there. So both spellings are asserted — the URL the browser observes
 * before the redirect and the one it commits afterwards, since `NavMatcher` sees whichever the page
 * load ends on.
 */
class ReferenceIntegrationNavTest {

    private val specDir = File(
        requireNotNull(System.getProperty("siteskin.spec.dir")) {
            "siteskin.spec.dir is not set — see siteskin-core/build.gradle.kts"
        },
    )

    /** The fixture is read, never transcribed: a copy here would be a sixth pinned copy. */
    private val manifest = specDir.resolve("fixtures/valid/bloom-flowers.json")

    private val origin = "https://bloomflowers.example"

    /**
     * Every route the reference site serves, against the item the site owner expects to see active.
     *
     * `/cart` with no trailing slash is the interesting one: the manifest declares only the
     * double-star pattern under `/cart`, and a double star matches *zero or more* whole segments,
     * so a trailing one is satisfied by an already-complete path. It reads like an off-by-one and
     * is the specified behaviour.
     *
     * (Written without the literal glob: Kotlin nests block comments, so a slash followed by two
     * stars opens one inside this KDoc and the file stops compiling.)
     */
    private val routes = listOf(
        "/" to "home",
        "/catalog" to "catalog",
        "/catalog/" to "catalog",
        "/catalog/roses" to "catalog",
        "/cart" to "cart",
        "/cart/" to "cart",
        "/account" to "account",
        "/account/orders" to "account",
    )

    @Test
    fun theReferenceManifestSelectsTheTabForEveryRouteTheSiteServes() {
        val navigation = bottomNavigation()

        routes.forEach { (path, expectedId) ->
            val active = NavMatcher.activeItem(navigation, origin + path)
            assertEquals(
                "$path must make `$expectedId` the active item of the reference integration",
                expectedId,
                active?.id,
            )
        }
    }

    /**
     * Without this the test above could pass by making everything match everything — the failure
     * mode `SPEC.md` §7.1 clause 4 forbids a browser from shipping.
     */
    @Test
    fun aPathTheManifestDoesNotDescribeSelectsNothing() {
        val navigation = bottomNavigation()

        listOf("/about", "/catalogue", "/carts", "/accounts").forEach { path ->
            assertNull(
                "$path is not described by the reference manifest and must leave no item active",
                NavMatcher.activeItem(navigation, origin + path),
            )
        }
    }

    /**
     * Guards the guard. If the fixture's navigation were emptied or renamed, every assertion above
     * would still hold vacuously for the negative test and fail loudly for the positive one — but
     * a *partial* loss (three items instead of four) would quietly narrow what is covered.
     */
    @Test
    fun theRouteTableCoversEveryNavigationItemInTheManifest() {
        val navigation = bottomNavigation()
        val expected = routes.map { (_, id) -> id }.toSet()

        assertEquals(
            "every bottomNavigation item must appear in the route table",
            navigation.map(NavigationItem::id).toSet(),
            expected,
        )
        assertTrue("the reference manifest declares no navigation", navigation.isNotEmpty())
    }

    @Test
    fun theReferenceManifestExercisesTheFinalSemanticShowcase() {
        val configuration = configuration()
        val presentation = requireNotNull(configuration.bottomNavigation).map { item ->
            "${item.id}:${item.label}:${item.icon}:${item.action.type}"
        } + requireNotNull(configuration.quickActions).map { item ->
            "${item.id}:${item.label}:${item.icon}:${item.action.type}"
        }

        assertEquals(
            listOf(
                "home:Home:home:internal_url",
                "catalog:Flowers:flower:internal_url",
                "cart:Cart:shopping_cart:internal_url",
                "account:Account:person:internal_url",
                "call-shop:Call:call:phone",
            ),
            presentation,
        )
    }

    private fun bottomNavigation(): List<NavigationItem> = requireNotNull(configuration().bottomNavigation) {
        "the reference manifest declares bottomNavigation"
    }

    private fun configuration(): SiteSkinConfiguration {
        val outcome = manifest.inputStream().use { SiteSkinValidator.validate(it, origin) }
        assertTrue(
            "the reference manifest must validate against $origin, got $outcome",
            outcome is SiteSkinValidationOutcome.Accepted,
        )
        return (outcome as SiteSkinValidationOutcome.Accepted).configuration
    }
}
