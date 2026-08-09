package app.webora.browser.siteskin

import app.webora.browser.browser.SecurityPresentation
import dev.siteskin.core.model.SiteSkinConfiguration

internal data class SiteSkinTopBarModel(
    val title: String,
    val subtitle: String?,
    val brandAsset: BrandAsset,
    val security: SecurityPresentation,
) {
    companion object {
        fun from(
            configuration: SiteSkinConfiguration,
            brandAsset: BrandAsset,
            security: SecurityPresentation,
        ): SiteSkinTopBarModel = SiteSkinTopBarModel(
            title = configuration.toolbar?.title ?: configuration.site.name,
            subtitle = configuration.toolbar?.subtitle,
            brandAsset = brandAsset,
            security = security,
        )
    }
}
