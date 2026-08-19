package app.webora.browser.inspector

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.webora.browser.design.WeboraTheme
import app.webora.browser.siteskin.HubSurface
import app.webora.browser.siteskin.SiteConsentDecision
import dev.siteskin.core.model.HubPresentation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The one assertion no JVM test can make: that the document reaches the **Android** clipboard.
 *
 * Everything else about the payload — its field names, its order, its bounds, its types, and the fact
 * that it is built from one snapshot and nothing else — is settled by `InspectorJsonTest` and
 * `InspectorCopyContractTest` in the gate. What is left here is the platform seam, and a device is
 * the only place to observe it.
 *
 * Instrumented evidence, never a gate claim. `./gradlew test` never runs this, and
 * `scripts/pre-commit-check.sh` reaches it only as `:app:compileDebugAndroidTestKotlin`.
 */
class InspectorCopyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun copyingPlacesParseableJsonOnTheSystemClipboard() {
        var closed = false
        compose.setContent { WeboraTheme { SiteSkinInspectorPanel(SNAPSHOT) { closed = true } } }

        compose.onNodeWithTag(INSPECTOR_COPY_TAG).performClick()
        compose.waitForIdle()

        val document = JSONObject(clipboardText())
        assertEquals("https://shop.example", document.getString("origin"))
        assertEquals("INTEGRATED", document.getString("activation"))
        assertTrue("booleans must survive as booleans", document.getBoolean("siteSkinEnabled"))
        // A nested object and an array, as JSON types rather than as display strings.
        assertEquals(
            "SS-W-FIELD-UNKNOWN",
            document.getJSONObject("record")
                .getJSONObject("validation")
                .getJSONArray("diagnostics")
                .getJSONObject(0)
                .getString("code"),
        )
        // An absent value is null, never the panel's dash.
        assertTrue("an absent trace must be JSON null", document.isNull("brandAssetTrace"))

        // The panel stays open, and the modal was not dismissed to deliver the payload.
        compose.onNodeWithTag(INSPECTOR_PANEL_TAG).assertIsDisplayed()
        assertEquals(false, closed)
    }

    @Test fun copyingConfirmsWithoutClosingAndRepeatsSafely() {
        compose.setContent { WeboraTheme { SiteSkinInspectorPanel(SNAPSHOT) {} } }

        compose.onNodeWithTag(INSPECTOR_COPY_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(INSPECTOR_COPY_STATUS_TAG).assertIsDisplayed()

        // Repeated taps replace the clipboard with the current snapshot's document and never
        // accumulate, truncate or corrupt it.
        compose.onNodeWithTag(INSPECTOR_COPY_TAG).performClick()
        compose.waitForIdle()

        assertEquals("https://shop.example", JSONObject(clipboardText()).getString("origin"))
        compose.onNodeWithTag(INSPECTOR_PANEL_TAG).assertIsDisplayed()
    }

    @Test fun theCopyControlStaysReachableAtDoubleFontScale() {
        // `AlertDialogFlowRow` reflows the action pair onto separate lines when the labels do not
        // fit, which is what makes two controls acceptable here without adopting `UX-007`'s explicit
        // vertical stack. `assertIsDisplayed` fails for a control pushed out of the viewport, which
        // is exactly how a non-reflowing row fails a user at 200%.
        compose.setContent {
            AtDoubleFontScale { SiteSkinInspectorPanel(SNAPSHOT) {} }
        }

        compose.onNodeWithTag(INSPECTOR_COPY_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHeightIsAtLeast(MINIMUM_TARGET)
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

    private fun clipboardText(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = requireNotNull(clipboard.primaryClip) { "nothing reached the system clipboard" }
        return clip.getItemAt(0).coerceToText(context).toString()
    }

    private companion object {
        const val DOUBLE_FONT_SCALE = 2f
        val MINIMUM_TARGET = 48.dp

        val SNAPSHOT = InspectorSnapshot(
            origin = "https://shop.example",
            activation = InspectorActivation.INTEGRATED,
            consent = SiteConsentDecision.ALLOW,
            siteSkinEnabled = true,
            brandAsset = InspectorBrandAsset.DECODED_BITMAP,
            // Deliberately absent, so the JSON `null` case is observed on the device too.
            brandAssetTrace = null,
            record = ManifestTraceRecord(
                origin = "https://shop.example",
                generation = 1,
                transport = ManifestTransportTrace(
                    manifestUrl = "https://shop.example/.well-known/siteskin.json",
                    outcome = TraceTransportOutcome.FETCHED,
                    cacheState = TraceCacheState.MISS,
                    httpStatus = 200,
                ),
                validation = ManifestValidationTrace(
                    result = TraceValidationResult.ACCEPTED,
                    schemaVersion = "1.0",
                    diagnostics = listOf(TraceDiagnostic("SS-W-FIELD-UNKNOWN", "/unknownField")),
                ),
            ),
            applied = InspectorAppliedChrome(
                siteName = "Bloom Flowers",
                siteId = "bloom",
                homeUrl = "https://shop.example/",
                activeNavigationId = "home",
                counts = listOf(InspectorItemCount(InspectorCollection.BOTTOM_NAVIGATION, 4, 4)),
                navigation = listOf(InspectorItem("home", "Home", "internal_url", true)),
                hub = InspectorHub(requested = HubPresentation.DRAWER, effective = HubSurface.DRAWER),
                theme = InspectorTheme(
                    darkTheme = false,
                    roles = listOf(
                        InspectorColorRole(InspectorColorRoleName.PRIMARY, "#D94F8A", "#D94F8A"),
                    ),
                ),
            ),
        )
    }
}
