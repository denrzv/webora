package dev.siteskin.core.nav

import dev.siteskin.core.model.NavigationItem
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Selects the one trusted navigation item active for a browser-observed page URL. */
public object NavMatcher {
    /**
     * Returns the highest-precedence matching [NavigationItem], or `null` when the URL is invalid
     * or no item matches. Query and fragment components never participate in matching.
     */
    public fun activeItem(items: List<NavigationItem>, currentPageUrl: String): NavigationItem? {
        val path = pagePath(currentPageUrl) ?: return null
        var best: Candidate? = null

        items.forEachIndexed { itemIndex, item ->
            item.match.forEach { pattern ->
                if (matches(pattern, path)) {
                    val candidate = Candidate(
                        item = item,
                        itemIndex = itemIndex,
                        exact = '*' !in pattern,
                        literalPrefixLength = pattern.indexOf('*').takeIf { it >= 0 } ?: pattern.length,
                    )
                    if (best?.let(candidate::precedes) != false) best = candidate
                }
            }
        }
        return best?.item
    }

    private fun pagePath(url: String): String? {
        val uri = try {
            URI(url)
        } catch (exception: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (!isSupportedPage(uri, scheme)) return null
        return uri.path?.ifEmpty { ROOT_PATH }
    }

    private fun isSupportedPage(uri: URI, scheme: String?): Boolean {
        if (scheme != HTTPS && scheme != HTTP) return false
        return uri.rawAuthority != null && !uri.isOpaque
    }

    private fun matches(pattern: String, path: String): Boolean {
        if (!pattern.startsWith(ROOT_PATH) || !path.startsWith(ROOT_PATH)) return false
        if ('*' !in pattern) return pattern == path

        val patternSegments = pattern.drop(1).split('/')
        val pathSegments = path.drop(1).split('/')
        var reachable = BooleanArray(pathSegments.size + 1).also { it[0] = true }

        patternSegments.forEach { patternSegment ->
            reachable = advancePath(patternSegment, pathSegments, reachable)
        }
        return reachable[pathSegments.size]
    }

    private fun advancePath(
        pattern: String,
        path: List<String>,
        reachable: BooleanArray,
    ): BooleanArray {
        val next = BooleanArray(path.size + 1)
        if (pattern == DOUBLE_STAR) {
            next[0] = reachable[0]
            for (index in path.indices) next[index + 1] = reachable[index + 1] || next[index]
        } else {
            for (index in path.indices) {
                next[index + 1] = reachable[index] && segmentMatches(pattern, path[index])
            }
        }
        return next
    }

    private fun segmentMatches(pattern: String, segment: String): Boolean {
        var reachable = BooleanArray(segment.length + 1).also { it[0] = true }
        pattern.forEach { token ->
            val next = BooleanArray(segment.length + 1)
            if (token == STAR) {
                next[0] = reachable[0]
                for (index in segment.indices) next[index + 1] = reachable[index + 1] || next[index]
            } else {
                for (index in segment.indices) next[index + 1] = reachable[index] && segment[index] == token
            }
            reachable = next
        }
        return reachable[segment.length]
    }

    private data class Candidate(
        val item: NavigationItem,
        val itemIndex: Int,
        val exact: Boolean,
        val literalPrefixLength: Int,
    ) {
        fun precedes(other: Candidate): Boolean = when {
            exact != other.exact -> exact
            literalPrefixLength != other.literalPrefixLength -> literalPrefixLength > other.literalPrefixLength
            else -> itemIndex < other.itemIndex
        }
    }

    private const val HTTP = "http"
    private const val HTTPS = "https"
    private const val ROOT_PATH = "/"
    private const val DOUBLE_STAR = "**"
    private const val STAR = '*'
}
