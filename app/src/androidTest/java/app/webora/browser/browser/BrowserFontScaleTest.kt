package app.webora.browser.browser

import app.webora.browser.design.WeboraTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import app.webora.browser.siteskin.SiteSkinConsentModel
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented evidence for A11Y-001's resize-text guarantee (WCAG 2.2 1.4.4).
 *
 * This is evidence, not enforcement: the pre-commit gate is JVM-only, so a layout property that can
 * only be observed on a device is recorded in QA rather than claimed as a gate. `assertIsDisplayed`
 * is the assertion that matters here — it fails for a control clipped out of the viewport, which is
 * exactly how a non-reflowing container fails a user at 200%.
 */
class BrowserFontScaleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onboardingControlsStayReachableAtDoubleFontScale() {
        compose.setContent { AtDoubleFontScale { OnboardingScreen(onComplete = {}) } }

        compose.onNodeWithText("Skip").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test fun privacySettingsControlsStayReachableAtDoubleFontScale() {
        compose.setContent {
            AtDoubleFontScale {
                PrivacySettingsScreen(
                    siteSkinEnabled = true,
                    decisions = emptyList(),
                    onSiteSkinEnabledChange = {},
                    onRemoveDecision = {},
                    onClearBrowsingData = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("Clear browsing data").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test fun consentChoicesStayReachableAtDoubleFontScale() {
        compose.setContent {
            AtDoubleFontScale {
                SiteSkinConsentDialog(
                    origin = "https://example.test",
                    model = SiteSkinConsentModel(
                        title = "Example",
                        subtitle = "A site-provided description",
                        brandColor = null,
                        navigationCount = 5,
                        quickActionCount = 5,
                        menuCount = 20,
                    ),
                    onAllow = {},
                    onNotNow = {},
                    onNever = {},
                )
            }
        }

        compose.onNodeWithText("Allow").assertIsDisplayed()
        compose.onNodeWithText("Not now").assertIsDisplayed()
        compose.onNodeWithText("Never for this site").assertIsDisplayed()
    }

    @Test fun regularChromeStaysReachableAtDoubleFontScale() {
        compose.setContent {
            AtDoubleFontScale {
                BrowserChrome(
                    state = BrowserState(
                        mode = BrowserMode.Regular(
                            requireNotNull(SiteOrigin.parse("https://example.com")),
                        ),
                        displayedUrl = "https://example.com",
                        addressText = "https://example.com",
                        canGoBack = true,
                    ),
                    onAddressChanged = {}, onSubmit = {},
                )
                BrowserNavigationDock(
                    canGoBack = true, canGoForward = false, onBack = {}, onForward = {},
                    onReload = {}, onHome = {}, onSettings = {}, onInspector = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Search or enter address").assertIsDisplayed()
        compose.onNodeWithText("Secure · example.com").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("More").assertIsDisplayed()
    }

    @Composable
    private fun AtDoubleFontScale(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = DOUBLE_FONT_SCALE),
        ) {
            WeboraTheme { content() }
        }
    }

    private companion object {
        const val DOUBLE_FONT_SCALE = 2f
    }
}
