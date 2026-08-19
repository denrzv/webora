package app.webora.browser.siteskin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.webora.browser.R
import app.webora.browser.browser.browserTouchTarget
import app.webora.browser.design.WeboraRadius
import app.webora.browser.design.WeboraSpacing
import dev.siteskin.core.model.NavigationItem

/**
 * The start-side hub drawer: every trusted site item the browser will dispatch, in three headed
 * groups.
 *
 * **Why a `Dialog` and not `ModalNavigationDrawer`.** This is `UX-018`/`UX-020`'s established
 * list-surface shape, and the deciding property is structural rather than visual: a `Dialog`
 * composes into its own window, so Back is consumed by that window before `BrowserBackHandler` ever
 * sees it. `ModalNavigationDrawer` composes into the same window and would need its own back
 * handler racing the browser's — a second answer to "what does Back do here", which is exactly the
 * kind of duplication `BROWSE-002`'s single Back contract exists to prevent. `BrowserBack.kt` is
 * untouched by this ticket as a result.
 *
 * **Why it exists at all.** `SPEC.md` §8 permits twenty `menu` entries and the bouquet shows five
 * total across all three collections. A site with a real menu had fifteen entries the browser had
 * validated, bounded and then simply never offered. The drawer is the presentation that can promise
 * every permitted entry is reachable, which is also why [resolveHubPresentation] sends `AUTO` here.
 *
 * Everything site-authored arrives already bounded through [SiteSkinChromeModel]; rows emit the
 * original trusted [NavigationItem] and never a URL, an intent or a generic command. Colours come
 * only from the contrast-guarded [SiteSkinColorScheme].
 */
@Composable
internal fun SiteSkinHubDrawer(
    model: SiteSkinChromeModel,
    projectedIds: Set<String>,
    identity: SiteSkinHubIdentity,
    colors: SiteSkinColorScheme,
    onSelect: (NavigationItem) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The panel occupies the start edge and the remainder is scrim. `Alignment.TopStart` resolves
        // its horizontal half against the layout direction, so this is start-side under RTL without
        // a physical left/right constant anywhere in the file. Top rather than centre because a
        // content-sized panel has to leave its scrim somewhere a tap can land.
        //
        // The inset is consumed here, on the box whose constraints the maximum is a fraction of.
        // Inside the panel it would inflate the panel's own height instead of reducing the space it
        // is measured against. `BrowserScreen` consumes `safeDrawing` once for the browser's window;
        // this is a different window, so the two do not compound.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .testTag(SITESKIN_HUB_SCRIM_TAG)
                .hubScrim(onDismiss)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopStart,
        ) {
            val height = hubDrawerHeight(maxHeight)
            Surface(
                modifier = Modifier
                    .heightIn(min = height.min, max = height.max)
                    .widthIn(max = HUB_DRAWER_MAX_WIDTH)
                    .fillMaxWidth(HUB_DRAWER_WIDTH_FRACTION)
                    .consumesPanelTaps()
                    .testTag(SITESKIN_HUB_DRAWER_TAG),
                color = colors.background,
                contentColor = colors.onBackground,
                shape = MaterialTheme.shapes.large,
            ) {
                HubDrawerContent(
                    model = model,
                    projectedIds = projectedIds,
                    identity = identity,
                    colors = colors,
                    onSelect = onSelect,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/**
 * Composes the drawer when it is the resolved surface, and nothing otherwise.
 *
 * The condition lives here rather than at the call site so each hub surface owns its own visibility
 * test beside itself — `SiteSkinDock` holds the mirror-image one for the bouquet. Both read the same
 * [visible] flag, which is the browser's single hub state with its three existing resets; neither
 * introduces a second one, so the two surfaces cannot both be open and cannot disagree about being
 * closed.
 */
@Composable
internal fun SiteSkinHubHost(
    visible: Boolean,
    surface: HubSurface,
    model: SiteSkinChromeModel,
    projectedIds: Set<String>,
    identity: SiteSkinHubIdentity,
    colors: SiteSkinColorScheme,
    onSelect: (NavigationItem) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible || surface != HubSurface.DRAWER) return
    SiteSkinHubDrawer(model, projectedIds, identity, colors, onSelect, onDismiss)
}

/**
 * The drawer body, separated from its window so the JVM gate and instrumentation can drive it
 * directly — the `PrivacySettingsDialog`/`PrivacySettingsScreen` split, for the same reason.
 *
 * The whole column scrolls. Twenty menu entries plus five navigation items plus five quick actions
 * cannot fit a 320 dp host at 200% font scale, and truncating them would reintroduce the defect this
 * ticket removes at a different scale.
 */
@Composable
internal fun HubDrawerContent(
    model: SiteSkinChromeModel,
    projectedIds: Set<String>,
    identity: SiteSkinHubIdentity,
    colors: SiteSkinColorScheme,
    onSelect: (NavigationItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val select: (NavigationItem) -> Unit = { item ->
        onDismiss()
        onSelect(item)
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(WeboraSpacing.GUTTER),
        verticalArrangement = Arrangement.spacedBy(WeboraSpacing.LARGE),
    ) {
        HubHeader(identity, colors)
        // `UX-025`: an item the dock already shows is not repeated here.
        //
        // **Subtraction, not deletion.** `SiteSkinChromeModel` still carries every item, so
        // `NavMatcher` still resolves the active route and dispatch still reaches everything. Only
        // this projection hides what the dock rendered — which is also what makes the fallback
        // automatic: an id that failed to project is not in `projectedIds`, so it is still listed
        // here rather than gone from every surface.
        HubGroup(
            R.string.siteskin_quick_actions, model.quickActions - projectedIds,
            colors, SITESKIN_HUB_ACTIONS_TAG, select,
        )
        HubGroup(
            R.string.siteskin_site_menu_heading, model.bottomNavigation - projectedIds,
            colors, SITESKIN_HUB_NAV_TAG, select,
        )
        HubGroup(
            R.string.siteskin_more_from_site, model.siteMenu - projectedIds,
            colors, SITESKIN_HUB_MENU_TAG, select,
        )
    }
}

/**
 * One headed group, or nothing at all.
 *
 * An empty group renders no heading rather than an empty one: a "Quick actions" heading over
 * nothing tells the user the site offered something the browser dropped, which is a claim this
 * component has no evidence for.
 */
@Composable
private fun HubGroup(
    heading: Int,
    items: List<SiteSkinItemModel>,
    colors: SiteSkinColorScheme,
    tag: String,
    onSelect: (NavigationItem) -> Unit,
) {
    if (items.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
    ) {
        Text(
            stringResource(heading),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground,
        )
        items.forEach { item -> HubRow(item, colors, onSelect) }
    }
}

/**
 * One trusted item as a tonal row.
 *
 * **Only navigation rows publish selection.** `UX-015` fixed that split and it holds here: a quick
 * action and a menu entry are actions, not routes, so `selected` and a selected/not-selected
 * `stateDescription` would claim a state they cannot be in. [SiteSkinItemModel.isNavigation] is the
 * model's own record of which it is, set by whether `NavMatcher` was consulted for the item at all.
 *
 * **The active pair is `primary`/`onPrimary` against `secondary`/`onSecondary`,** which is what
 * `ActionPetal` already uses and `SKIN-001` already guards to 4.5:1. Never an alpha multiplier, for
 * `UX-003`'s reason, and never colour alone — the `stateDescription` carries the state in the
 * channel a screen reader reads.
 */
@Composable
private fun HubRow(
    item: SiteSkinItemModel,
    colors: SiteSkinColorScheme,
    onSelect: (NavigationItem) -> Unit,
) {
    val selectedState = stringResource(R.string.siteskin_nav_selected)
    val notSelected = stringResource(R.string.siteskin_nav_not_selected)
    Surface(
        onClick = { onSelect(item.item) },
        modifier = Modifier
            .fillMaxWidth()
            .browserTouchTarget()
            .testTag("$SITESKIN_HUB_ROW_TAG_PREFIX${item.id}")
            .semantics {
                contentDescription = item.label
                if (item.isNavigation) {
                    selected = item.isActive
                    stateDescription = if (item.isActive) selectedState else notSelected
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = if (item.isActive) colors.primary else colors.secondary,
        contentColor = if (item.isActive) colors.onPrimary else colors.onSecondary,
    ) {
        Row(
            modifier = Modifier.padding(WeboraSpacing.MEDIUM),
            horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.MEDIUM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SiteSkinIcon(item.icon)
            Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The site's own identity, beside its already-published brand asset.
 *
 * The asset is the one [BrandAsset] the running browser already decided on — passed in, never
 * fetched here. A second loader would be a second answer to `NET-003`'s same-origin recheck and
 * `NET-004`'s publication guard, and would show a logo this configuration never earned.
 *
 * This carries no origin and no TLS state, deliberately. `ADR-006` puts that guarantee in the
 * browser-owned header, where `UX-021`'s chip is visible the whole time this drawer is open; adding
 * a second identity surface inside a site-coloured panel would put the browser's trust mark on a
 * ground the site chose.
 */
@Composable
private fun HubHeader(identity: SiteSkinHubIdentity, colors: SiteSkinColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(SITESKIN_HUB_HEADER_TAG),
        horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.MEDIUM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HubBrandMark(identity.asset, colors)
        Text(
            identity.name,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The bounded brand slot, decorative in the semantics tree exactly as `HARDEN-002` requires. */
@Composable
private fun HubBrandMark(asset: BrandAsset, colors: SiteSkinColorScheme) {
    Box(
        modifier = Modifier
            .size(HUB_BRAND_SLOT_SIZE)
            .clip(CircleShape)
            .background(colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        HubBrandGlyph(asset, colors.onPrimary)
    }
}

@Composable
private fun HubBrandGlyph(asset: BrandAsset, contentColor: Color) {
    when (asset) {
        is BrandAsset.BitmapAsset -> Image(
            bitmap = asset.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape).clearAndSetSemantics { },
        )
        is BrandAsset.Monogram -> if (asset.text.isNotBlank()) {
            Text(
                text = asset.text.take(MONOGRAM_LENGTH),
                color = contentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics { },
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_siteskin_flower),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(HUB_BRAND_GLYPH_SIZE).clearAndSetSemantics { },
            )
        }
    }
}

/**
 * What the drawer header may show about the site.
 *
 * A closed two-field record rather than the `SiteSkinConfiguration` itself. The configuration also
 * carries URLs, actions and colours, and a component holding one is a component that can reach for
 * them; this is the `SiteSkinConsentModel.from` shape, for the same reason.
 */
@ConsistentCopyVisibility
internal data class SiteSkinHubIdentity private constructor(val name: String, val asset: BrandAsset) {
    companion object {
        /**
         * The only constructor, so the bound cannot be skipped.
         *
         * `accessibleLabel` re-bounds the site name for the reason `A11Y-001` records: core already
         * truncated it, and the visual bound here is one line and an ellipsis, which is not a bound
         * at all in the channel a screen reader reads.
         */
        fun from(name: String, asset: BrandAsset): SiteSkinHubIdentity =
            SiteSkinHubIdentity(accessibleLabel(name), asset)
    }
}

/**
 * The scrim's dismissal, which the browser owns because the framework's cannot reach it.
 *
 * `DialogProperties.dismissOnClickOutside` is true here and is *honoured* — it simply never fires.
 * `DialogWrapper.onTouchEvent` consults `DialogLayout.isInsideContent`, which resolves its rectangle
 * from the composed content's first child; this drawer's content fills the window, so every touch
 * the window receives is "inside" and the dismissal branch is unreachable. Before `UX-026` that made
 * [SITESKIN_HUB_SCRIM_TAG] a rectangle which looked like a scrim, absorbed the tap and did nothing
 * with it, leaving "select a row and navigate" as the only reliable way out of the menu.
 *
 * **A gesture rather than `clickable`, and that is an accessibility decision.** A full-screen
 * `clickable` publishes a semantics node with a click role and an accessible name, so assistive
 * technology would meet a screen-sized button in front of the menu and have to traverse past it to
 * reach the rows. `pointerInput` contributes no semantics node at all — and Back, which `UX-022`'s
 * dialog window already consumes, remains the accessible dismissal path, so the scrim is never the
 * only route out.
 *
 * Pairs with [consumesPanelTaps]: this node is the panel's ancestor, so it is on the hit path for
 * taps on the panel too.
 */
private fun Modifier.hubScrim(onDismiss: () -> Unit): Modifier =
    pointerInput(onDismiss) { detectTapGestures { onDismiss() } }

/**
 * Everything the panel's own children did not take, so a tap on it cannot reach [hubScrim].
 *
 * Compose runs the Main pass children-first, so rows, headings and the scroll all see a tap before
 * this node does, and `detectTapGestures` on the ancestor awaits an **unconsumed** down. Consuming
 * the remainder here is therefore precisely "the panel handled it", and without it a tap on empty
 * panel space — beside a heading, below the last row — would fall through and close the drawer under
 * the user's finger.
 *
 * Consuming an already-consumed change is a no-op, so this neither competes with the rows nor
 * interferes with scrolling.
 */
private fun Modifier.consumesPanelTaps(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { change -> change.consume() }
        }
    }
}

/** Hides the ids the dock already renders, without touching the model they came from. */
private operator fun List<SiteSkinItemModel>.minus(projected: Set<String>): List<SiteSkinItemModel> =
    filterNot { it.id in projected }

internal const val SITESKIN_HUB_DRAWER_TAG = "siteskin_hub_drawer"
internal const val SITESKIN_HUB_SCRIM_TAG = "siteskin_hub_scrim"
internal const val SITESKIN_HUB_HEADER_TAG = "siteskin_hub_header"
internal const val SITESKIN_HUB_ACTIONS_TAG = "siteskin_hub_quick_actions"
internal const val SITESKIN_HUB_NAV_TAG = "siteskin_hub_navigation"
internal const val SITESKIN_HUB_MENU_TAG = "siteskin_hub_menu"
internal const val SITESKIN_HUB_ROW_TAG_PREFIX = "siteskin_hub_row_"
private val HUB_DRAWER_MAX_WIDTH = 360.dp
private const val HUB_DRAWER_WIDTH_FRACTION = 0.88f
private val HUB_BRAND_SLOT_SIZE = 48.dp
private val HUB_BRAND_GLYPH_SIZE = 28.dp
private const val MONOGRAM_LENGTH = 2
