package school.greenwood.playground

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import kotlin.math.*

/** Wallpaper pixels and crop geometry; shared once rather than reading the GPU per frame. */
data class GlassContrastSource(val pixels: PixelMap, val width: Int, val height: Int)
val LocalGlassContrast = staticCompositionLocalOf<GlassContrastSource?> { null }

/** Choose the higher WCAG contrast of black and white over a grid under the control.
 * A dead band preserves the previous choice around the crossover to avoid flicker.
 */
fun GlassContrastSource.foreground(bounds: Rect, previous: Color): Color {
    if (width <= 0 || height <= 0) return previous
    val scale = max(width.toFloat() / pixels.width, height.toFloat() / pixels.height)
    val cropX = (pixels.width * scale - width) / 2f
    val cropY = (pixels.height * scale - height) / 2f
    var luminance = 0f
    fun linear(v: Float) = if (v <= .04045f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f)
    for (y in 0..4) for (x in 0..6) {
        val px = ((bounds.left + bounds.width * (x + .5f) / 7 + cropX) / scale).toInt().coerceIn(0, pixels.width - 1)
        val py = ((bounds.top + bounds.height * (y + .5f) / 5 + cropY) / scale).toInt().coerceIn(0, pixels.height - 1)
        val c = pixels[px, py]
        luminance += .2126f * linear(c.red) + .7152f * linear(c.green) + .0722f * linear(c.blue)
    }
    luminance /= 35f
    return when {
        luminance > .20f -> Color.Black
        luminance < .16f -> Color.White
        else -> previous
    }
}
