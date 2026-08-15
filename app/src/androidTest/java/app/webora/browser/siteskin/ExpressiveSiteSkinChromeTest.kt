package app.webora.browser.siteskin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpressiveSiteSkinChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactLargeTextKeepsRequiredContentAndTargets() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ChromeFixture(presentation(darkTheme = false, reducedMotion = false))
            }
        }

        compose.onNodeWithTag(HEADER_CONTENT_TAG).assertIsDisplayed()
        compose.onNodeWithTag(EXPRESSIVE_HEADER_TAG).assertIsDisplayed()
        compose.onNodeWithTag(EXPRESSIVE_DOCK_TAG).assertIsDisplayed()
        val parent = compose.onNodeWithTag(CHROME_FIXTURE_TAG).fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag(EXPRESSIVE_DOCK_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("dock must float inside its parent: parent=$parent dock=$dock", dock.left > parent.left)
        assertTrue("dock must retain right separation: parent=$parent dock=$dock", dock.right < parent.right)
        COMMANDS.forEach { command ->
            compose.onNodeWithContentDescription(command).assertIsDisplayed()
                .assertHeightIsAtLeast(EXPRESSIVE_MINIMUM_TARGET)
        }
    }

    @Test fun themeAndMotionPoliciesKeepTheSameOwnedSlots() {
        listOf(false to false, true to false, true to true).forEach { (dark, reduced) ->
            compose.setContent { ChromeFixture(presentation(dark, reduced)) }

            val header = compose.onNodeWithTag(EXPRESSIVE_HEADER_TAG).fetchSemanticsNode().boundsInRoot
            val dock = compose.onNodeWithTag(EXPRESSIVE_DOCK_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue("header must precede dock for dark=$dark reduced=$reduced", header.bottom <= dock.top)
            COMMANDS.forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ChromeFixture(presentation: ExpressiveSiteSkinPresentation) {
        Column(Modifier.width(320.dp).testTag(CHROME_FIXTURE_TAG)) {
            ExpressiveSiteSkinHeader(presentation) {
                Text("Secure · example.com", Modifier.testTag(HEADER_CONTENT_TAG))
            }
            ExpressiveSiteSkinDock(presentation) {
                COMMANDS.forEach { command ->
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = EXPRESSIVE_MINIMUM_TARGET)
                            .semantics { contentDescription = command },
                    )
                }
            }
        }
    }

    private fun presentation(darkTheme: Boolean, reducedMotion: Boolean) =
        ExpressiveSiteSkinPresentation.from(CONFIGURATION, darkTheme, reducedMotion)

    private companion object {
        const val HEADER_CONTENT_TAG = "expressive_header_content"
        const val CHROME_FIXTURE_TAG = "expressive_chrome_fixture"
        val COMMANDS = listOf("Back", "Forward", "Site", "Tabs", "More")
        val CONFIGURATION = SiteSkinValidator.validate(
            """{"schemaVersion":"1.0","site":{"id":"site","name":"Site"},"branding":{"primaryColor":"#D94F8A","secondaryColor":"#FADADD","backgroundColor":"#FFF7FA","textColor":"#2B1B24"}}"""
                .byteInputStream(),
            "https://example.com",
        ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
    }
}
