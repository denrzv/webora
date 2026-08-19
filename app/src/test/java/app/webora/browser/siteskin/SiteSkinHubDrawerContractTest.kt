package app.webora.browser.siteskin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract for the hub drawer.
 *
 * The runtime behaviour it can reach lives in `SiteSkinChromeModelTest`; what a JVM gate cannot
 * drive is composition, so what it *can* do is read the source for the mechanisms this surface must
 * not acquire. Every assertion here is written against executable lines only — `BROWSE-009`'s rule,
 * and a file whose KDoc names `BrandAssetLoader` and `ModalNavigationDrawer` in order to explain why
 * neither is used would otherwise fail its own scan.
 */
class SiteSkinHubDrawerContractTest {
    @Test
    fun `the drawer is its own window so Back is consumed structurally`() {
        val source = drawerSource()

        assertTrue("the scan must see real code", "fun SiteSkinHubDrawer(" in source)
        assertTrue("a Dialog is what gives the drawer its own window", "Dialog(" in source)
        assertTrue("usePlatformDefaultWidth = false" in source)
        assertFalse(
            "ModalNavigationDrawer composes into the browser's window and would need a second " +
                "back handler racing BrowserBackHandler",
            "ModalNavigationDrawer" in source,
        )
        assertFalse(
            "a back handler here would be a second answer to BROWSE-002's single Back contract",
            "BackHandler" in source,
        )
    }

    /**
     * Start-side by layout direction, never by a physical edge.
     *
     * `Alignment.TopStart` resolves its horizontal half against `LayoutDirection`, so the drawer is
     * correct under RTL with no separate code path. A `CenterEnd`, an `absolute` alignment or a
     * negative offset would each pin it to a physical side and silently be wrong in half the world's
     * locales.
     *
     * **`UX-026` moved the vertical anchor and this assertion is restated rather than relaxed.** It
     * read `Alignment.CenterStart` while the panel filled the height, where the vertical half meant
     * nothing; a content-sized panel has to sit at the top so the scrim a short menu leaves behind is
     * *below* it, where a tap can land. The property under assertion is unchanged — the horizontal
     * anchor is direction-aware — and the forbidden list grew rather than shrank, because deleting
     * names from it would be indistinguishable from weakening the predicate.
     */
    @Test
    fun `the panel is placed by a direction-aware alignment`() {
        val source = drawerSource()

        assertTrue("Alignment.TopStart" in source)
        listOf(
            "Alignment.CenterEnd", "Alignment.TopEnd", "Alignment.BottomEnd",
            "AbsoluteAlignment", "Alignment.Absolute",
        ).forEach {
            assertFalse("$it pins the drawer to a physical edge", it in source)
        }
    }

    /**
     * The scrim closes the drawer, and it does so by a gesture the browser owns.
     *
     * **The framework's own path cannot do this, which research F1 establishes by decompiling it.**
     * `DialogWrapper.onTouchEvent` dismisses only when `DialogLayout.isInsideContent` says the touch
     * fell outside the composed content's bounds, and this content fills the window — so
     * `dismissOnClickOutside` is true, honoured, and unreachable. `SITESKIN_HUB_SCRIM_TAG` named a
     * rectangle that absorbed the tap and did nothing with it, which is why the only reliable way to
     * close the drawer was to select a row and navigate.
     *
     * **A gesture and not `clickable`.** A full-screen `clickable` publishes a semantics node with a
     * click role and an accessible name, so assistive technology would meet a screen-sized button in
     * front of the menu and have to traverse past it. `pointerInput` contributes no semantics at all,
     * and Back remains the accessible dismissal path — which is why the issue's requirement that the
     * scrim not be the *only* accessible route is met by construction.
     */
    @Test
    fun `the scrim dismisses by a browser-owned gesture`() {
        val source = drawerSource()

        val window = drawerWindowSource()

        assertTrue("the gesture is applied to the tagged scrim", ".testTag(SITESKIN_HUB_SCRIM_TAG)" in window)
        assertTrue("and it is this modifier that carries it", ".hubScrim(onDismiss)" in window)
        assertTrue("which is a tap gesture", "detectTapGestures" in declaration("private fun Modifier.hubScrim("))
        assertFalse(
            "a clickable scrim would be a screen-sized control in front of the menu",
            "clickable" in source,
        )
        assertFalse("and a role would be the same claim by another spelling", "Role." in source)
    }

    /**
     * A tap that lands on the panel never reaches the scrim's detector.
     *
     * The scrim is the panel's **ancestor**, so it is on the hit path for a tap on the panel too, and
     * Compose runs the Main pass children-first. Without a consuming node on the panel, a tap on
     * empty panel space — beside a heading, below the last row — would fall through and close the
     * drawer under the user's finger. `detectTapGestures` awaits an *unconsumed* down, so consuming
     * what the rows and the scroll did not is the whole mechanism.
     *
     * This is the half a reviewer would leave out, because the scrim works without it.
     */
    @Test
    fun `the panel consumes what its children did not`() {
        assertTrue("the panel is the node that consumes", ".consumesPanelTaps()" in drawerWindowSource())
        val consumer = declaration("private fun Modifier.consumesPanelTaps(")
        assertTrue("by consuming pointer changes", "consume()" in consumer)
    }

    /**
     * The panel is the size of its menu, between bounds the browser owns.
     *
     * Before `UX-026` it was `fillMaxHeight()`, so the reference integration drew ~272 dp of content
     * — two rows and a header, after `UX-025` moved three ids into the dock — in an ~800 dp box, with
     * two thirds of it blank. `heightIn` plus the content column's existing `verticalScroll` wraps a
     * short menu and bounds-and-scrolls a long one, so nothing is capped to stay compact.
     *
     * **The bounds come from the rule and never from a literal.** A `heightIn(max = 600.dp)` here
     * would be a second, unmeasured height policy sitting where nobody would look for one, and it
     * would escape `HubDrawerHeightTest`'s assertion that the decision reads a dimension and nothing
     * else — which is this ticket's security assertion.
     */
    @Test
    fun `the panel is bounded by the rule, not by the viewport`() {
        val window = drawerWindowSource()

        assertFalse(
            "a full-height panel is the defect UX-026 removes",
            "fillMaxHeight()" in window,
        )
        assertTrue("the available height is measured", "BoxWithConstraints(" in window)
        assertTrue("and handed to the rule", "hubDrawerHeight(maxHeight)" in window)
        assertTrue("whose bounds are what the panel takes", "heightIn(min = height.min, max = height.max)" in window)
        assertEquals("one height chain, or there are two height policies", 1, window.split("heightIn(").size - 1)
        assertFalse(
            "every dimension in this composable is a named constant or the height rule; a literal " +
                "here is a measurement nothing has justified",
            ".dp)" in window,
        )
    }

    /**
     * One inset read, on the box that measures the space the panel is sized against.
     *
     * `BrowserScreen` consumes `safeDrawing` once for the browser's own window and the drawer is a
     * different window, so these do not compound — but *within* this window the placement stopped
     * being cosmetic once the panel became content-sized. Inside the panel the padding inflates the
     * panel's own height; on the measuring box it reduces the space the maximum is a fraction of,
     * which is what the maximum should mean.
     */
    @Test
    fun `the safe area is read exactly once, by the box that measures`() {
        val window = drawerWindowSource()

        assertEquals(
            "one read, or the panel is padded twice",
            1,
            drawerSource().split("WindowInsets.safeDrawing").size - 1,
        )
        // Presence in this composable is not enough — the previous placement was inside it too, on
        // the content. What separates them is *order*: the padding has to be in the chain whose
        // constraints the rule then reads, so it must precede that read.
        val padded = window.indexOf("windowInsetsPadding")
        val measured = window.indexOf("hubDrawerHeight(maxHeight)")
        assertTrue("the inset must be applied before the height it reduces is measured", padded in 0..<measured)
        assertFalse(
            "the content takes no modifier at all now, which is what keeps the read on the box",
            "HubDrawerContent(\n" in window && "modifier = " in window.substringAfter("HubDrawerContent(\n"),
        )
    }

    /**
     * No second brand-asset loader, and no raw resource lookup.
     *
     * `NET-003`'s same-origin recheck and `NET-004`'s publication guard both live in one place, and
     * a component that can fetch is a component that can show a logo this configuration never
     * earned. The asset arrives as an already-decided [BrandAsset] parameter. `UX-005`'s rule
     * covers the icons: a closed map, never `getIdentifier`.
     */
    @Test
    fun `the drawer loads nothing and looks nothing up`() {
        val source = drawerSource()

        listOf(
            "BrandAssetLoader", "OkHttp", "HttpUrl", "LaunchedEffect", "rememberCoroutineScope",
            "getIdentifier", "Resources", "AsyncImage",
        ).forEach { assertFalse("$it has no place in the drawer", it in source) }
    }

    /**
     * Rows emit the trusted item and nothing else.
     *
     * `UX-015`'s rule: no raw URI, intent, controller, tab id or generic command may be stored or
     * introduced. The callback type is the whole guarantee, so the assertion is on the signature.
     */
    @Test
    fun `rows dispatch the original trusted NavigationItem`() {
        val source = drawerSource()

        assertTrue("onSelect: (NavigationItem) -> Unit" in source)
        listOf("Intent(", "Uri.parse", "ActionResolver", "startActivity").forEach {
            assertFalse("$it belongs to the browser's dispatcher, not to a list row", it in source)
        }
    }

    /**
     * Site colours only, from the guarded scheme.
     *
     * `SiteSkinColorScheme` is the complete website-influenceable vocabulary and every pair in it
     * has already cleared `SKIN-001`'s contrast guard. A raw `Color(0x…)` here would be an
     * unmeasured colour, and a `WeboraTheme` token painted on a site-chosen ground would be
     * `UX-002`'s `C2` violation arriving through the one panel the site paints.
     */
    @Test
    fun `no unmeasured colour is constructed in the drawer`() {
        val source = drawerSource()

        assertFalse("Color(0x" in source)
        assertFalse("Color.White" in source)
        assertFalse("Color.Black" in source)
        assertTrue("colors.primary" in source)
        assertTrue("colors.onSecondary" in source)
    }

    /**
     * The three groups are the model's three collections, each read exactly once.
     *
     * A group rendered from a re-derived list — `configuration.menu` rather than `model.siteMenu` —
     * would bypass `SiteSkinChromeModel`'s caps and `accessibleLabel`'s re-bounding, which is the
     * whole point of the projection existing.
     */
    @Test
    fun `each group reads one bounded collection from the projected model`() {
        val source = drawerSource()

        listOf("model.quickActions", "model.bottomNavigation", "model.siteMenu").forEach {
            assertEquals("$it must be read exactly once", 1, source.windowed(it.length).count { w -> w == it })
        }
        assertFalse(
            "a group built from the configuration bypasses the model's caps and label bounds",
            "configuration." in source,
        )
    }

    /**
     * Only navigation rows publish selection, and the guard is the model's own flag.
     *
     * `UX-015`'s split: a quick action and a menu entry are actions, not routes, so `selected` and a
     * selected/not-selected `stateDescription` would claim a state they cannot be in.
     * [SiteSkinItemModel.isNavigation] records which a row is — set by whether `NavMatcher` was
     * consulted for it at all — so the assertion is that both semantics properties sit behind that
     * flag and neither escapes it.
     *
     * Written against the row body rather than the file because `isNavigation` legitimately appears
     * nowhere else, and a whole-file scan could not tell an unconditional `selected` from a guarded
     * one. Its negative control is replacing the condition with `true`, which compiles and which the
     * first version of this file did not catch.
     */
    @Test
    fun `selection semantics are reachable only for navigation rows`() {
        val source = rowSource()

        assertTrue("the scan must see the row's real code", "Surface(" in source)
        val guard = source.indexOf("if (item.isNavigation) {")
        assertTrue("both semantics properties must sit behind the navigation flag", guard >= 0)
        assertTrue("selected is claimed outside the guard", source.indexOf("selected = ") > guard)
        assertTrue("stateDescription is claimed outside the guard", source.indexOf("stateDescription = ") > guard)
        assertEquals("one guard, one claim", 1, source.split("selected = ").size - 1)
        assertEquals("one guard, one claim", 1, source.split("stateDescription = ").size - 1)
    }

    /**
     * `actionBouquet()` is not consulted here, and this is the assertion that keeps it unedited.
     *
     * The bouquet's five-item cap is its own presentation decision. A drawer that reused it would
     * inherit the truncation this ticket exists to remove, and a drawer that *changed* it would
     * change the bouquet — `SiteSkinChromeModelTest` passes unedited precisely because neither
     * happens.
     */
    @Test
    fun `the drawer does not reuse the bouquet projection`() {
        assertFalse("actionBouquet" in drawerSource())
    }

    /**
     * Every entry the model carries is rendered; the drawer adds no cap of its own.
     *
     * `SPEC.md` §8 permits twenty `menu` entries and `SiteSkinChromeModel` already bounds them to
     * twenty. A `take(` here would be a second cap silently narrower than the first — which is the
     * defect this ticket removes, reintroduced one layer down where nobody would look for it. The
     * model side is asserted for real in `SiteSkinChromeModelTest`; this is the half that says the
     * component does not discard what it was handed.
     */
    @Test
    fun `the drawer caps nothing it was handed`() {
        val source = groupSource()

        assertTrue("the scan must see the group's real code", "items.forEach" in source)
        listOf("take(", "dropLast(", "chunked(", "windowed(", "first(").forEach {
            assertFalse("$it would be a second, narrower cap over the model's own", it in source)
        }
    }

    /**
     * One hub visibility state, read by both surfaces.
     *
     * `UX-015` made hub state "visibility only" with three resets — page generation, active
     * tab/configuration change, and loss of `PROTECTED_INTEGRATED`. A drawer with its own flag would
     * need all three again, and the failure is silent and specific: a tab switch tears down the
     * dock while a drawer nobody reset stays composed over the next origin's page.
     *
     * The gate cannot drive this — the state is a `remember` inside `BrowserScreen` — so the
     * assertion is that no second one is declared. Its negative control is adding
     * `var hubDrawerVisible by remember { mutableStateOf(false) }`, which is exactly the change the
     * rule forbids and exactly what someone would write.
     */
    @Test
    fun `the hub has one visibility state and both surfaces read it`() {
        val source = browserScreenSource()

        // `UX-024` strengthened this rather than replacing it. The rule was "one flag for two site
        // surfaces"; a third overlay — the browser navigation bouquet — would have made the second
        // boolean this test forbids, so the flag became one nullable `IntegratedOverlay` and the
        // rule became "one state for all three". Exclusion is now what the type says instead of what
        // a pair of assignments has to remember in both directions.
        val declarations = Regex("""var\s+\w+\s+by\s+remember\s*\{\s*mutableStateOf<IntegratedOverlay\?>""")
            .findAll(source)
            .count()
        assertEquals("one overlay state serves every integrated overlay", 1, declarations)

        assertTrue("the dock reads it", "siteActionsExpanded = overlay == IntegratedOverlay.SITE_HUB" in source)
        assertTrue("the drawer host reads the same value", "visible = overlay == IntegratedOverlay.SITE_HUB" in source)
        assertTrue(
            "and so does the browser navigation bouquet",
            "expanded = overlay == IntegratedOverlay.BROWSER_NAVIGATION" in source,
        )
        listOf(
            "drawerVisible", "hubVisible", "hubDrawerVisible", "drawerExpanded",
            // The two spellings this ticket would most plausibly have introduced.
            "navigationExpanded by", "siteActionsExpanded by",
        ).forEach {
            assertFalse("$it would be a second overlay state with its own resets to forget", it in source)
        }
    }

    /**
     * The surface is decided once, from the trusted configuration, at the composition that uses it.
     *
     * Both readers take the same `hubSurface` value rather than each calling the policy, so they
     * cannot disagree about which surface is open — and neither can be handed a literal.
     */
    @Test
    fun `both surfaces are told the same resolved surface`() {
        val source = browserScreenSource()

        assertTrue("val hubSurface = integrated.configuration.hubSurface()" in source)
        assertTrue("hubSurface = hubSurface" in source)
        assertTrue("surface = hubSurface" in source)
        assertFalse("a literal here would bypass the policy", "HubSurface.DRAWER" in source)
        assertFalse("a literal here would bypass the policy", "HubSurface.BOUQUET" in source)
    }

    private companion object {
        /**
         * `SiteSkinHubDrawer`'s body alone — the composable that wires the window, and nothing else.
         *
         * **Scoped because `UX-020`'s rule bit this file during its own negative control.** A
         * whole-file `".consumesPanelTaps()" in source` is satisfied by the *declaration*
         * `private fun Modifier.consumesPanelTaps(): Modifier`, so deleting the modifier from the
         * panel left the assertion green — a check that would have passed over exactly the
         * regression it exists to catch. Assert the application, not the constant.
         */
        fun drawerWindowSource(): String = declaration("internal fun SiteSkinHubDrawer(")

        fun browserScreenSource(): String =
            stripComments(File("src/main/java/app/webora/browser/browser/BrowserScreen.kt"))

        /**
         * `HubGroup`'s body alone.
         *
         * Scoped rather than whole-file because the monogram's `text.take(2)` is a legitimate bound
         * on a two-character glyph and has nothing to do with how many items a group renders. A
         * whole-file scan conflates them and fails on correct code — which is how this assertion
         * failed on its first run.
         */
        /** `HubRow`'s body alone, for the same reason [groupSource] is scoped. */
        fun rowSource(): String = declaration("private fun HubRow(")

        fun groupSource(): String = declaration("private fun HubGroup(")

        /** One top-level declaration's body, ending at whatever declaration follows it. */
        fun declaration(header: String): String {
            val source = drawerSource()
            val start = source.indexOf(header)
            check(start >= 0) { "$header not found" }
            val next = listOf("\nprivate fun ", "\ninternal fun ", "\n@Composable")
                .mapNotNull { marker -> source.indexOf(marker, start + 1).takeIf { it >= 0 } }
                .minOrNull()
            return source.substring(start, next ?: source.length)
        }

        fun drawerSource(): String =
            stripComments(File("src/main/java/app/webora/browser/siteskin/SiteSkinHubDrawer.kt"))

        fun stripComments(file: File): String {
            check(file.exists()) { "source not found at ${file.absolutePath}" }
            return file.readLines()
                .filterNot { line ->
                    val trimmed = line.trimStart()
                    trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
                }
                .joinToString("\n")
        }
    }
}
