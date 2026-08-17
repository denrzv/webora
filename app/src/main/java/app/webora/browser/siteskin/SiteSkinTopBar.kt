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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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

@Composable
internal fun SiteSkinTopBar(
    model: SiteSkinTopBarModel,
    presentation: ExpressiveSiteSkinPresentation,
    canGoBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveSiteSkinHeader(presentation, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
            SecurityIdentity(model.security)
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

@Composable
private fun SecurityIdentity(security: SecurityPresentation) {
    // The shared browser-owned copy, exhaustive with no `else`. `siteskin_tls_secure` and
    // `siteskin_tls_not_secure` were verbatim duplicates of the regular-mode strings, so the same
    // guarantee could have drifted between modes by a translation edit; they are gone.
    val transport = stringResource(
        when (security.transportSecurity) {
            TransportSecurity.SECURE -> R.string.security_secure
            TransportSecurity.NOT_SECURE -> R.string.security_not_secure
            TransportSecurity.UNKNOWN -> R.string.security_unknown
            TransportSecurity.TLS_ERROR -> R.string.security_tls_error
        },
    )
    val description = stringResource(R.string.security_description, transport, security.registrableDomain)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = description }
            .testTag(SITESKIN_SECURITY_TAG),
    ) {
        Text(
            transport,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            stringResource(R.string.siteskin_security_separator),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
        )
        Text(
            security.registrableDomain,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
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
