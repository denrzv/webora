package app.webora.browser.siteskin

import app.webora.browser.inspector.SITESKIN_INSPECTOR_AVAILABLE
import dev.siteskin.core.SiteSkinLimits
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSkinChromeModelTest {

    /**
     * Both answers, driven explicitly.
     *
     * Only `testDebugUnitTest` exists, so a test that read the constant could never see the release
     * answer — and an implementation returning `BrowserMenuCommand.entries` unconditionally would
     * pass it. The `false` case is the one that catches that, and it is why the function takes a
     * parameter instead of reading the constant inline.
     */
    @Test fun `a variant without a panel is not offered the inspector`() {
        assertTrue(browserMenuCommands(inspectorAvailable = true).contains(BrowserMenuCommand.INSPECTOR))
        assertFalse(browserMenuCommands(inspectorAvailable = false).contains(BrowserMenuCommand.INSPECTOR))
    }

    /** The default argument is wired to the variant constant, not to a second copy of the decision. */
    @Test fun `the default reads this variant's constant`() {
        assertEquals(
            browserMenuCommands(inspectorAvailable = SITESKIN_INSPECTOR_AVAILABLE),
            browserMenuCommands(),
        )
    }

    /** The closed browser section keeps its members in every variant. A floor, so it cannot shrink. */
    @Test fun `browser menu always offers page information and settings`() {
        listOf(true, false).forEach { available ->
            val commands = browserMenuCommands(inspectorAvailable = available)

            assertTrue(commands.contains(BrowserMenuCommand.PAGE_INFORMATION))
            assertTrue(commands.contains(BrowserMenuCommand.SETTINGS))
            assertEquals(BrowserMenuCommand.PAGE_INFORMATION, commands.first())
        }
    }

    @Test fun `trusted collections preserve order and enforce surface limits`() {
        val model = SiteSkinChromeModel.from(configuration(6, 6, 21), "https://shop.example/item")

        assertEquals(listOf("nav-0", "nav-1", "nav-2", "nav-3", "nav-4"), model.bottomNavigation.map { it.id })
        assertEquals(5, model.quickActions.size)
        assertEquals(20, model.siteMenu.size)
    }

    @Test fun `active navigation uses browser page and no match selects nothing`() {
        val configuration = configuration(2, 0, 0)

        val active = SiteSkinChromeModel.from(configuration, "https://shop.example/item")
        val unmatched = SiteSkinChromeModel.from(configuration, "https://shop.example/elsewhere")

        assertTrue(active.bottomNavigation.single { it.id == "nav-1" }.isActive)
        assertFalse(active.bottomNavigation.single { it.id == "nav-0" }.isActive)
        assertFalse(unmatched.bottomNavigation.any(SiteSkinItemModel::isActive))
    }

    @Test fun `empty optional collections stay empty`() {
        val model = SiteSkinChromeModel.from(configuration(0, 0, 0), "https://shop.example/")

        assertTrue(model.bottomNavigation.isEmpty())
        assertTrue(model.quickActions.isEmpty())
        assertTrue(model.siteMenu.isEmpty())
    }

    @Test fun `selection retains trusted item and browser menu remains immutable`() {
        val configuration = configuration(1, 1, 1)
        val model = SiteSkinChromeModel.from(configuration, "https://shop.example/")

        assertSame(configuration.quickActions?.single(), model.quickActions.single().item)
        // The claim is that the *manifest* cannot change this section, which is what "immutable"
        // meant. Two very different configurations must produce the same browser menu. Asserting a
        // hardcoded pair would now also be asserting which variant is running, which is a different
        // question and not the one this test exists to ask.
        assertEquals(
            SiteSkinChromeModel.from(configuration(6, 6, 21), "https://shop.example/").browserMenu,
            model.browserMenu,
        )
        assertEquals(browserMenuCommands(), model.browserMenu)
    }

    @Test
    fun `manifest text reaching the accessibility tree is bounded`() {
        // The model's label is both the visible label and the contentDescription, so this bound is
        // what stops a hostile manifest from narrating an unbounded string to a TalkBack user
        // regardless of how the pixels were clipped.
        val hostile = "Secure connection to yourbank.example ".repeat(20)

        assertTrue(accessibleLabel(hostile).length <= SiteSkinLimits.MAX_LABEL_LENGTH)
        assertEquals(hostile.take(SiteSkinLimits.MAX_LABEL_LENGTH), accessibleLabel(hostile))
    }

    @Test
    fun `labels within the bound are untouched`() {
        assertEquals("Cart", accessibleLabel("Cart"))
    }

    private fun configuration(nav: Int, quick: Int, menu: Int) = SiteSkinValidator.validate(
        manifest(nav, quick, menu).byteInputStream(),
        "https://shop.example",
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private fun manifest(nav: Int, quick: Int, menu: Int): String = """
        {
          "schemaVersion":"1.0",
          "site":{"id":"shop","name":"Shop"},
          "bottomNavigation":${items("nav", nav, true)},
          "quickActions":${items("quick", quick, false)},
          "menu":${items("menu", menu, false)}
        }
    """.trimIndent()

    private fun items(prefix: String, count: Int, matches: Boolean): String = (0 until count).joinToString(
        prefix = "[",
        postfix = "]",
    ) { index ->
        val match = if (matches && index == 1) ",\"match\":[\"/item\"]" else ""
        """{"id":"$prefix-$index","label":"Label $index","icon":"home","action":{"type":"refresh"}$match}"""
    }
}
