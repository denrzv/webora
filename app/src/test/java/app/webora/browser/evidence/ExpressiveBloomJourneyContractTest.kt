package app.webora.browser.evidence

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveBloomJourneyContractTest {
    @Test
    fun `canonical visual story is six showcase frames in order`() {
        val source = source(SCREENSHOT_SOURCE)

        assertEquals(CANONICAL_FRAMES, capturedFrames(source))
        // A frame depicting the page must prove the page drew; a frame depicting a full-window
        // modal must prove the modal composed. `UX-022` moved 04 across that line: the hub is now a
        // drawer in its own window covering `BROWSER_CONTENT_TAG`'s rectangle, so a rendered-content
        // check there would measure the drawer and pass for the wrong reason — `CI-005`'s defect,
        // not `CI-003`'s guarantee. Requiring the *right* check per frame is what keeps this from
        // becoming a place to opt out of one.
        (CANONICAL_FRAMES - MODAL_FRAMES).drop(1).forEach { frame ->
            assertTrue(
                "$frame must require rendered page content",
                source.contains("captureDeviceScreenshot(\"$frame\", requirePageContent = true)"),
            )
        }
        MODAL_FRAMES.forEach { frame ->
            assertFalse(
                "$frame covers the page region, so a rendered-content check there measures the modal",
                source.contains("captureDeviceScreenshot(\"$frame\", requirePageContent = true)"),
            )
        }
        MODAL_FRAME_ASSERTIONS.forEach { marker ->
            assertTrue("a modal frame must assert its own surface instead: $marker", source.contains(marker))
        }
        SHOWCASE_MARKERS.forEach { marker -> assertTrue("missing showcase marker: $marker", source.contains(marker)) }
    }

    @Test
    fun `showcase does not carry the diagnostic product history traversal`() {
        val source = source(SCREENSHOT_SOURCE)

        assertFalse(source.contains("PRODUCT_LINK_TEXT"))
        assertFalse(source.contains("SITESKIN_FORWARD_TAG"))
        assertFalse(source.contains("waitForProductLink"))
    }

    @Test
    fun `navigation smoke retains product back forward hub and regular handoff`() {
        val source = source(SMOKE_SOURCE)

        SMOKE_MARKERS.forEach { marker -> assertTrue("missing smoke marker: $marker", source.contains(marker)) }
        assertFalse("smoke coverage must not publish visual evidence", source.contains("captureDeviceScreenshot("))
    }

    @Test
    fun `hosted suite runs showcase before smoke`() {
        val source = source(SUITE_SOURCE)
        val screenshot = source.indexOf("LiveSiteScreenshotTest::class")
        val smoke = source.indexOf("LiveSiteNavigationSmokeTest::class")

        assertTrue(screenshot >= 0)
        assertTrue(smoke > screenshot)
    }

    @Test
    fun `a partial or reordered inventory cannot satisfy the evidence contract`() {
        val unsafe = CANONICAL_FRAMES.dropLast(1).reversed().joinToString("\n") { frame ->
            "captureDeviceScreenshot(\"$frame\")"
        }

        assertFalse(capturedFrames(unsafe) == CANONICAL_FRAMES)
    }

    @Test
    fun `retired eight frame product artifact cannot masquerade as the showcase`() {
        val unsafe = listOf(
            "01-home.png",
            "02-siteskin-consent.png",
            "03-bloom-storefront.png",
            "04-happy-days-product.png",
            "05-bloom-storefront-back.png",
            "06-happy-days-forward.png",
            "07-siteskin-hub.png",
            "08-regular-browsing.png",
        ).joinToString("\n") { frame -> "captureDeviceScreenshot(\"$frame\")" }

        assertFalse(capturedFrames(unsafe) == CANONICAL_FRAMES)
    }

    /**
     * The hosted source at [path], resolved from the root the build declares as an input.
     *
     * Not a relative path. `SiteSkinTopBarContractTest` records why — *"the working directory a test
     * runs in is not a contract, and a scan that silently fails to find its subject is a scan that
     * passes for the wrong reason"* — and `app/build.gradle.kts` records why the same property also
     * has to be an `inputs.dir`: without it this whole file is `UP-TO-DATE` on exactly the change it
     * exists to catch.
     */
    private fun source(path: Path): String {
        val root = requireNotNull(System.getProperty(INSTRUMENTED_ROOT_PROPERTY)) {
            "$INSTRUMENTED_ROOT_PROPERTY is unset; app/build.gradle.kts must pass the androidTest root"
        }
        val resolved = Path.of(root).resolve(path)
        require(Files.exists(resolved)) { "hosted source not found: $resolved" }
        return String(Files.readAllBytes(resolved))
    }

    private fun capturedFrames(source: String): List<String> = CAPTURE.findAll(source)
        .map { match -> match.groupValues[1] }
        .toList()

    private companion object {
        const val INSTRUMENTED_ROOT_PROPERTY = "webora.app.androidTest"

        val SCREENSHOT_SOURCE = Path.of("app/webora/browser/visual/LiveSiteScreenshotTest.kt")
        val SMOKE_SOURCE = Path.of("app/webora/browser/visual/LiveSiteNavigationSmokeTest.kt")
        val SUITE_SOURCE = Path.of("app/webora/browser/visual/LiveSiteHostedSuite.kt")

        /** Frames whose subject is a full-window modal rather than the page behind it. */
        val MODAL_FRAMES = listOf("02-siteskin-consent.png", "04-bloom-actions.png")

        /**
         * What a modal frame asserts in place of a pixel fraction.
         *
         * Both are stricter than the check they replace for the frame they cover: a heading that is
         * present, and a hub whose every trusted row is displayed. Neither can be satisfied by an
         * empty surface, which is what the fraction was there to catch.
         */
        val MODAL_FRAME_ASSERTIONS = listOf(
            "onNodeWithText(consentTitle).assertIsDisplayed()",
            "onNodeWithTag(SITESKIN_HUB_DRAWER_TAG).assertIsDisplayed()",
            "BloomReferenceContract.ACTION_IDS.forEach",
        )

        val SHOWCASE_MARKERS = listOf(
            // `UX-024`/`/review` FINDING-2: two browser-owned controls now stand between the user
            // and integrated Back, and `CI-008` binds on both. One helper per file rather than an
            // inlined copy, because the showcase's copy and the smoke test's had already drifted —
            // one asserted the hub's prerequisite and the other did not.
            "private fun openNavigationHub()",
            ".onNodeWithTag(SITESKIN_NAV_HUB_TAG).assertIsDisplayed().assertIsEnabled().performClick()",
            "SITESKIN_DOCK_HUB_TAG",
            "SITESKIN_HUB_DRAWER_TAG",
            // `CI-009`'s #110 moved the tag prefix behind `BloomReferenceContract`, and this list
            // still required the literal it replaced — so `main` was red before `BROWSE-010` began.
            // The markers follow the mechanism rather than the spelling: the showcase must still
            // address site actions by tag, and must still select the profile action specifically.
            "BloomReferenceContract.actionTag(",
            "PROFILE_PAGE_HEADING = \"Account\"",
            "BloomReferenceContract.PROFILE_ACTION_ID",
            "REGULAR_ADDRESS = \"https://www.google.com/ncr\"",
            "REGULAR_DOMAIN = \"google.com\"",
            "EXPRESSIVE_HEADER_TAG).assertDoesNotExist()",
            "SITESKIN_DOCK_TAG).assertDoesNotExist()",
        )

        val SMOKE_MARKERS = listOf(
            "PRODUCT_NAME = \"Happy Days Bouquet\"",
            "PRODUCT_LINK_TEXT = \"Happy Days\"",
            "findStorefrontProductLink()",
            "scrollStorefrontTowardsProduct()",
            "By.text(PRODUCT_LINK_TEXT).clickable(true)",
            // `UX-024`: the dock no longer offers Back or Forward, so the smoke traversal reaches
            // them through the header's navigation hub. Re-stated rather than dropped — losing the
            // history half of this journey is exactly what a shortened marker list would hide.
            "private fun openNavigationHub()",
            ".onNodeWithTag(SITESKIN_NAV_HUB_TAG).assertIsDisplayed().assertIsEnabled().performClick()",
            "SITESKIN_BACK_TAG",
            "SITESKIN_FORWARD_TAG",
            "SITESKIN_DOCK_HUB_TAG",
            "waitForProductLink(isPresent = false)",
            "waitForProductLink(isPresent = true)",
            "REGULAR_SMOKE_ADDRESS = \"example.com\"",
        )

        val CANONICAL_FRAMES = listOf(
            "01-home.png",
            "02-siteskin-consent.png",
            "03-bloom-storefront.png",
            "04-bloom-actions.png",
            "05-bloom-profile.png",
            "06-google-regular.png",
        )
        val CAPTURE = Regex("captureDeviceScreenshot\\(\\\"([^\\\"]+\\.png)\\\"")
    }
}
