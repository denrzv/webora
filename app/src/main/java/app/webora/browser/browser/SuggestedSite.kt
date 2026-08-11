package app.webora.browser.browser

import androidx.annotation.StringRes
import app.webora.browser.R
import java.net.URI

/**
 * A browser-owned suggestion. Copy is held as resource ids rather than strings so the catalogue is
 * localizable and so no display text can be constructed at a call site — the type system, not a
 * convention, keeps a literal out of this surface.
 */
@ConsistentCopyVisibility
internal data class SuggestedSite private constructor(
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val url: String,
) {
    companion object {
        fun create(@StringRes nameRes: Int, @StringRes descriptionRes: Int, url: String): SuggestedSite? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (!uri.isSafeSuggestion()) return null
            return SuggestedSite(nameRes, descriptionRes, url)
        }
    }
}

/**
 * The reference integration, and deliberately the only entry.
 *
 * `SCOPE-001` narrowed the demo fleet to Bloom Flowers, so PixelPlay and Daily Journal were removed
 * rather than left pointing at hosts that will never resolve — a suggestion the browser itself
 * cannot reach teaches the home screen to lie.
 */
internal val defaultSuggestedSites = listOfNotNull(
    SuggestedSite.create(
        R.string.suggested_bloom_name,
        R.string.suggested_bloom_description,
        "https://denrzv.github.io/",
    ),
)

private fun URI.isSafeSuggestion(): Boolean =
    scheme == "https" && host != null && rawUserInfo == null && rawFragment == null
