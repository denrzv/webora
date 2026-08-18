package app.webora.browser.siteskin

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import app.webora.browser.design.WeboraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The rendered half of `UX-024`'s navigation hub.
 *
 * `BrowserNavigationHubContractTest` reads the source for the paths that must not exist; this drives
 * the surface. The split is `UX-003`'s: *"runtime behaviour and source structure fail under
 * different regressions"*. Everything here is **instrumented evidence and never a gate claim**, per
 * `A11Y-001` — the JVM gate cannot compose.
 *
 * The fixture builds its actions through [browserNavigationActions] rather than by hand, so a case
 * cannot pass against a shape the browser never produces.
 */
class BrowserNavigationHubTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun theHubOpensExactlyThreeBrowserCommands() {
        compose.setContent { hub() }

        compose.onNodeWithTag(SITESKIN_NAV_BOUQUET_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).assertIsDisplayed().performClick()

        compose.onNodeWithTag(SITESKIN_NAV_BOUQUET_TAG).assertIsDisplayed()
        listOf(SITESKIN_BACK_TAG, SITESKIN_FORWARD_TAG, SITESKIN_REFRESH_TAG).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(MINIMUM_TARGET)
                .assertWidthIsAtLeast(MINIMUM_TARGET)
        }
    }

    @Test fun eachCommandClosesTheBouquetAndDispatchesOnlyItself() {
        // Issue requirements 2–4 in one case, because the interesting property is that each bubble
        // fires *only* its own command. Three separate cases each asserting one call could all pass
        // against a bouquet whose every bubble dispatched Back.
        listOf(
            SITESKIN_BACK_TAG to BrowserNavigationCommand.BACK,
            SITESKIN_FORWARD_TAG to BrowserNavigationCommand.FORWARD,
            SITESKIN_REFRESH_TAG to BrowserNavigationCommand.REFRESH,
        ).forEach { (tag, expected) ->
            val commands = mutableListOf<BrowserNavigationCommand>()
            compose.setContent { hub(onCommand = { commands += it }) }

            compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).performClick()
            compose.onNodeWithTag(tag).performClick()

            assertEquals("$tag must dispatch exactly $expected", listOf(expected), commands)
            compose.onNodeWithTag(SITESKIN_NAV_BOUQUET_TAG).assertDoesNotExist()
        }
    }

    @Test fun anUnavailableCommandIsDisabledWhileTheOthersAreNot() {
        // Issue requirements 5 and 6. Disabled state is a semantics property, not a colour —
        // `A11Y-001` — and the surrounding assertions are what stop a regression that disabled
        // everything from passing the row it was written for.
        compose.setContent { hub(canGoBack = false, canGoForward = false) }

        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).performClick()

        compose.onNodeWithTag(SITESKIN_BACK_TAG).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag(SITESKIN_FORWARD_TAG).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag(SITESKIN_REFRESH_TAG).assertIsDisplayed().assertIsEnabled()
    }

    @Test fun theCollapsedHubIsEnabledWithNoHistoryAtAll() {
        // Criterion 2, as a rendered fact. A hub that greys out at the history root *is* a Back
        // button, and a user who cannot open it also loses Forward and Refresh.
        compose.setContent { hub(canGoBack = false, canGoForward = false, canRefresh = false) }

        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag(SITESKIN_NAV_BOUQUET_TAG).assertIsDisplayed()
    }

    @Test fun systemBackClosesTheBouquetExactlyOnceAndDispatchesNothing() {
        // Issue requirement 8. The `Popup` is focusable and its own window, so Back is consumed
        // there before `BrowserBackHandler` sees it — `UX-022`'s mechanism, and the reason
        // `BrowserBack.kt` is not touched by this ticket.
        val commands = mutableListOf<BrowserNavigationCommand>()
        compose.setContent { hub(onCommand = { commands += it }) }

        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).performClick()
        compose.onNodeWithTag(SITESKIN_NAV_BOUQUET_TAG).assertIsDisplayed()
        pressBack()

        compose.onNodeWithTag(SITESKIN_NAV_BOUQUET_TAG).assertDoesNotExist()
        assertEquals("closing must navigate nothing", emptyList<BrowserNavigationCommand>(), commands)
    }

    @Test fun everyCommandStaysReachableOnACompactHostAtDoubleTextScale() {
        // Issue requirement 12. The bubbles are icon-only and fixed-size, so scale cannot grow them;
        // what this proves is that the cluster still fits and still meets the target contract at the
        // 320 dp floor this repository treats as its compact width.
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                Box(Modifier.width(COMPACT_HOST)) { hub() }
            }
        }

        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).performClick()

        val bouquet = compose.onNodeWithTag(SITESKIN_NAV_BOUQUET_TAG).fetchSemanticsNode().boundsInRoot
        val hostRight = with(compose.density) { COMPACT_HOST.toPx() }
        assertTrue("the cluster must stay inside a $COMPACT_HOST host: $bouquet", bouquet.right <= hostRight)
        listOf(SITESKIN_BACK_TAG, SITESKIN_FORWARD_TAG, SITESKIN_REFRESH_TAG).forEach { tag ->
            compose.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(MINIMUM_TARGET)
                .assertWidthIsAtLeast(MINIMUM_TARGET)
        }
    }

    @Composable
    private fun hub(
        canGoBack: Boolean = true,
        canGoForward: Boolean = true,
        canRefresh: Boolean = true,
        onCommand: (BrowserNavigationCommand) -> Unit = {},
    ) {
        var expanded by remember { mutableStateOf(false) }
        WeboraTheme {
            BrowserNavigationHub(
                BrowserNavigationHubState(
                    actions = browserNavigationActions(canGoBack, canGoForward, canRefresh),
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onCommand = onCommand,
                ),
            )
        }
    }

    private companion object {
        val MINIMUM_TARGET = 48.dp
        val COMPACT_HOST = 320.dp
    }
}
