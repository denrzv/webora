package app.webora.browser.siteskin

import app.webora.browser.browser.SecurityPresentation
import app.webora.browser.browser.TransportSecurity
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SiteSkinTopBarModelTest {
    @Test fun `trusted toolbar and brand asset become bounded presentation`() {
        val configuration = configuration(title = "Bloom Flowers", subtitle = "Fresh today")
        val asset = BrandAsset.Monogram("B")

        val model = SiteSkinTopBarModel.from(configuration, asset, SECURITY)

        assertEquals("Bloom Flowers", model.title)
        assertEquals("Fresh today", model.subtitle)
        assertSame(asset, model.brandAsset)
    }

    @Test fun `missing toolbar uses trusted site name without inventing subtitle`() {
        val model = SiteSkinTopBarModel.from(configuration(), BrandAsset.Monogram("B"), SECURITY)

        assertEquals("Bloom Flowers", model.title)
        assertEquals(null, model.subtitle)
    }

    @Test fun `browser observed identity is structurally preserved independent of branding`() {
        val hostileBrand = configuration(title = "Trusted Bank", subtitle = "Secure account")

        val model = SiteSkinTopBarModel.from(hostileBrand, BrandAsset.Monogram("T"), SECURITY)

        assertEquals("example.co.uk", model.security.registrableDomain)
        assertEquals(TransportSecurity.SECURE, model.security.transportSecurity)
    }

    private fun configuration(title: String? = null, subtitle: String? = null) =
        SiteSkinValidator.validate(
            manifest(title, subtitle).byteInputStream(),
            "https://shop.example.co.uk",
        ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private fun manifest(title: String?, subtitle: String?): String {
        val toolbar = listOfNotNull(
            title?.let { "\"title\":\"$it\"" },
            subtitle?.let { "\"subtitle\":\"$it\"" },
        ).takeIf(List<String>::isNotEmpty)?.joinToString()?.let { ",\"toolbar\":{$it}" }.orEmpty()
        return """{"schemaVersion":"1.0","site":{"id":"bloom","name":"Bloom Flowers"}$toolbar}"""
    }

    private companion object {
        val SECURITY = SecurityPresentation("example.co.uk", TransportSecurity.SECURE)
    }
}
