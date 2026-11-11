package school.greenwood.playground

import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.ExperimentalComposeUiApi
import kotlinx.browser.document
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.await
import org.khronos.webgl.get
import kotlinx.browser.window
import org.jetbrains.skia.Image as SkiaImage
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import com.kashif_e.backdrop.backdrops.rememberCombinedBackdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.opacity
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.Shadow
import kotlin.math.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import com.kashif_e.backdrop.effects.vibrancy

import com.kashif_e.backdrop.Backdrop
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup

/** The shared GWS+ glass effect recipe, backed by real Skia shaders. */
fun Modifier.liquidGlass(backdrop: Backdrop, radius: androidx.compose.ui.unit.Dp = 28.dp): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { RoundedCornerShape(radius) },
    effects = { lens(18.dp.toPx(), 48.dp.toPx(), chromaticAberration = true) },
    highlight = { Highlight.Ambient },
    shadow = { Shadow(radius = 12.dp) }
)

/** Spring motion shared by buttons and navigation items. Optional dragging remains caller-owned. */
@Composable
fun LiquidButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDrag: (Offset) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val currentClick by rememberUpdatedState(onClick)
    val currentDrag by rememberUpdatedState(onDrag)
    val scope = rememberCoroutineScope()
    val pressure = remember { Animatable(0f) }
    val pull = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val speed = remember { Animatable(0f) }
    val bounce = spring<Float>(dampingRatio = .5f, stiffness = 300f)
    val contrast = LocalGlassContrast.current
    var foreground by remember { mutableStateOf(Color.White) }
    Box(modifier.onGloballyPositioned { coordinates ->
        contrast?.let { foreground = it.foreground(coordinates.boundsInRoot(), foreground) }
    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            val origin = down.position
                            var distance = 0f
                            scope.launch { pressure.animateTo(1f, bounce) }
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                val delta = change.position - change.previousPosition
                                if (change.pressed) {
                                    distance += delta.getDistance()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    val velocity = tracker.calculateVelocity().x
                                    scope.launch { speed.animateTo((abs(velocity) / 1800f).coerceIn(0f, 1f), spring(.7f, 700f)) }
                                    currentDrag(delta)
                                    val raw = change.position - origin
                                    val bounded = Offset(90f * tanh(raw.x / 180f), 60f * tanh(raw.y / 140f))
                                    scope.launch { pull.animateTo(bounded, spring(.62f, 650f)) }
                                    change.consume()
                                }
                                change.consume()
                            } while (event.changes.any { it.pressed })
                            if (distance < viewConfiguration.touchSlop) currentClick()
                            scope.launch { speed.animateTo(0f, spring(.45f, 240f)) }
                            scope.launch { pressure.animateTo(0f, bounce) }
                            scope.launch { pull.animateTo(Offset.Zero, spring(.5f, 300f)) }
                        }
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape((28f + 8f * pressure.value).dp) },
                        effects = {
                            // Genuine runtime lens shader: optical depth follows the spring state.
                            val strain = (pull.value.getDistance() / 100f).coerceIn(0f, 1f)
                            lens((18f + 9f * pressure.value + 7f * strain).dp.toPx(),
                                (48f + 28f * pressure.value + 32f * strain).dp.toPx(),
                                chromaticAberration = true)
                        },
                        highlight = { Highlight.Ambient },
                        shadow = { Shadow(radius = (12f + 5f * pressure.value).dp) },
                        layerBlock = {
                            val dx = pull.value.x
                            val dy = pull.value.y
                            val strainX = abs(dx) / 90f
                            val strainY = abs(dy) / 60f
                            // Bounded elastic offset around a fixed anchor, with volume-like compensation.
                            translationX = dx * .22f
                            translationY = dy * .22f
                            scaleX = 1f + .08f * pressure.value + .22f * strainX - .10f * strainY - .10f * speed.value
                            scaleY = 1f + .08f * pressure.value + .25f * strainY - .12f * strainX + .06f * speed.value
                            rotationZ = (dx * dy / 1800f).coerceIn(-4f, 4f)
                        }
                    ),
        contentAlignment = Alignment.Center
    ) { CompositionLocalProvider(LocalContentColor provides foreground) { content() } }
}

/** The moving lens stays larger than a tab even under velocity compression. */
private fun navigationLensScale(pressure: Float, speed: Float): Offset {
    val liquid = pressure.coerceIn(0f, 1.15f)
    val compact = speed.coerceIn(0f, 1f)
    return Offset(1f + liquid * (.38f - .16f * compact),
        1f + liquid * (.50f + .12f * compact))
}

/** Foreground lens samples the entire rendered track, including its labels. */
@Composable
fun LiquidNavigationBar(
    backdrop: Backdrop,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.isNotEmpty() && selectedIndex in items.indices)
    val scope = rememberCoroutineScope()
    val selection by rememberUpdatedState(onSelect)
    val position = remember { Animatable(selectedIndex.toFloat()) }
    val pressure = remember { Animatable(0f) }
    val speed = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val contrast = LocalGlassContrast.current
    val track = rememberLayerBackdrop()
    val clearLensBackdrop = rememberCombinedBackdrop(backdrop, track)
    var lensCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Clicks, keyboard selection and drag release share the same spring transition.
    // Cancellation on a new press/selection keeps quick retargets continuous.
    LaunchedEffect(selectedIndex, dragging) {
        if (!dragging) {
            val target = selectedIndex.toFloat()
            if (abs(position.value - target) > .001f) {
                coroutineScope {
                    launch { pressure.animateTo(1f, spring(.55f, 500f)) }
                    position.animateTo(target, spring(.5f, 300f))
                }
            }
            coroutineScope {
                launch { pressure.animateTo(0f, spring(.5f, 300f)) }
                launch { speed.animateTo(0f, spring(.45f, 240f)) }
            }
        }
    }
    // Expand the entire track and lens as one surface while the handle is held.
    BoxWithConstraints(modifier.graphicsLayer {
        scaleX = 1f + .045f * pressure.value
        scaleY = 1f + .16f * pressure.value
    }) {
        val density = LocalDensity.current
        val tabWidth = (maxWidth - 12.dp) / items.size
        val tabPixels = with(density) { tabWidth.toPx() }
        Box(Modifier.fillMaxWidth().height(68.dp).layerBackdrop(track)) {
            Box(Modifier.fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(36.dp) },
                    effects = {
                        blur(4.dp.toPx())
                        lens(12.dp.toPx(), 24.dp.toPx(), chromaticAberration = true)
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = .12f)) }
                ))
            Row(Modifier.fillMaxSize().padding(6.dp).selectableGroup()) {
                items.forEachIndexed { index, label ->
                    var foreground by remember { mutableStateOf(Color.White) }
                    var labelBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                    LaunchedEffect(contrast, labelBounds) {
                        labelBounds?.let { bounds ->
                            contrast?.let { foreground = it.foreground(bounds, foreground) }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .selectable(selected = index == selectedIndex, role = Role.Tab, onClick = { selection(index) }),
                        contentAlignment = Alignment.Center) {
                        Text(label, color = foreground, fontSize = 12.sp,
                            modifier = Modifier.onGloballyPositioned { labelBounds = it.boundsInRoot() },
                            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(
                                color = if (foreground == Color.Black) Color.White.copy(alpha = .6f) else Color.Black.copy(alpha = .6f),
                                blurRadius = 3f)),
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        // The transparent foreground lens samples the frosted/refracted rear and labels.
        Box(Modifier.padding(6.dp)
            .offset { IntOffset((position.value * tabPixels).roundToInt(), 0) }
            .width(tabWidth).height(56.dp)
            .drawBackdrop(
                backdrop = clearLensBackdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    val motion = maxOf(speed.value, (abs(position.velocity) * tabPixels / 1800f).coerceIn(0f, 1f))
                    val liquid = pressure.value.coerceIn(0f, 1.15f)
                    if (liquid > .001f) {
                        lens(((18f + 4f * motion) * liquid).dp.toPx(),
                            ((48f + 16f * motion) * liquid).dp.toPx(), chromaticAberration = true)
                    }
                },
                highlight = { Highlight.Ambient },
                shadow = { Shadow(radius = 8.dp) },
                layerBlock = {
                    val compact = maxOf(speed.value, abs(position.velocity) * tabPixels / 1800f).coerceIn(0f, 1f)
                    val deformation = navigationLensScale(pressure.value, compact)
                    scaleX = deformation.x
                    scaleY = deformation.y
                }
            )
            .onGloballyPositioned { lensCoordinates = it }
            .pointerInput(tabPixels, items.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val start = position.value
                    val startRoot = lensCoordinates?.localToRoot(down.position) ?: down.position
                    var total = 0f
                    dragging = true
                    scope.launch { pressure.animateTo(1f, spring(.55f, 500f)) }
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                val root = lensCoordinates?.localToRoot(change.position) ?: change.position
                                total = root.x - startRoot.x
                                tracker.addPosition(change.uptimeMillis, root)
                                val velocity = abs(tracker.calculateVelocity().x)
                                scope.launch { speed.animateTo((velocity / 1800f).coerceIn(0f, 1f), spring(.7f, 700f)) }
                                scope.launch { position.snapTo((start + total / tabPixels).coerceIn(0f, items.lastIndex.toFloat())) }
                                change.consume()
                            }
                            change.consume()
                        } while (event.changes.any { it.id == down.id && it.pressed })
                    } finally {
                        dragging = false
                        val target = (start + total / tabPixels).roundToInt().coerceIn(items.indices)
                        selection(target)
                        // The shared transition above settles the lens and releases its pressure.
                    }
                }
            })
    }
}
