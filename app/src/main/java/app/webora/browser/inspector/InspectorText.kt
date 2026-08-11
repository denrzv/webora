package app.webora.browser.inspector

import app.webora.browser.siteskin.untrustedText
import dev.siteskin.core.SiteSkinLimits

/**
 * The inspector's bound on website-controlled text.
 *
 * The walk itself lives in [untrustedText], because `UX-006`'s consent sheet needs the same
 * treatment at a different bound and a security dialog should not import a debug tool's helper.
 * This stays as the inspector's spelling of it: the panel renders a grid of browser-authored labels
 * beside values, several of which are arbitrary website text, and every one of them is bounded by
 * the same limit — so the constant belongs here rather than at fifteen call sites.
 */
internal fun inspectorValue(raw: String?): String =
    untrustedText(raw, SiteSkinLimits.MAX_SUBTITLE_LENGTH)
