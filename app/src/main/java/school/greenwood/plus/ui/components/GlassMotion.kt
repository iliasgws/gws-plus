package school.greenwood.plus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

internal data class GlassDeformation(
    val x: Float = 0f,
    val y: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

/** Bounded motion shared by liquid buttons, profile and icon actions. */
internal fun glassDeformation(
    x: Float, y: Float, width: Float, height: Float, pressure: Float, density: Float,
): GlassDeformation {
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f ||
        !density.isFinite() || density <= 0f) return GlassDeformation()
    val p = if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 0f
    val dx = if (x.isFinite()) x else 0f
    val dy = if (y.isFinite()) y else 0f
    // Original Kyant catalog LiquidButton deformation, shared by all action types.
    val bound = minOf(width, height)
    val scale = 1f + 4f * density / height * p
    val dragScale = 4f * density / height
    val angle = atan2(dy, dx)
    return GlassDeformation(
        x = bound * tanh(0.05f * dx / bound),
        y = bound * tanh(0.05f * dy / bound),
        scaleX = scale + dragScale * abs(cos(angle) * dx / maxOf(width, height)) * minOf(width / height, 1f),
        scaleY = scale + dragScale * abs(sin(angle) * dy / maxOf(width, height)) * minOf(height / width, 1f),
    )
}

@Composable
internal fun rememberGlassStretch(enabled: Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    if (!enabled) return Modifier
    return Modifier.graphicsLayer {
        val motion = glassDeformation(
            highlight.offset.x, highlight.offset.y, size.width, size.height,
            highlight.pressProgress, density,
        )
        translationX = motion.x
        translationY = motion.y
        scaleX = motion.scaleX
        scaleY = motion.scaleY
    }.then(highlight.gestureModifier)
}
