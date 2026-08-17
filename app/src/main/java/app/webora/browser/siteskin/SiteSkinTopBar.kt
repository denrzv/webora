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
import app.webora.browser.browser.WeboraIconButton
import app.webora.browser.design.WeboraColors
import app.webora.browser.design.WeboraRadius

@Composable
internal fun SiteSkinTopBar(
    model: SiteSkinTopBarModel,
    presentation: ExpressiveSiteSkinPresentation,
    canGoBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveSiteSkinHeader(presentation, modifier) {
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
}

@Composable
private fun BrowserBack(canGoBack: Boolean, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .testTag(SITESKIN_BACK_TAG),
    ) {
        WeboraIconButton(
            icon = R.drawable.ic_back,
            contentDescription = stringResource(R.string.back),
            onClick = onBack,
            enabled = canGoBack,
        )
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
/**
 * The browser-authored word for each transport state.
 *
 * Exhaustive with no `else`, so a fifth state is a compile error rather than a silent fall-through
 * to whichever label the `else` happened to name — on a surface where the wrong label is a false
 * security claim. The same four strings serve regular chrome, so one guarantee cannot be worded two
 * ways depending on which mode the user is in.
 */
@Composable
private fun transportLabel(transport: TransportSecurity): String = stringResource(
    when (transport) {
        TransportSecurity.SECURE -> R.string.security_secure
        TransportSecurity.NOT_SECURE -> R.string.security_not_secure
        TransportSecurity.UNKNOWN -> R.string.security_unknown
        TransportSecurity.TLS_ERROR -> R.string.security_tls_error
    },
)

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
 */
private val SECURITY_CHIP_MAX_WIDTH = 160.dp
