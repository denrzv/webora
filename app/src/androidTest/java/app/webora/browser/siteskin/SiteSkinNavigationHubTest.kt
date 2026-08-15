package app.webora.browser.siteskin

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.webora.browser.design.WeboraTheme
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.model.NavigationItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SiteSkinNavigationHubTest {
    @get:Rule val compose = createComposeRule()

    @Test fun routeStateDoesNotLeakOntoActionsOrBrowserCommands() {
        val selected = mutableListOf<NavigationItem>()
        val configuration = SiteSkinValidator.validate(MANIFEST.byteInputStream(), SITE_URL)
            .let { (it as SiteSkinValidationOutcome.Accepted).configuration }
        val model = SiteSkinChromeModel.from(configuration, "$SITE_URL/catalog")
        compose.setContent {
            WeboraTheme {
                SiteSkinMenu(model, { selected += it }, {})
            }
        }

        compose.onNodeWithText("Site navigation").assertIsDisplayed()
        compose.onNodeWithText("Quick actions").assertIsDisplayed()
        compose.onNodeWithText("Webora controls").assertIsDisplayed()
        compose.onNodeWithText("Catalog").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected"),
        ).performClick()
        compose.onNodeWithText("Call").assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription),
        )
        compose.onNodeWithText("Settings").assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription),
        )
        assertEquals("catalog", selected.single().id)
    }

    private companion object {
        const val SITE_URL = "https://shop.example"
        val MANIFEST = """
            {"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},
            "bottomNavigation":[
              {"id":"home","label":"Home","action":{"type":"home"},"match":"/"},
              {"id":"catalog","label":"Catalog","action":{"type":"refresh"},"match":"/catalog"}],
            "quickActions":[{"id":"call","label":"Call","action":{"type":"refresh"}}]}
        """.trimIndent()
    }
}
