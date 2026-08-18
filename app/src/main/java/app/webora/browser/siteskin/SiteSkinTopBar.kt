package app.webora.browser.siteskin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.webora.browser.R
import app.webora.browser.browser.SecurityPresentation
import app.webora.browser.browser.TransportSecurity
import app.webora.browser.browser.transportLabel
import app.webora.browser.browser.WeboraIconButton
import app.webora.browser.design.WeboraColors
import app.webora.browser.design.WeboraRadius

/**
 * Integrated top chrome: the site's identity row, then the browser's own control row.
 *
 * **Two rows, and the second one is `BROWSE-011`'s answer to a measurement.** Issue #116 sketched
 * Refresh as a trailing icon in the brand row. On the 320 dp host this repository treats as its
 * floor that row has 280 dp, of which Back, the logo and three gaps take 116 — leaving 164 dp for
 * the weighted title and the unweighted trust chip. A 48 dp control plus its gap takes that to 108,
 * while the chip alone wants about 121 for a domain like `denrzv.github.io`. The chip is unweighted
 * and measures first, so it truncates and the site's title measures to *zero* — at default font
 * scale. `UX-023` already records that this row cannot hold four things at 200%; a fifth is not the
 * direction.
 *
 * So the browser's controls get their own row and the brand row is not edited at all, which is what
 * keeps `SITESKIN_SECURITY_TAG`, the chip's ground, its width floor and its declaration order
 * meaning exactly what they meant when `UX-021` accepted them.
 *
 * Back stays in the brand row. `UX-008` and `UX-014` require integrated top chrome to carry a
 * leading Back affordance before site branding; moving it down here to pay for Refresh's width
 * would have been width-neutral and is a navigation-contract reversal, not a side effect of adding
 * a reload command.
 *
 * Neither [canRefresh] nor [onRefresh] is defaulted. `DEVX-003`: an offered browser command whose
 * handler does nothing is the failure the offered list exists to prevent, and a default no-op puts
 * that one forgetful call site away.
 */
@Composable
@Suppress("LongParameterList")
internal fun SiteSkinTopBar(
    model: SiteSkinTopBarModel,
    presentation: ExpressiveSiteSkinPresentation,
    canGoBack: Boolean,
    onBack: () -> Unit,
    canRefresh: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveSiteSkinHeader(presentation, modifier) {
        Column(Modifier.fillMaxWidth()) {
            BrandRow(model, presentation, canGoBack, onBack)
            BrowserControlRow(canRefresh, onRefresh)
        }
    }
}

/**
 * The site's half of the header, plus the browser controls that must precede it.
 *
 * Lifted into its own declaration by `BROWSE-011` so the containing function stays under detekt's
 * `LongMethod` ceiling. Its contents are otherwise unchanged, which is the point: every structural
 * assertion `UX-021` left behind reads this row.
 */
@Composable
private fun BrandRow(
    model: SiteSkinTopBarModel,
    presentation: ExpressiveSiteSkinPresentation,
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().testTag(SITESKIN_BRAND_TAG),
    ) {
        BrowserBack(canGoBack, onBack)
        Spacer(Modifier.width(8.dp))
        BrandLogo(model.brandAsset, presentation.colors)
        Spacer(Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = model.title,
                color = presentation.colors.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            model.subtitle?.let {
                Text(
                    it,
                    color = presentation.colors.onBackground,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Declared *after* the flexible title column and given no weight, so the title yields
        // width to it rather than the other way round. A manifest controls the title's length;
        // if the order were reversed, a long enough one would push the browser's own trust mark
        // out of the header, which is precisely the surface a site must not be able to move.
        SiteSkinSecurityChip(model.security)
    }
}

/**
 * The browser's controls, on their own line inside a header the site paints.
 *
 * Trailing-aligned by `Arrangement.End` rather than by a weighted spacer, and this is deliberate:
 * `SiteSkinTopBarContractTest` locates the brand row's flexible title column by the *first*
 * `Modifier.weight(1f)` in this file, and a second weighted child would make that assertion depend
 * on declaration order for a reason unrelated to what it asserts.
 *
 * The row is composed unconditionally. There is no count, flag, list index or model field through
 * which a manifest could empty it, reorder it or add to it — the reason the isolation test asserts
 * on this declaration's text rather than on a runtime absence.
 */
@Composable
private fun BrowserControlRow(canRefresh: Boolean, onRefresh: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth().testTag(SITESKIN_CONTROLS_TAG),
    ) {
        BrowserControlTile(SITESKIN_REFRESH_TAG) {
            WeboraIconButton(
                icon = R.drawable.ic_reload,
                // The same name regular chrome gives this command. `DEVX-003`: one command does not
                // acquire two names because it is drawn on a second surface.
                contentDescription = stringResource(R.string.reload),
                onClick = onRefresh,
                enabled = canRefresh,
            )
        }
    }
}

@Composable
private fun BrowserBack(canGoBack: Boolean, onBack: () -> Unit) {
    BrowserControlTile(SITESKIN_BACK_TAG) {
        WeboraIconButton(
            icon = R.drawable.ic_back,
            contentDescription = stringResource(R.string.back),
            onClick = onBack,
            enabled = canGoBack,
        )
    }
}

/**
 * One declaration for the browser-owned sub-surface every browser control in this header sits on.
 *
 * `UX-014` records why the tile exists at all: the header's colours are the site's, so a browser
 * control drawn straight onto them would read as the site's too — *"the visual boundary is the
 * ownership boundary"*. It reads `MaterialTheme.colorScheme.surfaceContainer` and nothing from
 * [SiteSkinColorScheme]; `UX-021`'s trust chip grounds elsewhere for a reason specific to the
 * measured `secure`/`notSecure` pair, and that difference is not an inconsistency to tidy away.
 *
 * Shared rather than copied because `UX-021` records what two copies of one rule cost: regular
 * chrome and the integrated chip each carried a verbatim `when`, and a re-pointed branch in one
 * file drifted from the other with nothing failing.
 */
@Composable
private fun BrowserControlTile(tag: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .testTag(tag),
        content = { content() },
    )
}

@Composable
private fun BrandLogo(asset: BrandAsset, colors: SiteSkinColorScheme) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(LOGO_SLOT_SIZE)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.background)
            .clearAndSetSemantics { }
            .testTag(SITESKIN_LOGO_TAG),
    ) {
        when (asset) {
            is BrandAsset.BitmapAsset -> Image(
                bitmap = asset.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(LOGO_SLOT_SIZE),
            )
            is BrandAsset.Monogram -> Text(asset.text, color = colors.onBackground, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The browser's own trust mark, inside a header a manifest paints.
 *
 * `UX-021` replaced a full-width `Secure · domain` row with this. What it kept is the part `ADR-006`
 * requires: the browser-derived registrable domain stays *visible*, not merely announced. The issue
 * asked for a bare shield, and the complaint behind that ask was that the row was heavy — a third of
 * the header — which deleting the row satisfies without deleting the domain. Removing the domain
 * would have left a coloured glyph as the only thing contradicting a manifest-supplied title and
 * logo, and `ADR-006`'s argument for why that is unacceptable is the whole reason the guarantee
 * exists.
 *
 * **Every colour here is compiled, and the ground is not a detail.** `secure` and `notSecure` are
 * documented in `WeboraColorScheme` as "on `container`" and `WeboraColorSchemeTest` measures them
 * against `container` alone. `materialColorScheme` maps `primaryContainer` to `container` — and maps
 * `surfaceContainer` to `chrome`, which is what the deleted row used and what would put a measured
 * colour on an unmeasured ground. Painting instead onto `presentation.colors.background` would put a
 * browser colour on a website-chosen surface with no contrast floor at all: `UX-002`'s `C2`
 * violation arriving through the one element whose entire job is to be trustworthy.
 *
 * The glyph and the domain both carry the state, so meaning never rests on colour alone, and the
 * `when` is exhaustive so a fifth transport state is a compile error rather than a silent neutral.
 */
@Composable
private fun SiteSkinSecurityChip(security: SecurityPresentation) {
    val secure = security.transportSecurity == TransportSecurity.SECURE
    val transport = transportLabel(security.transportSecurity)
    val description = stringResource(R.string.security_description, transport, security.registrableDomain)
    // Read from the compiled palette rather than through `MaterialTheme`, because `secure` has no
    // Material role to be mapped onto — this chip is its first consumer, and `UX-002` compiled and
    // measured the pair before any surface drew it. The dark/light choice is the user's system
    // setting, never a manifest's.
    val browserColors = WeboraColors.scheme(isSystemInDarkTheme())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .widthIn(max = SECURITY_CHIP_MAX_WIDTH)
            .clip(RoundedCornerShape(WeboraRadius.PILL))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = description }
            .testTag(SITESKIN_SECURITY_TAG),
    ) {
        Icon(
            painter = painterResource(
                if (secure) R.drawable.ic_shield_secure else R.drawable.ic_shield_unverified,
            ),
            // Decorative: the row above carries the whole meaning in one browser-authored sentence,
            // and a second description here would make a screen reader read the state twice.
            contentDescription = null,
            tint = if (secure) browserColors.secure else browserColors.notSecure,
            modifier = Modifier.size(SECURITY_SHIELD_SIZE),
        )
        Text(
            security.registrableDomain,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal const val SITESKIN_LOGO_TAG = "siteskin_logo"
internal const val SITESKIN_SECURITY_TAG = "siteskin_security"
internal const val SITESKIN_BACK_TAG = "siteskin_back"
internal const val SITESKIN_BRAND_TAG = "siteskin_brand"
internal const val SITESKIN_CONTROLS_TAG = "siteskin_browser_controls"
internal const val SITESKIN_REFRESH_TAG = "siteskin_refresh"
internal val LOGO_SLOT_SIZE = 40.dp

/**
 * A status indicator, not a control, so the 48 dp target contract does not apply and must not be
 * borrowed here — `MINIMUM_TOUCH_TARGET` has one owner and this is not a second name for it.
 */
private val SECURITY_SHIELD_SIZE = 16.dp

/**
 * Bounds the chip so a long registrable domain cannot consume the row it shares with the title.
 * The domain ellipsizes inside this; the shield never does, because it is laid out first within the
 * chip and the chip is laid out before the title yields.
 *
 * **This number is not what truncates the domain at large font scale, and raising it does not fix
 * that.** `UX-021`'s review proposed raising it so a typical domain survives 200% scale; measuring
 * the row shows the cap is not the binding constraint at either end of the scale. On a 320 dp host
 * the brand row has 280 dp, of which Back, the logo and three gaps take 116 — leaving 164 dp for the
 * title and this chip together. At 100% scale `example.co.uk` needs about 117 dp including the
 * shield and padding, so the cap never binds. At 200% it needs about 198 dp, so *available width*
 * binds at 164 first. Raising the cap to 200 dp therefore widens the chip by 4 dp and takes the
 * title from 4 dp to 0 — trading the site's name away for roughly one more character of domain.
 *
 * The real constraint is that one row cannot hold Back, a logo, a manifest title and a full domain
 * at 200% scale. That is a responsive-layout problem, recorded as `UX-023`, not a constant to tune.
 * What the review was right about is that the old `width > 0f` assertion could not tell a chip
 * showing one character from a healthy one; the instrumented floor now can.
 */
private val SECURITY_CHIP_MAX_WIDTH = 160.dp
