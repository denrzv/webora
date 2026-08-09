package app.webora.browser.siteskin

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SiteSkinChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bottomNavigationShowsFiveBoundedItemsAndSelectedSemantics() {
        val model = model(nav = 6, quick = 0, menu = 0, page = "https://shop.example/item")
        compose.setContent { SiteSkinBottomNavigation(model.bottomNavigation, {}) }

        compose.onNodeWithTag(SITESKIN_BOTTOM_NAV_TAG).assertIsDisplayed()
        compose.onAllNodesWithText("Label 5").assertCountEquals(0)
        compose.onAllNodesWithText("⌂").assertCountEquals(0)
        compose.onNodeWithContentDescription("Label 1").assertIsSelected()
    }

    @Test fun quickActionCollapsesAfterTypedSelection() {
        val model = model(nav = 0, quick = 2, menu = 0)
        var selected = ""
        compose.setContent { SiteSkinQuickActions(model.quickActions, { selected = it.id }) }

        compose.onNodeWithContentDescription("Quick actions").performClick()
        compose.onNodeWithText("Label 1").performClick()

        assertEquals("quick-1", selected)
        compose.onAllNodesWithText("Label 0").assertCountEquals(0)
    }

    @Test fun menuAlwaysSeparatesBrowserOwnedCommands() {
        val model = model(nav = 0, quick = 0, menu = 2)
        compose.setContent { SiteSkinMenu(model, {}, {}) }

        compose.onNodeWithText("Site navigation").assertIsDisplayed()
        compose.onNodeWithText("Webora controls").assertIsDisplayed()
        compose.onNodeWithText("Page information").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    private fun model(
        nav: Int,
        quick: Int,
        menu: Int,
        page: String = "https://shop.example/none",
    ): SiteSkinChromeModel = SiteSkinChromeModel.from(configuration(nav, quick, menu), page)

    private fun configuration(nav: Int, quick: Int, menu: Int) = SiteSkinValidator.validate(
        manifest(nav, quick, menu).byteInputStream(),
        "https://shop.example",
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private fun manifest(nav: Int, quick: Int, menu: Int): String = """
        {"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},
        "bottomNavigation":${items("nav", nav, true)},
        "quickActions":${items("quick", quick, false)},
        "menu":${items("menu", menu, false)}}
    """.trimIndent()

    private fun items(prefix: String, count: Int, matches: Boolean): String = (0 until count).joinToString(
        prefix = "[",
        postfix = "]",
    ) { index ->
        val match = if (matches && index == 1) ",\"match\":[\"/item\"]" else ""
        """{"id":"$prefix-$index","label":"Label $index","icon":"home","action":{"type":"refresh"}$match}"""
    }
}
