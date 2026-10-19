package school.greenwood.plus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import kotlin.math.abs
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
    return GlassDeformation(
        x = 8f * density * tanh(dx / (width * 0.5f)) * p,
        y = 8f * density * tanh(dy / (height * 0.5f)) * p,
        scaleX = 1f + 0.04f * p + 0.12f * abs(tanh(dx / width)) * p,
        scaleY = 1f + 0.04f * p + 0.12f * abs(tanh(dy / height)) * p,
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
    }.then(highlight.modifier).then(highlight.gestureModifier)
}
