package dev.siteskin.core.validate

import java.net.IDN
import java.net.URI

internal class TrustedOrigin private constructor(
    val value: String,
    private val host: String,
    private val effectivePort: Int,
) {
    @Suppress("ReturnCount")
    fun resolveInternal(reference: String): String? {
        if (reference.startsWith("//") || pathEscapesRoot(reference)) return null
        val resolved = runCatching { URI(value + "/").resolve(reference).normalize() }.getOrNull() ?: return null
        if (resolved.userInfo != null || resolved.scheme != "https") return null
        val resolvedHost = resolved.host?.let(::asciiHost) ?: return null
        if (resolvedHost != host || portOf(resolved) != effectivePort) return null
        return canonicalUrl(resolved, resolvedHost)
    }

    internal companion object {
        fun create(value: String, host: String, effectivePort: Int): TrustedOrigin =
            TrustedOrigin(value, host, effectivePort)
    }
}

internal object OriginPolicy {
    @Suppress("CyclomaticComplexMethod", "ComplexCondition", "ReturnCount")
    fun parse(raw: String): TrustedOrigin? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null) return null
        if (uri.query != null || uri.fragment != null || (uri.path.isNotEmpty() && uri.path != "/")) return null
        val host = uri.host?.let(::asciiHost) ?: return null
        val port = portOf(uri)
        if (port !in MIN_PORT..MAX_PORT) return null
        val authority = if (port == HTTPS_PORT) host else "$host:$port"
        return TrustedOrigin.create("https://$authority", host, port)
    }

    @Suppress("ReturnCount")
    fun resolveExternal(raw: String): String? {
        val uri = runCatching { URI(raw).normalize() }.getOrNull() ?: return null
        if (!uri.isAbsolute || uri.scheme?.lowercase() != "https" || uri.userInfo != null) return null
        val host = uri.host?.let(::asciiHost) ?: return null
        if (portOf(uri) !in MIN_PORT..MAX_PORT) return null
        return canonicalUrl(uri, host)
    }
}

private fun asciiHost(host: String): String = IDN.toASCII(host).lowercase()
private fun portOf(uri: URI): Int = if (uri.port == -1) HTTPS_PORT else uri.port

private fun canonicalUrl(uri: URI, host: String): String {
    val port = portOf(uri)
    val authority = if (port == HTTPS_PORT) host else "$host:$port"
    val path = uri.rawPath.ifEmpty { "/" }
    return URI("https", authority, path, uri.rawQuery, uri.rawFragment).toASCIIString()
}

private fun pathEscapesRoot(reference: String): Boolean {
    val path = runCatching { URI(reference).path }.getOrNull() ?: return true
    var depth = 0
    for (segment in path.split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (depth == 0) return true else depth--
            else -> depth++
        }
    }
    return false
}

private const val HTTPS_PORT = 443
private const val MIN_PORT = 1
private const val MAX_PORT = 65_535
