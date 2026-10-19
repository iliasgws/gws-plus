package school.greenwood.plus.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

internal class GlassBackdropProbe {
    val lightForeground = mutableStateMapOf<String, Boolean>()
    val regions = mutableMapOf<String, Rect>()
    var coordinates: LayoutCoordinates? = null
    var origin: Offset? = null
    var scale: Float = 1f
    var modifier: Modifier = Modifier
}

internal fun GlassBackdropProbe.track(key: String): Modifier =
    Modifier.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) regions[key] = coordinates.boundsInWindow()
        else regions.remove(key)
    }

/** One tiny GPU readback per bar, at most eight per second, only while STARTED.
 * Records the sibling backdrop without drawing it over the chrome or sampling itself.
 * Pixels never leave memory; no screenshot/bitmap is saved or logged.
 */
@Composable
internal fun rememberGlassBackdropProbe(backdrop: Backdrop): GlassBackdropProbe {
    val probe = remember(backdrop) { GlassBackdropProbe() }
    val layer = rememberGraphicsLayer()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    probe.modifier = Modifier.onGloballyPositioned { probe.coordinates = it }.drawWithContent {
        val coordinates = probe.coordinates
        if (coordinates?.isAttached == true && size.width > 0f && size.height > 0f) {
            val factor = min(1f, 320f / size.width)
            val density = this
            layer.record(size = IntSize(
                (size.width * factor).roundToInt().coerceAtLeast(1),
                (size.height * factor).roundToInt().coerceAtLeast(1),
            )) {
                scale(factor, factor, pivot = Offset.Zero) {
                    with(backdrop) { drawBackdrop(density, coordinates) }
                }
            }
            probe.origin = coordinates.positionInWindow()
            probe.scale = factor
        }
        drawContent()
    }
    LaunchedEffect(probe, layer, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(125)
                val origin = probe.origin ?: continue
                if (probe.regions.isEmpty()) continue
                val regions = probe.regions.toMap()
                val previous = probe.lightForeground.toMap()
                val factor = probe.scale
                try {
                    val image = layer.toImageBitmap().asAndroidBitmap()
                    val results = withContext(Dispatchers.Default) {
                        try {
                            val software = image.copy(Bitmap.Config.ARGB_8888, false)
                                ?: return@withContext emptyMap<String, Boolean>()
                            try {
                                val width = software.width
                                val height = software.height
                                val pixels = IntArray(width * height)
                                software.getPixels(pixels, 0, width, 0, 0, width, height)
                                regions.mapValues { (key, bounds) ->
                                    val samples = IntArray(81) { index ->
                                        val x = (((bounds.left - origin.x) + bounds.width *
                                            ((index % 9 + 0.5f) / 9f)) * factor).toInt()
                                        val y = (((bounds.top - origin.y) + bounds.height *
                                            ((index / 9 + 0.5f) / 9f)) * factor).toInt()
                                        if (x in 0 until width && y in 0 until height) pixels[y * width + x] else 0
                                    }
                                    useLightGlassForeground(samples, previous[key] ?: false)
                                }
                            } finally { software.recycle() }
                        } finally { image.recycle() }
                    }
                    results.forEach { (key, light) ->
                        if (probe.lightForeground[key] != light) probe.lightForeground[key] = light
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: RuntimeException) {
                    // Renderer teardown/unsupported readback: retain the last known accessible tint.
                }
            }
        }
    }
    return probe
}
