package app.webora.browser.siteskin

import androidx.compose.ui.graphics.Color
import dev.siteskin.core.SiteSkinLimits
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.model.SiteSkinConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bounds on what a website may place inside the dialog that decides whether to trust it.
 *
 * Configurations are built by running real manifests through the real validator, so anything these
 * cases prove is proven about text that has already passed core — which is the point. Core clamps
 * the subtitle and item labels and does not clamp `site.name`, so several of these bounds exist
 * only here.
 */
class SiteSkinConsentModelTest {

    @Test fun `an unbounded site name is bounded before it can reach a consent decision`() {
        // site.name is schema-required and core never clamps it — SecurityValidator clamps the
        // subtitle and item labels and stops there. This is the only bound it has.
        val configuration = configuration(name = "N".repeat(200))

        val model = SiteSkinConsentModel.from(configuration, darkTheme = false)

        assertEquals(SiteSkinLimits.MAX_TITLE_LENGTH, model.title.length)
    }

    @Test fun `a bidi override in the site name cannot reorder the browser's own copy`() {
        val configuration = configuration(name = "‮Your Bank")

        val model = SiteSkinConsentModel.from(configuration, darkTheme = false)

        assertFalse(model.title.any { Character.getType(it) == Character.FORMAT.toInt() })
        assertEquals("Your Bank", model.title)
    }

    @Test fun `a site name cannot open a second line in the sheet`() {
        val configuration = configuration(name = "Bloom Flowers\nVerified by Webora")

        val model = SiteSkinConsentModel.from(configuration, darkTheme = false)

        assertFalse("a consent line must never break: ${model.title}", model.title.any { it == '\n' })
        assertEquals("Bloom Flowers Verified by Webora", model.title)
    }

    @Test fun `the sheet promises the title the chrome would actually render`() {
        val model = SiteSkinConsentModel.from(
            configuration(name = "Bloom Flowers", title = "Bloom"),
            darkTheme = false,
        )

        assertEquals("Bloom", model.title)
    }

    @Test fun `without a toolbar title the site name is the promise`() {
        val model = SiteSkinConsentModel.from(configuration(name = "Bloom Flowers"), darkTheme = false)

        assertEquals("Bloom Flowers", model.title)
        assertNull(model.subtitle)
    }

    @Test fun `a subtitle is carried when present and never invented when absent`() {
        val withSubtitle = SiteSkinConsentModel.from(
            configuration(title = "Bloom", subtitle = "Fresh today"),
            darkTheme = false,
        )

        assertEquals("Fresh today", withSubtitle.subtitle)
        assertNull(SiteSkinConsentModel.from(configuration(title = "Bloom"), darkTheme = false).subtitle)
    }

    @Test fun `a site that asked for no colour is not credited with Webora's own`() {
        // SiteSkinTheme.from fills omissions with compiled defaults, so an unconditional swatch
        // would show Webora's #3F51B5 under a heading attributing it to the site.
        val model = SiteSkinConsentModel.from(configuration(), darkTheme = false)

        assertNull(model.brandColor)
    }

    @Test fun `a requested colour is previewed as the chrome would paint it`() {
        val configuration = configuration(primaryColor = "#D94F8A")

        val model = SiteSkinConsentModel.from(configuration, darkTheme = false)

        assertEquals(SiteSkinTheme.from(configuration).scheme(darkTheme = false).primary, model.brandColor)
    }

    @Test fun `an unreadable requested colour is never previewed as requested`() {
        // White on white passes no contrast target. Core corrects it and the app layer guards it
        // again; the swatch must show the outcome, not the request.
        val model = SiteSkinConsentModel.from(
            configuration(primaryColor = "#FFFFFF", textColor = "#FFFFFF"),
            darkTheme = false,
        )

        assertNotEquals(Color.White, model.brandColor)
    }

    @Test fun `the browser's dark choice selects the projection, not the manifest`() {
        val configuration = configuration(primaryColor = "#D94F8A")

        assertEquals(
            SiteSkinTheme.from(configuration).scheme(darkTheme = true).primary,
            SiteSkinConsentModel.from(configuration, darkTheme = true).brandColor,
        )
    }

    @Test fun `counts describe the chrome that would render, not the manifest that was fetched`() {
        val model = SiteSkinConsentModel.from(
            configuration(navigation = 6, quickActions = 6, menu = 21),
            darkTheme = false,
        )

        assertEquals(SiteSkinLimits.MAX_NAVIGATION_ITEMS, model.navigationCount)
        assertEquals(SiteSkinLimits.MAX_QUICK_ACTIONS, model.quickActionCount)
        assertEquals(SiteSkinLimits.MAX_MENU_ITEMS, model.menuCount)
    }

    @Test fun `counts under the cap are reported as they are`() {
        val model = SiteSkinConsentModel.from(configuration(navigation = 3, quickActions = 1), darkTheme = false)

        assertEquals(3, model.navigationCount)
        assertEquals(1, model.quickActionCount)
        assertEquals(0, model.menuCount)
    }

    @Test fun `a manifest with every optional field absent still yields a complete sheet`() {
        val model = SiteSkinConsentModel.from(configuration(), darkTheme = false)

        assertTrue(model.title.isNotEmpty())
        assertNull(model.subtitle)
        assertNull(model.brandColor)
        assertEquals(0, model.navigationCount)
        assertEquals(0, model.quickActionCount)
        assertEquals(0, model.menuCount)
    }

    @Suppress("LongParameterList")
    private fun configuration(
        name: String = "Bloom Flowers",
        title: String? = null,
        subtitle: String? = null,
        primaryColor: String? = null,
        textColor: String? = null,
        navigation: Int = 0,
        quickActions: Int = 0,
        menu: Int = 0,
    ): SiteSkinConfiguration = SiteSkinValidator.validate(
        manifest(name, title, subtitle, primaryColor, textColor, navigation, quickActions, menu)
            .byteInputStream(),
        ORIGIN,
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    @Suppress("LongParameterList")
    private fun manifest(
        name: String,
        title: String?,
        subtitle: String?,
        primaryColor: String?,
        textColor: String?,
        navigation: Int,
        quickActions: Int,
        menu: Int,
    ): String {
        val toolbar = listOfNotNull(
            title?.let { """"title":"${it.escaped()}"""" },
            subtitle?.let { """"subtitle":"${it.escaped()}"""" },
        ).takeIf(List<String>::isNotEmpty)?.joinToString()?.let { ""","toolbar":{$it}""" }.orEmpty()
        val branding = listOfNotNull(
            primaryColor?.let { """"primaryColor":"$it"""" },
            textColor?.let { """"textColor":"$it"""" },
        ).takeIf(List<String>::isNotEmpty)?.joinToString()?.let { ""","branding":{$it}""" }.orEmpty()
        return """{"schemaVersion":"1.0","site":{"id":"bloom","name":"${name.escaped()}"}""" +
            toolbar + branding +
            items("bottomNavigation", navigation) +
            items("quickActions", quickActions) +
            items("menu", menu) +
            "}"
    }

    private fun items(collection: String, count: Int): String {
        if (count == 0) return ""
        val entries = (1..count).joinToString {
            """{"id":"i$it","label":"Item $it","action":{"type":"internal_url","url":"/p$it"}}"""
        }
        return ""","$collection":[$entries]"""
    }

    /** JSON string escaping for the adversarial names; `\n` and `U+202E` must survive into core. */
    private fun String.escaped(): String = buildString {
        this@escaped.forEach { character ->
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character.code < 0x20 || character.code > 0x7E ->
                    append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
    }

    private companion object {
        const val ORIGIN = "https://bloomflowers.example"
    }
}
