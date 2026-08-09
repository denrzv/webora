package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSkinChromeModelTest {
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
        assertEquals(
            listOf(BrowserMenuCommand.PAGE_INFORMATION, BrowserMenuCommand.SETTINGS),
            model.browserMenu,
        )
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
