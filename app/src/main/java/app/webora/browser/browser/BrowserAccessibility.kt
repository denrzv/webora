package app.webora.browser.browser

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
