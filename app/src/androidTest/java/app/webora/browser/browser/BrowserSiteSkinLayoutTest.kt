package app.webora.browser.browser

import androidx.compose.foundation.layout.fillMaxSize
import app.webora.browser.design.WeboraTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import app.webora.browser.siteskin.BrandAsset
import app.webora.browser.siteskin.SITESKIN_BOTTOM_NAV_TAG
import app.webora.browser.web.BrowserWebViewController
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Rule
import org.junit.Test

class BrowserSiteSkinLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun integratedBrowserKeepsBottomNavigationAndQuickActionsVisible() {
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
                    controller = BrowserWebViewController(),
                    onObservation = {},
                    onHome = {},
                    onExternalNavigation = {},
                    onDownload = {},
                    onFileChooser = { _, complete -> complete(null) },
                    brandAsset = BrandAsset.Monogram("S"),
                    onSiteSelect = {},
                    onPageStarted = {},
                    onTabs = {},
                    onSettings = {},
                    onInspector = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeRule.onNodeWithTag(SITESKIN_BOTTOM_NAV_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Quick actions").assertIsDisplayed()
    }

    private companion object {
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
