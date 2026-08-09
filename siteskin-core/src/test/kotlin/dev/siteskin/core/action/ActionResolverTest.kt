package dev.siteskin.core.action

import dev.siteskin.core.model.NormalizedAction
import dev.siteskin.core.model.SiteConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionResolverTest {
    @Test fun `all v1 action types resolve to semantic effects`() {
        val cases = listOf(
            action("internal_url", url = INTERNAL) to ResolvedAction.NavigateInternal(INTERNAL),
            action("external_url", url = EXTERNAL) to ResolvedAction.NavigateExternal(EXTERNAL),
            action("phone", value = "+10000000000") to ResolvedAction.Dial("+10000000000"),
            action("email", value = "hello@example.com") to ResolvedAction.ComposeEmail("hello@example.com"),
            action("map", value = "0,0?q=Example") to ResolvedAction.OpenMap("0,0?q=Example"),
            action("share") to ResolvedAction.Share(CURRENT_PAGE),
            action("home") to ResolvedAction.NavigateInternal(HOME),
            action("refresh") to ResolvedAction.Refresh,
            action("open_menu") to ResolvedAction.OpenMenu,
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, ActionResolver.resolve(input, SITE, CURRENT_PAGE))
        }
    }

    @Test fun `browser context wins over hostile unused action fields`() {
        val hostile = "intent://attacker.example/#Intent;end"

        assertEquals(
            ResolvedAction.NavigateInternal(HOME),
            ActionResolver.resolve(action("home", hostile, hostile), SITE, CURRENT_PAGE),
        )
        assertEquals(
            ResolvedAction.Share(CURRENT_PAGE),
            ActionResolver.resolve(action("share", hostile, hostile), SITE, CURRENT_PAGE),
        )
    }

    @Test fun `unknown types and missing required payloads fail closed`() {
        val inputs = listOf(
            action("open_intent", url = "intent://attacker.example/#Intent;end"),
            action("internal_url"),
            action("external_url"),
            action("phone"),
            action("email"),
            action("map"),
        )

        inputs.forEach { assertNull(ActionResolver.resolve(it, SITE, CURRENT_PAGE)) }
    }

    private fun action(type: String, url: String? = null, value: String? = null): NormalizedAction =
        NormalizedAction(type, url, value)

    private companion object {
        const val INTERNAL = "https://site.example/catalog"
        const val EXTERNAL = "https://elsewhere.example/"
        const val HOME = "https://site.example/"
        const val CURRENT_PAGE = "https://site.example/products/1"
        val SITE = SiteConfiguration("site", "Site", null, HOME)
    }
}
