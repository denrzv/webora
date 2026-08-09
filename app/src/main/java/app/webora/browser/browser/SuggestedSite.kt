package app.webora.browser.browser

import java.net.URI

@ConsistentCopyVisibility
internal data class SuggestedSite private constructor(
    val name: String,
    val description: String,
    val url: String,
) {
    companion object {
        fun create(name: String, description: String, url: String): SuggestedSite? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (!uri.isSafeSuggestion()) return null
            return SuggestedSite(name, description, url)
        }
    }
}

internal val defaultSuggestedSites = listOfNotNull(
    SuggestedSite.create("Bloom Flowers", "Fresh flowers delivered today", "https://bloomflowers.example/"),
    SuggestedSite.create("PixelPlay", "Discover independent games", "https://pixelplay.webora.app/"),
    SuggestedSite.create("Daily Journal", "A focused place to write", "https://journal.webora.app/"),
)

private fun URI.isSafeSuggestion(): Boolean =
    scheme == "https" && host != null && rawUserInfo == null && rawFragment == null
