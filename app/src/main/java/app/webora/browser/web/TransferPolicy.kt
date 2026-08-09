package app.webora.browser.web

import java.net.URI

private const val MAX_ACCEPT_TYPES = 16

internal fun downloadUrl(value: String): String? {
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    return value.takeIf {
        uri.isAbsolute && !uri.host.isNullOrBlank() && uri.scheme.lowercase() in setOf("http", "https")
    }
}

internal fun uploadMimeType(acceptTypes: Array<out String>): String? {
    if (acceptTypes.isEmpty() || acceptTypes.size > MAX_ACCEPT_TYPES) return null
    val types = acceptTypes.mapNotNull(::allowedMimeType).distinct()
    if (types.isEmpty()) return null
    if (types.all { it.startsWith("image/") }) return "image/*"
    return types.singleOrNull()
}

private fun allowedMimeType(value: String): String? = when (val normalized = value.trim().lowercase()) {
    "image/png", "image/jpeg", "image/gif", "image/webp", "image/*", "application/pdf" -> normalized
    else -> null
}

internal fun selectedUploadUri(value: String?): String? {
    val uri = value?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    return value.takeIf { uri.scheme.equals("content", ignoreCase = true) && !uri.authority.isNullOrBlank() }
}
