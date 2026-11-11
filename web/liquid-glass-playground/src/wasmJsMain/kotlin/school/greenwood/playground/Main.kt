package school.greenwood.playground

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.await
import org.khronos.webgl.get
import kotlinx.browser.window
import org.jetbrains.skia.Image as SkiaImage
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
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
import com.kashif_e.backdrop.effects.vibrancy

@Composable
fun Playground() {
    val backdrop = rememberLayerBackdrop()
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var clicks by remember { mutableIntStateOf(0) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val pixels = remember(wallpaper) { wallpaper?.toPixelMap() }
    val contrast = remember(pixels, viewport) { pixels?.let { GlassContrastSource(it, viewport.width, viewport.height) } }
    CompositionLocalProvider(LocalGlassContrast provides contrast) {
    MaterialTheme {
        Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }.background(Color(0xFF17120F))) {
            wallpaper?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = "Autumn leaves and the Golden Pavilion reflected in a pond",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)
                )
            }
            Text(
                "KMP LIQUID GLASS",
                modifier = Modifier.align(Alignment.TopStart).padding(28.dp),
                color = Color.White.copy(alpha = .82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
            LiquidButton(
                backdrop = backdrop,
                onClick = { clicks++ },
                modifier = Modifier.align(Alignment.Center)
                    .size(184.dp, 72.dp)
            ) {
                Text(if (clicks == 0) "Étirer · Cliquer" else "Cliqué $clicks fois",
                    modifier = Modifier.graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        blendMode = androidx.compose.ui.graphics.BlendMode.Difference
                    },
                    color = Color.White, fontWeight = FontWeight.SemiBold)

            }
            LiquidNavigationBar(
                backdrop = backdrop,
                items = listOf("Registre", "Devoirs", "Documents", "Messages"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 28.dp)
                    .widthIn(max = 440.dp).fillMaxWidth()
            )
            Text(
                listOf("Registre", "Devoirs", "Documents", "Messages")[selectedTab],
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp),
                color = Color.White.copy(alpha = .78f),
                fontSize = 14.sp
            )
        }
    }
    }
    LaunchedEffect(Unit) {
        val response = window.fetch("/wallpaper.png").await<org.w3c.fetch.Response>()
        val bytes = response.arrayBuffer().await<org.khronos.webgl.ArrayBuffer>()
        val data = org.khronos.webgl.Int8Array(bytes)
        val byteArray = ByteArray(data.length) { data[it] }
        wallpaper = SkiaImage.makeFromEncoded(byteArray).toComposeImageBitmap()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    androidx.compose.ui.window.ComposeViewport(document.body!!) {
        Playground()
    }
}
