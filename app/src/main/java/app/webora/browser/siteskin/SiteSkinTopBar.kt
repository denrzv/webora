package app.webora.browser.siteskin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.webora.browser.R
import app.webora.browser.browser.SecurityPresentation
import app.webora.browser.browser.TransportSecurity

@Composable
internal fun SiteSkinTopBar(
    model: SiteSkinTopBarModel,
    colors: SiteSkinColorScheme,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOP_BAR_MIN_HEIGHT)
            .background(colors.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BrandLogo(model.brandAsset, colors)
        Spacer(Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = model.title,
                color = colors.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            model.subtitle?.let {
                Text(it, color = colors.onBackground, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SecurityIdentity(model.security, colors)
        }
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

@Composable
private fun SecurityIdentity(security: SecurityPresentation, colors: SiteSkinColorScheme) {
    val transport = when (security.transportSecurity) {
        TransportSecurity.SECURE -> stringResource(R.string.siteskin_tls_secure)
        TransportSecurity.NOT_SECURE -> stringResource(R.string.siteskin_tls_not_secure)
    }
    val description = stringResource(R.string.siteskin_security_description, transport, security.registrableDomain)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .semantics { contentDescription = description }
            .testTag(SITESKIN_SECURITY_TAG),
    ) {
        Text(transport, color = colors.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(" · ", color = colors.onBackground, fontSize = 12.sp)
        Text(
            security.registrableDomain,
            color = colors.onBackground,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SiteSkinTopBarPreview() {
    val colors = SiteSkinColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFD94F8A),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        secondary = androidx.compose.ui.graphics.Color(0xFFFADADD),
        onSecondary = androidx.compose.ui.graphics.Color.Black,
        background = androidx.compose.ui.graphics.Color.White,
        onBackground = androidx.compose.ui.graphics.Color.Black,
    )
    SiteSkinTopBar(
        SiteSkinTopBarModel(
            "Bloom Flowers",
            "Fresh flowers delivered today",
            BrandAsset.Monogram("B"),
            SecurityPresentation("bloomflowers.example", TransportSecurity.SECURE),
        ),
        colors,
    )
}

internal const val SITESKIN_LOGO_TAG = "siteskin_logo"
internal const val SITESKIN_SECURITY_TAG = "siteskin_security"
internal val LOGO_SLOT_SIZE = 40.dp
private val TOP_BAR_MIN_HEIGHT = 80.dp
