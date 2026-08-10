package app.webora.browser.browser

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Browser-owned accessibility affordances.
 *
 * The minimum touch target lives here rather than at each call site because a per-call-site
 * `sizeIn` is a convention, and a convention is one forgotten screen away from being false.
 * `BrowserSurfaceConventionsTest` keeps the raw Material components out of browser-owned UI so the
 * minimum cannot be bypassed by writing ordinary-looking code.
 */
internal val MINIMUM_TOUCH_TARGET: Dp = 48.dp

/**
 * Raises a control to the minimum target on both axes.
 *
 * Material 3 gives `Button` a 40 dp minimum height via `defaultMinSize` and — unlike `Switch`,
 * `Checkbox` and `IconButton` — does not apply `minimumInteractiveComponentSize()`. Every plain
 * button in this app was therefore an 8 dp-short target until this modifier existed.
 */
internal fun Modifier.browserTouchTarget(): Modifier =
    sizeIn(minWidth = MINIMUM_TOUCH_TARGET, minHeight = MINIMUM_TOUCH_TARGET)

@Composable
internal fun WeboraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.browserTouchTarget(),
        content = content,
    )
}

@Composable
internal fun WeboraTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.browserTouchTarget(),
        content = content,
    )
}

/** A labelled button, the shape most browser chrome needs. */
@Composable
internal fun WeboraButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    WeboraButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
}

/** What the browser has to say about the current page, in browser-owned copy. */
internal enum class BrowserAnnouncement {
    LOADING,
    LOADED,
    FAILED,
}

/**
 * The announcement describing [state], or `null` when the browser has nothing to say.
 *
 * Derived from the state rather than from a transition, and deliberately so: a Compose live region
 * announces when its content *changes*, so the derived value already is the transition. Tracking a
 * previous state alongside it would add a second place for the announcement to go stale — and a
 * stale announcement is worse than none, because assistive technology presents it as current.
 *
 * Home has nothing to announce, and neither does a browser that has not committed a page: an empty
 * announcement would be a claim about a page that does not exist.
 */
internal fun browserAnnouncement(state: BrowserState): BrowserAnnouncement? = when {
    state.mode == BrowserMode.Home -> null
    state.loadFailure != null -> BrowserAnnouncement.FAILED
    state.isLoading -> BrowserAnnouncement.LOADING
    state.displayedUrl.isNotEmpty() -> BrowserAnnouncement.LOADED
    else -> null
}

/**
 * Failure interrupts; progress waits its turn.
 *
 * Announcing every load politely would bury a failure the user needs now, and announcing every load
 * assertively would interrupt them on every navigation. The distinction is the point of having two
 * modes at all.
 */
internal fun BrowserAnnouncement.liveRegionMode(): LiveRegionMode = when (this) {
    BrowserAnnouncement.FAILED -> LiveRegionMode.Assertive
    BrowserAnnouncement.LOADING, BrowserAnnouncement.LOADED -> LiveRegionMode.Polite
}
