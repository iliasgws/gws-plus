package school.greenwood.plus.ui.components

import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.unit.dp
import school.greenwood.plus.ui.theme.RegistreTheme

internal data class GlassSceneOwner(val view: View, val backdrop: Backdrop)
internal val LocalGlassScene = staticCompositionLocalOf<GlassSceneOwner?> { null }
internal val LocalScreenBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalGlassBlur = staticCompositionLocalOf { GlassBlurDp }

/** Strong frosting is scoped to messaging; foreground text/icons are never blurred. */
@Composable
internal fun MessagingGlass(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassBlur provides 32f, content = content)
}

/** Only the sibling gradient is recorded. Controls can never sample themselves. */
@Composable
fun GlassScene(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val colors = RegistreTheme.colors
    val accent = RegistreTheme.accent
    Box(modifier) {
        Box(
            Modifier.matchParentSize().clip(shape).layerBackdrop(backdrop).background(
                Brush.linearGradient(listOf(colors.paper, accent.conteneur, colors.page, colors.paper)),
            ),
        )
        CompositionLocalProvider(LocalGlassScene provides GlassSceneOwner(LocalView.current, backdrop)) {
            content()
        }
    }
}

/** Dialogs, sheets and popups have a different owner; never reuse window coordinates. */
@Composable
internal fun WithGlassScene(content: @Composable () -> Unit) {
    if (LocalGlassScene.current?.view !== LocalView.current) {
        GlassScene { content() }
    } else {
        content()
    }
}

internal fun Modifier.glassChrome(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color,
    blurDp: Float = GlassBlurDp,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        if (glassPolicy(Build.VERSION.SDK_INT).blur) {
            vibrancy()
            blur(blurDp.dp.toPx())
        }
        if (glassPolicy(Build.VERSION.SDK_INT).lens) {
            lens(18.dp.toPx(), GlassRefractionDp.dp.toPx(), depthEffect = true, chromaticAberration = true)
        }
    },
    onDrawSurface = { drawRect(tint) },
)
