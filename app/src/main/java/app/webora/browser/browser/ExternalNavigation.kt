package app.webora.browser.browser

import java.net.URI

internal class ExternalNavigation internal constructor(
    val kind: Kind,
    val uri: String,
) {
    internal enum class Kind { EMAIL, TELEPHONE, MAP }
}

internal fun externalNavigation(value: String): ExternalNavigation? {
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (!uri.isAbsolute || uri.schemeSpecificPart.isNullOrBlank()) return null
    val kind = when (uri.scheme?.lowercase()) {
        "mailto" -> ExternalNavigation.Kind.EMAIL
        "tel" -> ExternalNavigation.Kind.TELEPHONE
        "geo" -> ExternalNavigation.Kind.MAP
        else -> return null
    }
    return ExternalNavigation(kind, uri.toASCIIString())
}
