package app.webora.browser.browser

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
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

    @Composable
    private fun AtDoubleFontScale(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = DOUBLE_FONT_SCALE),
        ) {
            MaterialTheme { content() }
        }
    }

    private companion object {
        const val DOUBLE_FONT_SCALE = 2f
    }
}
