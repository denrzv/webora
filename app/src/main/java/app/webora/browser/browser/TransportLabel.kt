package app.webora.browser.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.webora.browser.R

/**
 * The browser-authored word for each transport state, for every chrome that shows one.
 *
 * Exhaustive with no `else`, so a fifth state is a compile error rather than a silent fall-through
 * to whichever label the `else` happened to name — on a surface where the wrong label is a false
 * security claim.
 *
 * **One owner, because two copies of a mapping is two answers waiting to diverge.** Regular chrome
 * and the integrated trust chip each had their own verbatim copy of this `when`. Sharing the four
 * *strings* was never enough: a fifth state produced two compile errors that could legitimately be
 * resolved differently, and a re-pointed branch in one file drifted from the other with nothing
 * failing. `UX-020` recorded that exact lesson one ticket earlier, where a colour assertion named
 * Webora roles while the composable named Material ones and `materialColorScheme` — the mapping
 * between them — was consulted by neither.
 *
 * It lives beside [SecurityPresentation] rather than in either chrome package because it belongs to
 * neither: it is a property of the transport state, and both modes are readers.
 */
@Composable
internal fun transportLabel(transport: TransportSecurity): String = stringResource(
    when (transport) {
        TransportSecurity.SECURE -> R.string.security_secure
        TransportSecurity.NOT_SECURE -> R.string.security_not_secure
        TransportSecurity.UNKNOWN -> R.string.security_unknown
        TransportSecurity.TLS_ERROR -> R.string.security_tls_error
    },
)
