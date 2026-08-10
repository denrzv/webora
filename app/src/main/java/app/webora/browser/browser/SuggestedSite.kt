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

internal val defaultSuggestedSites = listOfNotNull(
    SuggestedSite.create(
        R.string.suggested_bloom_name,
        R.string.suggested_bloom_description,
        "https://bloomflowers.webora.app/",
    ),
    SuggestedSite.create(
        R.string.suggested_pixelplay_name,
        R.string.suggested_pixelplay_description,
        "https://pixelplay.webora.app/",
    ),
    SuggestedSite.create(
        R.string.suggested_journal_name,
        R.string.suggested_journal_description,
        "https://journal.webora.app/",
    ),
)

private fun URI.isSafeSuggestion(): Boolean =
    scheme == "https" && host != null && rawUserInfo == null && rawFragment == null
