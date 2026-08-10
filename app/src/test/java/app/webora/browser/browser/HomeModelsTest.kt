package app.webora.browser.browser

import app.webora.browser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModelsTest {
    @Test
    fun `returning users launch home`() {
        assertEquals(LaunchDestination.Home, launchDestination(onboardingCompleted = true))
    }

    @Test
    fun `new users launch onboarding`() {
        assertEquals(LaunchDestination.Onboarding, launchDestination(onboardingCompleted = false))
    }

    @Test
    fun `suggestions accept absolute credential-free HTTPS destinations`() {
        val suggestion = SuggestedSite.create(NAME, DESCRIPTION, "https://bloomflowers.example/")

        assertEquals("https://bloomflowers.example/", suggestion?.url)
    }

    @Test
    fun `suggestions reject unsafe destinations`() {
        val targets = listOf(
            "http://example.com",
            "javascript:alert(1)",
            "/relative",
            "https://user:secret@example.com",
            "https://example.com/#fragment",
            "not a url",
        )

        targets.forEach { target ->
            assertNull(target, SuggestedSite.create(NAME, DESCRIPTION, target))
        }
    }

    @Test
    fun `default suggestions are browser-owned safe destinations`() {
        assertTrue(defaultSuggestedSites.isNotEmpty())
        assertTrue(defaultSuggestedSites.all { resolveAddressInput(it.url) == it.url })
    }

    private companion object {
        val NAME = R.string.suggested_bloom_name
        val DESCRIPTION = R.string.suggested_bloom_description
    }
}
