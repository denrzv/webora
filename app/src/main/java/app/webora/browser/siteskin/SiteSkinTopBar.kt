package app.webora.browser.siteskin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
 * Integrated top chrome: the browser's navigation hub, the site's identity, the browser's trust mark.
 *
 * **One row now, where `BROWSE-011` needed two.** That ticket measured the brand row and found it
 * could not take a sixth child — 280 dp of content width at the 320 dp floor, of which Back, the
 * logo and three gaps take 116, leaving 164 dp for the weighted title and the unweighted chip, so a
 * 48 dp control plus its gap truncates the domain and measures the site's title to zero. Its
 * conclusion, *a browser command needed a row of its own*, was correct given that the leading slot
 * already held a single-purpose Back button. `UX-024` removes that premise instead of arguing with
 * the arithmetic: the slot now holds a control that opens Back, Forward **and** Refresh, so the
 * width budget is unchanged and `BrowserControlRow`'s 40 dp goes back to the page.
 *
 * The leading slot itself is unchanged in every dimension — the same 48 dp `BrowserControlTile` in
 * the same position — which is why `headerIdentityPlacement`'s `HEADER_FIXED_WIDTH` did not move.
 * If a future change needs that constant edited, the footprint has moved and `UX-023`'s measured
 * wrap threshold needs re-deriving with it.
 *
 * `UX-008` and `UX-014` require integrated top chrome to carry a leading browser-owned affordance
 * before site branding, with browser-observed enabled state and a browser callback that no manifest
 * seam can suppress, relabel, reorder, restyle or dispatch. Every clause still holds. What changed
 * is that the slot holds a control which *opens* Back rather than *being* Back — and holds two more
 * browser commands besides, which is more browser capability in browser-owned chrome, not less.
 * SiteSkin still does not replace the browser's history contract, which is what those tickets are
 * about.
 */
@Composable
internal fun SiteSkinTopBar(
    model: SiteSkinTopBarModel,
    presentation: ExpressiveSiteSkinPresentation,
    navigation: BrowserNavigationHubState,
    modifier: Modifier = Modifier,
) {
    ExpressiveSiteSkinHeader(presentation, modifier) {
        // The one responsive read in the browser, and both of its inputs are platform facts.
        // `maxWidth` is the header's content width — gutters already removed — and `fontScale` is
        // the user's text-size setting. Neither is anything a website can influence, which is the
        // whole security property of `headerIdentityPlacement`; see its KDoc.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val placement = headerIdentityPlacement(
                availableWidth = maxWidth,
                fontScale = LocalDensity.current.fontScale,
                domainLength = model.security.registrableDomain.length,
            )
            Column(Modifier.fillMaxWidth()) {
                BrandRow(
                    model = model,
                    presentation = presentation,
                    navigation = navigation,
                    inlineIdentity = placement == HeaderIdentityPlacement.INLINE,
                )
                if (placement == HeaderIdentityPlacement.OWN_ROW) {
                    SiteSkinIdentityRow(model.security)
                }
            }
        }
    }
}

@Composable
private fun BrandRow(
    model: SiteSkinTopBarModel,
    presentation: ExpressiveSiteSkinPresentation,
    navigation: BrowserNavigationHubState,
    inlineIdentity: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().testTag(SITESKIN_BRAND_TAG),
    ) {
        BrowserNavigationHub(navigation)
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
        // Declared *after* the flexible title column and given no weight, so the title yields
        // width to it rather than the other way round. A manifest controls the title's length;
        // if the order were reversed, a long enough one would push the browser's own trust mark
        // out of the header, which is precisely the surface a site must not be able to move.
        //
        // `UX-023`: when the row cannot hold both, the chip moves to `SiteSkinIdentityRow` rather
        // than rationing width with the title. The condition is browser-owned — width and font
        // scale — never the length of what the site asked to be called.
        if (inlineIdentity) {
            Spacer(Modifier.width(8.dp))
            SiteSkinSecurityChip(model.security)
        }
    }
}

/**
 * The trust chip alone, on a row that carries no site content whatsoever.
 *
 * Composed only when [headerIdentityPlacement] says the brand row cannot hold it. `UX-023` chose a
 * separate row over joining `BROWSE-011`'s browser control row, on a named mechanism: that row
 * declared no weight and used `Arrangement.End`, and the contract test locates the brand row's
 * flexible title column by the file's *first* `Modifier.weight(1f)`, so a weighted passenger would
 * have made a live assertion depend on something unrelated to what it asserts. `UX-024` removed the
 * control row entirely, which makes the question moot rather than the decision wrong — and leaves
 * this row unchanged, composed on the same browser-owned condition it always was.
 *
 * `UX-021`'s guarantee is that a manifest-supplied title cannot push the browser's trust mark out of
 * the header. Here it holds by construction rather than by declaration order — there is no site
 * content in this row for a title to compete with.
 */
@Composable
private fun SiteSkinIdentityRow(security: SecurityPresentation) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        // `Dp.Unspecified`, not the inline cap. `SECURITY_CHIP_MAX_WIDTH` exists to stop the chip
        // starving the site's title *in the brand row*; on a row with no title it has nothing to
        // ration, and leaving it applied would cap the chip at 160 dp while a 13-character domain
        // needs ~208 at 200% — the ellipsis this ticket exists to remove, surviving the wrap that
        // was supposed to remove it. The enclosing `Row`'s own constraint still bounds the chip, so
        // dropping the modifier's cap does not let it overflow the header.
        SiteSkinSecurityChip(security, maxWidth = Dp.Unspecified)
    }
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
private fun SiteSkinSecurityChip(security: SecurityPresentation, maxWidth: Dp = SECURITY_CHIP_MAX_WIDTH) {
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
            .widthIn(max = maxWidth)
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
internal const val SITESKIN_BRAND_TAG = "siteskin_brand"
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
// `internal` so the instrumented case that proves this cap no longer binds on the wrapped row can
// name the constant instead of restating 160 dp — a copy of it in a test is a second place for the
// two to drift.
internal val SECURITY_CHIP_MAX_WIDTH = 160.dp
