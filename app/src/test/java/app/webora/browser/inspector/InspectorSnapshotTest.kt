package app.webora.browser.inspector

import app.webora.browser.siteskin.BrandAsset
import app.webora.browser.siteskin.SiteConsentDecision
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.model.SiteSkinConfiguration
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorSnapshotTest {

    @Test fun `an over-long collection arrives already bounded, so the two layers agree`() {
        // Core truncated this to five during normalization and emitted SS-W-LIMIT-TRUNCATED. The
        // app-layer 5/5/20 cap is defence in depth on top of that, so the counts matching is the
        // expected reading — and the diagnostic in the record is where the sixth item is accounted
        // for. The panel would be lying if it presented the app cap as the truncation.
        val configuration = configuration(navigation = (1..6).joinToString(",") { item("n$it", "/p$it") })

        val navigation = snapshot(configuration).applied!!.counts
            .single { it.collection == InspectorCollection.BOTTOM_NAVIGATION }

        assertEquals(5, navigation.trusted)
        assertEquals(5, navigation.rendered)
        assertFalse(navigation.diverged)
    }

    @Test fun `the divergence flag is not decoration`() {
        // It should never fire, which is exactly why it needs proving it can.
        assertTrue(InspectorItemCount(InspectorCollection.MENU, trusted = 6, rendered = 5).diverged)
    }

    @Test fun `a page matching no pattern has no active item rather than the first one`() {
        val configuration = configuration(navigation = "${item("home", "/home")},${item("cart", "/cart")}")

        val applied = snapshot(configuration, pageUrl = "https://shop.example/nothing").applied

        assertNull(applied?.activeNavigationId)
        assertTrue(applied?.navigation?.none(InspectorItem::active) == true)
    }

    @Test fun `the active item is the one NavMatcher chose`() {
        val configuration = configuration(navigation = "${item("home", "/home")},${item("cart", "/cart")}")

        val applied = snapshot(configuration, pageUrl = "https://shop.example/cart").applied

        assertEquals("cart", applied?.activeNavigationId)
    }

    @Test fun `a colour core corrected reaches the app layer already corrected`() {
        // White on white passes the schema and fails WCAG. The correction happens in core's security
        // validation, so the trusted value is no longer the value the manifest wrote — and
        // SS-W-CONTRAST-CORRECTED in the record's diagnostics is the only trace of that. The panel
        // must not present the trusted value as "what you asked for".
        val configuration = configuration(branding = """"backgroundColor":"#FFFFFF","textColor":"#FFFFFF"""")

        val background = snapshot(configuration).applied!!.theme.roles
            .single { it.role == InspectorColorRoleName.BACKGROUND }

        assertNotEquals("#FFFFFF", background.trusted)
        assertEquals(background.trusted, background.applied)
    }

    @Test fun `the dark projection is selected by the browser flag, not by the manifest`() {
        val configuration = configuration(branding = """"backgroundColor":"#FFFFFF"""")

        val light = snapshot(configuration, darkTheme = false).applied!!.theme
        val dark = snapshot(configuration, darkTheme = true).applied!!.theme

        assertTrue(light.roles.isNotEmpty())
        assertNotEquals(
            light.roles.single { it.role == InspectorColorRoleName.BACKGROUND }.applied,
            dark.roles.single { it.role == InspectorColorRoleName.BACKGROUND }.applied,
        )
        assertTrue(dark.darkTheme)
    }

    @Test fun `the dark projection reports no trusted content colour, because none was used`() {
        val configuration = configuration(branding = """"textColor":"#1B1B1F"""")

        val roles = snapshot(configuration, darkTheme = true).applied!!.theme.roles

        assertNull(roles.single { it.role == InspectorColorRoleName.ON_BACKGROUND }.trusted)
        assertEquals(
            "#1B1B1F",
            snapshot(configuration, darkTheme = false).applied!!.theme.roles
                .single { it.role == InspectorColorRoleName.ON_BACKGROUND }.trusted,
        )
    }

    @Test fun `activation distinguishes every reason branding is or is not applied`() {
        val configuration = configuration()

        assertEquals(
            InspectorActivation.DISABLED,
            snapshot(configuration, siteSkinEnabled = false).activation,
        )
        assertEquals(InspectorActivation.INTEGRATED, snapshot(configuration).activation)
        assertEquals(InspectorActivation.PENDING, snapshot(null, record = null).activation)
        assertEquals(
            InspectorActivation.UNAVAILABLE,
            snapshot(null, record = record(TraceValidationResult.REJECTED)).activation,
        )
        assertEquals(
            InspectorActivation.AWAITING_CONSENT,
            snapshot(null, record = record(TraceValidationResult.ACCEPTED), consent = null).activation,
        )
        assertEquals(
            InspectorActivation.REFUSED,
            snapshot(
                null,
                record = record(TraceValidationResult.ACCEPTED),
                consent = SiteConsentDecision.NEVER,
            ).activation,
        )
    }

    @Test fun `the brand asset is reported as decoded or generated, never as a url`() {
        assertEquals(InspectorBrandAsset.NONE, snapshot(null, brandAsset = null).brandAsset)
        assertEquals(
            InspectorBrandAsset.MONOGRAM,
            snapshot(null, brandAsset = BrandAsset.Monogram("S")).brandAsset,
        )
    }

    @Test fun `the origin is reported in the canonical form the browser keys decisions under`() {
        assertEquals("https://shop.example", snapshot(null).origin)
    }

    private fun snapshot(
        configuration: SiteSkinConfiguration?,
        pageUrl: String = "https://shop.example/home",
        darkTheme: Boolean = false,
        siteSkinEnabled: Boolean = true,
        consent: SiteConsentDecision? = SiteConsentDecision.ALLOW,
        brandAsset: BrandAsset? = BrandAsset.Monogram("S"),
        record: ManifestTraceRecord? = record(TraceValidationResult.ACCEPTED),
    ): InspectorSnapshot = inspectorSnapshot(
        InspectorBrowserState(
            origin = SiteOrigin.parse(pageUrl),
            pageUrl = pageUrl,
            configuration = configuration,
            consent = consent,
            siteSkinEnabled = siteSkinEnabled,
            brandAsset = brandAsset,
            darkTheme = darkTheme,
        ),
        record,
    )

    private fun record(result: TraceValidationResult) = ManifestTraceRecord(
        origin = "https://shop.example",
        generation = 1,
        transport = ManifestTransportTrace(
            manifestUrl = "https://shop.example/.well-known/siteskin.json",
            outcome = TraceTransportOutcome.FETCHED,
            cacheState = TraceCacheState.MISS,
            httpStatus = 200,
        ),
        validation = ManifestValidationTrace(result, "1.0".takeIf { result == TraceValidationResult.ACCEPTED }),
    )

    private fun item(id: String, path: String) =
        """{"id":"$id","label":"$id","action":{"type":"internal_url","url":"$path"},"match":["$path"]}"""

    private fun configuration(navigation: String = "", branding: String = ""): SiteSkinConfiguration {
        val parts = buildList {
            add(""""schemaVersion":"1.0"""")
            add(""""site":{"id":"shop","name":"Shop","homeUrl":"https://shop.example/"}""")
            if (branding.isNotEmpty()) add(""""branding":{$branding}""")
            if (navigation.isNotEmpty()) add(""""bottomNavigation":[$navigation]""")
        }
        val json = "{${parts.joinToString(",")}}"
        val outcome = SiteSkinValidator.validate(json.byteInputStream(), "https://shop.example")
        return (outcome as SiteSkinValidationOutcome.Accepted).configuration
    }
}
