package dev.siteskin.core.nav

import dev.siteskin.core.model.NavigationItem
import dev.siteskin.core.model.NormalizedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavMatcherTest {
    @Test fun `exact match beats an earlier and longer glob`() {
        val items = listOf(
            item("glob", "/catalog/products/**"),
            item("exact", "/catalog/products/featured"),
        )

        assertEquals("exact", NavMatcher.activeItem(items, url("/catalog/products/featured"))?.id)
    }

    @Test fun `longest literal prefix wins then earliest item breaks ties`() {
        val items = listOf(
            item("broad", "/cart/**"),
            item("first-specific", "/cart/checkout/*"),
            item("later-specific", "/cart/checkout/**"),
        )

        assertEquals("first-specific", NavMatcher.activeItem(items, url("/cart/checkout/pay"))?.id)
    }

    @Test fun `single star stays within one segment and may match nothing`() {
        val item = item("product", "/products/*/details")

        assertEquals("product", NavMatcher.activeItem(listOf(item), url("/products/42/details"))?.id)
        assertEquals("product", NavMatcher.activeItem(listOf(item), url("/products//details"))?.id)
        assertNull(NavMatcher.activeItem(listOf(item), url("/products/42/reviews/details")))
    }

    @Test fun `double star matches zero or more whole segments`() {
        val item = item("cart", "/cart/**")

        listOf("/cart", "/cart/", "/cart/item", "/cart/item/options").forEach { path ->
            assertEquals(path, "cart", NavMatcher.activeItem(listOf(item), url(path))?.id)
        }
        assertNull(NavMatcher.activeItem(listOf(item), url("/cartridge")))
    }

    @Test fun `non star glob metacharacters are literal`() {
        val items = listOf(
            item("question", "/search/?"),
            item("brackets", "/items/[a-z]"),
            item("braces", "/items/{one,two}"),
        )

        assertEquals("question", NavMatcher.activeItem(items, url("/search/%3F"))?.id)
        assertEquals("brackets", NavMatcher.activeItem(items, url("/items/%5Ba-z%5D"))?.id)
        assertEquals("braces", NavMatcher.activeItem(items, url("/items/%7Bone,two%7D"))?.id)
        assertNull(NavMatcher.activeItem(items, url("/items/a")))
    }

    @Test fun `query and fragment do not participate`() {
        val item = item("catalog", "/catalog")

        assertEquals(
            "catalog",
            NavMatcher.activeItem(listOf(item), "https://shop.example/catalog?next=/cart#details")?.id,
        )
    }

    @Test fun `invalid unsupported and unmatched URLs have no default`() {
        val items = listOf(item("home", "/"), item("catalog", "/catalog"))

        listOf("not a url", "mailto:user@example.com", "file:///catalog", "/catalog").forEach { currentUrl ->
            assertNull(currentUrl, NavMatcher.activeItem(items, currentUrl))
        }
        assertNull(NavMatcher.activeItem(items, url("/missing")))
        assertNull(NavMatcher.activeItem(emptyList(), url("/")))
    }

    @Test fun `repeated wildcards remain deterministic`() {
        val repeated = (1..64).joinToString("/") { "**" }
        val path = (1..64).joinToString("/", prefix = "/") { "segment" }

        assertEquals("many", NavMatcher.activeItem(listOf(item("many", "/$repeated")), url(path))?.id)
    }

    private fun item(id: String, vararg match: String): NavigationItem = NavigationItem(
        id = id,
        label = id,
        icon = null,
        action = NormalizedAction("refresh", null, null),
        match = match.toList(),
    )

    private fun url(path: String): String = "https://shop.example$path"
}
