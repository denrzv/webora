package app.webora.browser.browser

import androidx.compose.foundation.layout.fillMaxSize
import app.webora.browser.design.WeboraTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.webora.browser.siteskin.BrandAsset
import app.webora.browser.siteskin.SITESKIN_DOCK_TAG
import app.webora.browser.siteskin.SITESKIN_BOTTOM_NAV_TAG
import app.webora.browser.web.BrowserWebViewController
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class BrowserSiteSkinLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun integratedBrowserUsesFixedDockInsteadOfPersistentSiteNavigation() {
        var backInvoked = false
        val origin = requireNotNull(SiteOrigin.parse(SITE_URL))
        val configuration = SiteSkinValidator.validate(MANIFEST.byteInputStream(), SITE_URL)
            .let { (it as SiteSkinValidationOutcome.Accepted).configuration }
        val state = BrowserState(
            mode = BrowserMode.Integrated(origin, configuration),
            displayedUrl = "about:blank",
            addressText = SITE_URL,
        )

        composeRule.setContent {
            WeboraTheme {
                RegularBrowser(
                    state = state,
                    controller = BrowserWebViewController(TAB_ID),
                    canNavigateBack = true,
                    onBack = { backInvoked = true },
                    onAddressEdited = {},
                    onRendererEvent = {},
                    onHome = {},
                    onExternalNavigation = {},
                    onDownload = {},
                    onFileChooser = { _, complete -> complete(null) },
                    brandAsset = BrandAsset.Monogram("S"),
                    siteActionsExpanded = false,
                    onSiteActionsToggle = {},
                    onSiteActionsDismiss = {},
                    onSiteSelect = {},
                    onOpenBrowserMenu = {},
                    onTabs = {},
                    onSettings = {},
                    onInspector = {},
                    canRefresh = true,
                    onRefresh = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeRule.onNodeWithTag(SITESKIN_DOCK_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BROWSER_NAVIGATION_SHELL_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SITESKIN_BOTTOM_NAV_TAG).assertDoesNotExist()
        val content = composeRule.onNodeWithTag(BROWSER_CONTENT_TAG).fetchSemanticsNode().boundsInRoot
        val dock = composeRule.onNodeWithTag(SITESKIN_DOCK_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("WebView content must reserve the dock: content=$content dock=$dock", content.bottom <= dock.top)
        composeRule.onNodeWithContentDescription("Open site navigation").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsEnabled().performClick()
        assertTrue(backInvoked)
    }

    @Test
    fun regularBrowserRemovesSiteSkinLayersBeforeShowingBrowserNavigation() {
        var backInvoked = false
        val origin = requireNotNull(SiteOrigin.parse(SITE_URL))
        val state = BrowserState(mode = BrowserMode.Regular(origin), displayedUrl = "about:blank")

        composeRule.setContent {
            WeboraTheme {
                RegularBrowser(
                    state = state,
                    controller = BrowserWebViewController(TAB_ID),
                    canNavigateBack = true,
                    onBack = { backInvoked = true },
                    onAddressEdited = {},
                    onRendererEvent = {},
                    onHome = {},
                    onExternalNavigation = {},
                    onDownload = {},
                    onFileChooser = { _, complete -> complete(null) },
                    brandAsset = null,
                    siteActionsExpanded = false,
                    onSiteActionsToggle = {},
                    onSiteActionsDismiss = {},
                    onSiteSelect = {},
                    onOpenBrowserMenu = {},
                    onTabs = {},
                    onSettings = {},
                    onInspector = {},
                    canRefresh = true,
                    onRefresh = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeRule.onNodeWithTag(BROWSER_NAVIGATION_SHELL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SITESKIN_DOCK_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").assertIsEnabled().performClick()
        assertTrue(backInvoked)
    }

    private companion object {
        const val TAB_ID = 1L
        const val SITE_URL = "https://shop.example"
        val MANIFEST = """
            {"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},
            "bottomNavigation":[
              {"id":"home","label":"Home","action":{"type":"refresh"}},
              {"id":"page","label":"Page","action":{"type":"refresh"}},
              {"id":"outside","label":"Outside","action":{"type":"refresh"}},
              {"id":"refresh","label":"Refresh","action":{"type":"refresh"}},
              {"id":"menu","label":"Menu","action":{"type":"open_menu"}}],
            "quickActions":[{"id":"call","label":"Call","action":{"type":"refresh"}}]}
        """.trimIndent()
    }
}
