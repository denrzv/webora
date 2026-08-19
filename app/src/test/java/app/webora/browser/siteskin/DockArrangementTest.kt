package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class DockArrangementTest {
    @Test
    fun `no nominated ids leaves UX-024's dock untouched`() {
        val arrangement = dockArrangement(model(BLOOM), dockIds = emptyList())

        assertEquals(DockArrangement.BrowserOnly, arrangement)
        assertEquals(listOf(DockSlot.Brand, DockSlot.Tabs, DockSlot.More), arrangement.slots)
        assertTrue(arrangement.projectedIds.isEmpty())
    }

    /** The issue's target shape: `Catalog / Cart / Brand / Account / More`. */
    @Test
    fun `three ids produce five slots around a centred brand hub`() {
        val arrangement = dockArrangement(model(BLOOM), listOf("catalog", "cart", "profile"))

        assertEquals(
            listOf("catalog", "cart", "BRAND", "profile", "MORE"),
            arrangement.slots.map(::describe),
        )
        assertEquals(setOf("catalog", "cart", "profile"), arrangement.projectedIds)
    }

    /**
     * Fewer than three still projects, and Tabs still leaves the dock.
     *
     * A site that nominates one item has expressed the same preference as one that nominates three,
     * and falling back to the browser dock for it would silently ignore a valid request. The brand
     * hub stays between the leading and trailing groups at every size.
     */
    @Test
    fun `one and two ids centre the brand hub between what there is`() {
        assertEquals(
            listOf("catalog", "BRAND", "MORE"),
            dockArrangement(model(BLOOM), listOf("catalog")).slots.map(::describe),
        )
        assertEquals(
            listOf("catalog", "BRAND", "cart", "MORE"),
            dockArrangement(model(BLOOM), listOf("catalog", "cart")).slots.map(::describe),
        )
    }

    /** Order is the site's; membership is the browser's. */
    @Test
    fun `the site chooses the sequence`() {
        assertEquals(
            listOf("profile", "catalog", "BRAND", "cart", "MORE"),
            dockArrangement(model(BLOOM), listOf("profile", "catalog", "cart")).slots.map(::describe),
        )
    }

    /**
     * An id the model cannot supply yields no slot, and an empty result is the browser dock.
     *
     * Core has already dropped ids that named nothing, so this is defence in depth for the case
     * where the app's own 5/5/20 caps removed an item core kept — the divergence `DEVX-001` records
     * as expected never to fire.
     */
    @Test
    fun `ids the model cannot supply are skipped rather than rendered empty`() {
        assertEquals(
            listOf("catalog", "BRAND", "MORE"),
            dockArrangement(model(BLOOM), listOf("catalog", "ghost")).slots.map(::describe),
        )
        assertEquals(DockArrangement.BrowserOnly, dockArrangement(model(BLOOM), listOf("ghost")))
    }

    /** A quick action or menu entry may be promoted; an id is unique across the manifest. */
    @Test
    fun `any collection can supply a projected item`() {
        val arrangement = dockArrangement(model(BLOOM), listOf("call-shop"))

        assertEquals(listOf("call-shop", "BRAND", "MORE"), arrangement.slots.map(::describe))
    }

    /** Brand and More are in the type, so no input can remove them. */
    @Test
    fun `every arrangement keeps the browser's own slots`() {
        val arrangements = listOf(
            emptyList(), listOf("ghost"), listOf("catalog"),
            listOf("catalog", "cart"), listOf("catalog", "cart", "profile"),
        ).map { dockArrangement(model(BLOOM), it) }

        arrangements.forEach { arrangement ->
            assertTrue("brand hub missing from $arrangement", DockSlot.Brand in arrangement.slots)
            assertTrue("more missing from $arrangement", DockSlot.More in arrangement.slots)
            assertEquals("more must be last", DockSlot.More, arrangement.slots.last())
        }
    }

    /**
     * The site can never reach a fourth slot, whatever it nominates.
     *
     * Core bounds the id list to three, so this asserts the app layer does not undo that: even given
     * an over-long list — which only a bug upstream could produce — the arrangement still yields at
     * most three site slots.
     */
    @Test
    fun `the site cannot obtain a fourth slot`() {
        val arrangement = dockArrangement(model(BLOOM), listOf("home", "catalog", "cart", "profile"))
        val siteSlots = arrangement.slots.count { it is DockSlot.Site }

        assertEquals("the cap keeps the site's first three choices", MAX_SITE_SLOTS, siteSlots)
        assertEquals(
            "and it keeps them in the site's order, dropping the tail",
            listOf("home", "catalog", "BRAND", "cart", "MORE"),
            arrangement.slots.map(::describe),
        )
    }

    /**
     * The browser slots carry no site data and the site slot carries no browser command.
     *
     * Reflective so a field added later is covered without anyone remembering — the shape `UX-024`
     * used for `BrowserNavigationAction`. **Static fields are filtered, not only synthetic ones:**
     * the Compose compiler adds `public static final int $stable` to every stable class and does not
     * mark it synthetic, so a synthetic-only filter fails on correct code.
     */
    @Test
    fun `the browser and site slots share no shape`() {
        listOf(DockSlot.Brand, DockSlot.Tabs, DockSlot.More).forEach { slot ->
            val fields = instanceFields(slot.javaClass)
            assertTrue("${slot.javaClass.simpleName} must carry no data, had $fields", fields.isEmpty())
        }

        val siteFields = instanceFields(DockSlot.Site::class.java)
        assertEquals("a site slot carries the trusted item and nothing else", listOf("item"), siteFields)
        assertEquals(SiteSkinItemModel::class.java, DockSlot.Site::class.java.getDeclaredField("item").type)
    }

    /** A generic slot would let a manifest supply what a browser command looks like. */
    @Test
    fun `no slot exposes an icon, a label or a callback`() {
        val forbidden = listOf("icon", "label", "onClick", "action", "command", "tag")
        DockSlot::class.java.permittedSubclasses.orEmpty().forEach { subclass ->
            instanceFields(subclass).forEach { field ->
                assertFalse("$field on ${subclass.simpleName} is a site-supplied browser command", field in forbidden)
            }
        }
    }

    private fun describe(slot: DockSlot): String = when (slot) {
        DockSlot.Brand -> "BRAND"
        DockSlot.Tabs -> "TABS"
        DockSlot.More -> "MORE"
        is DockSlot.Site -> slot.item.id
    }

    private fun model(manifest: String) = SiteSkinChromeModel.from(
        SiteSkinValidator.validate(manifest.byteInputStream(), ORIGIN)
            .let { (it as SiteSkinValidationOutcome.Accepted).configuration },
        "$ORIGIN/catalog",
    )

    private companion object {
        const val ORIGIN = "https://bloom.example"
        const val MAX_SITE_SLOTS = 3

        fun instanceFields(type: Class<*>): List<String> = type.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }

        val BLOOM = """
            {"schemaVersion":"1.0","site":{"id":"bloom","name":"Bloom","homeUrl":"/"},
             "bottomNavigation":[
               {"id":"home","label":"Home","icon":"home","action":{"type":"internal_url","url":"/"}},
               {"id":"catalog","label":"Catalog","icon":"catalog",
                "action":{"type":"internal_url","url":"/catalog"},"match":["/catalog/**"]},
               {"id":"cart","label":"Cart","icon":"shopping_cart",
                "action":{"type":"internal_url","url":"/cart"}},
               {"id":"profile","label":"Account","icon":"person",
                "action":{"type":"internal_url","url":"/account"}}],
             "quickActions":[
               {"id":"call-shop","label":"Call","icon":"call","action":{"type":"phone","value":"+10000000000"}}]}
        """.trimIndent()
    }
}
