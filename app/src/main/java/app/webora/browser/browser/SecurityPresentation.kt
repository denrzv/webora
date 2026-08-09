package app.webora.browser.browser

internal enum class TransportSecurity {
    SECURE,
    NOT_SECURE,
}

internal data class SecurityPresentation(
    val registrableDomain: String,
    val transportSecurity: TransportSecurity,
)

internal fun securityPresentation(mode: BrowserMode): SecurityPresentation? {
    val origin = (mode as? BrowserMode.Regular)?.origin ?: return null
    return SecurityPresentation(
        registrableDomain = origin.registrableDomain,
        transportSecurity = if (origin.scheme == "https") {
            TransportSecurity.SECURE
        } else {
            TransportSecurity.NOT_SECURE
        },
    )
}
