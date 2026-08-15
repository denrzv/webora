package app.webora.browser.siteskin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.webora.browser.browser.MINIMUM_TOUCH_TARGET

/** The deterministic browser-owned lower edge of M9's expressive integrated header. */
internal object ExpressiveHeaderShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val curve = expressiveCurve(size, with(density) { EXPRESSIVE_CURVE_DEPTH.toPx() })
        return Outline.Generic(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, curve.shoulderY)
                quadraticTo(curve.controlX, curve.controlY, 0f, curve.shoulderY)
                close()
            },
        )
    }
}

internal data class ExpressiveCurve(
    val shoulderY: Float,
    val controlX: Float,
    val controlY: Float,
)

internal fun expressiveCurve(size: Size, requestedDepth: Float): ExpressiveCurve {
    val depth = requestedDepth.coerceAtMost(size.height / 3f)
    return ExpressiveCurve(
        shoulderY = size.height - depth,
        controlX = size.width / 2f,
        controlY = size.height + depth,
    )
}

/**
 * Branded header container for UX-014.
 *
 * The caller supplies content, never geometry. Content is padded above the reserved curve so the
 * decorative edge cannot clip identity or controls at compact width or large text.
 */
@Composable
internal fun ExpressiveSiteSkinHeader(
    presentation: ExpressiveSiteSkinPresentation,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EXPRESSIVE_HEADER_MIN_HEIGHT)
            .clip(ExpressiveHeaderShape)
            .background(presentation.colors.background)
            .padding(start = EXPRESSIVE_GUTTER, top = EXPRESSIVE_GUTTER, end = EXPRESSIVE_GUTTER)
            .padding(bottom = EXPRESSIVE_CURVE_DEPTH)
            .testTag(EXPRESSIVE_HEADER_TAG),
        content = content,
    )
}

/** Floating branded dock container for UX-015; command identity and callbacks remain caller-owned. */
@Composable
internal fun ExpressiveSiteSkinDock(
    presentation: ExpressiveSiteSkinPresentation,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EXPRESSIVE_DOCK_INSET),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EXPRESSIVE_DOCK_MIN_HEIGHT)
                .clip(RoundedCornerShape(EXPRESSIVE_DOCK_RADIUS))
                .background(presentation.colors.secondary)
                .padding(horizontal = EXPRESSIVE_DOCK_PADDING)
                .testTag(EXPRESSIVE_DOCK_TAG),
            content = content,
        )
    }
}

internal const val EXPRESSIVE_HEADER_TAG = "expressive_siteskin_header"
internal const val EXPRESSIVE_DOCK_TAG = "expressive_siteskin_dock"
internal val EXPRESSIVE_DOCK_MIN_HEIGHT = 60.dp
internal val EXPRESSIVE_MINIMUM_TARGET = MINIMUM_TOUCH_TARGET
private val EXPRESSIVE_HEADER_MIN_HEIGHT = 96.dp
private val EXPRESSIVE_CURVE_DEPTH = 20.dp
private val EXPRESSIVE_GUTTER = 20.dp
private val EXPRESSIVE_DOCK_RADIUS = 30.dp
private val EXPRESSIVE_DOCK_INSET = 12.dp
private val EXPRESSIVE_DOCK_PADDING = 8.dp
