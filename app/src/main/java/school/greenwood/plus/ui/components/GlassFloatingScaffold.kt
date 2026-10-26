package school.greenwood.plus.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** A stationary, unpainted header sampling only its scrolling sibling, never itself.
 * Apply the supplied insets INSIDE the scroll container (contentPadding / after verticalScroll).
 * This keeps the first/last item reachable without placing opaque bands behind the glass.
 */
@Composable
fun GlassFloatingScaffold(
    padding: PaddingValues,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var headerHeight by remember { mutableIntStateOf(0) }
    var footerHeight by remember { mutableIntStateOf(0) }
    val left = padding.calculateLeftPadding(direction)
    val right = padding.calculateRightPadding(direction)
    val start = if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr) left else right
    val end = if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr) right else left
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            content(PaddingValues(
                start = start,
                end = end,
                top = padding.calculateTopPadding() + with(density) { headerHeight.toDp() },
                bottom = padding.calculateBottomPadding() + with(density) { footerHeight.toDp() },
            ))
        }
        CompositionLocalProvider(LocalGlassScene provides GlassSceneOwner(LocalView.current, backdrop)) {
            Box(
                Modifier.align(Alignment.TopStart)
                    .padding(top = padding.calculateTopPadding(), start = start, end = end)
                    .onSizeChanged { headerHeight = it.height },
            ) {
                header()
            }
            if (footer != null) {
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .padding(bottom = padding.calculateBottomPadding(), start = start, end = end)
                        .onSizeChanged { footerHeight = it.height },
                ) { footer() }
            }
        }
    }
}
