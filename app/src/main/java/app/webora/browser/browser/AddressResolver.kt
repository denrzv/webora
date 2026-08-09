package app.webora.browser.browser

import java.net.IDN
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val SEARCH_ENDPOINT = "https://www.google.com/search?q="

internal fun resolveAddressInput(raw: String): String? {
    val input = raw.trim()
    if (input.isEmpty() || input.any(Char::isISOControl)) return null

    val explicit = parseUri(input)
    return when {
        explicit?.scheme != null -> explicit.toSafeWebUrl()
        input.contains("://") -> null
        looksLikeHost(input) -> parseUri("https://$input")?.toSafeWebUrl()
        else -> SEARCH_ENDPOINT + URLEncoder.encode(input, StandardCharsets.UTF_8)
    }
}

private fun URI.toSafeWebUrl(): String? {
    if (scheme?.lowercase() !in setOf("http", "https")) return null
    if (rawUserInfo != null || rawFragment != null || host == null) return null
    return runCatching {
        val asciiHost = IDN.toASCII(host).lowercase()
        URI(scheme.lowercase(), null, asciiHost, port, rawPath.ifEmpty { null }, rawQuery, null)
            .toASCIIString()
    }.getOrNull()
}

private fun looksLikeHost(input: String): Boolean {
    val authority = input.substringBefore('/').substringBefore('?')
    return authority.equals("localhost", ignoreCase = true) ||
        authority.contains('.') ||
        authority.startsWith('[')
}

private fun parseUri(raw: String): URI? = runCatching { URI(raw) }.getOrNull()
