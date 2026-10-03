package school.greenwood.plus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import school.greenwood.plus.ui.theme.RegistreTheme

internal val HauteurBannièreRéduite = 40.dp
internal val HauteurBannièreComplète = 140.dp

/** Shared artwork and fade; the screen supplies the scroll-and-pin offset. */
@Composable
internal fun BanniereOnglet(
    @DrawableRes image: Int,
    hauteurBarreÉtat: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(HauteurBannièreComplète + hauteurBarreÉtat)) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, RegistreTheme.colors.paper))),
        )
        Box(
            Modifier.fillMaxWidth().height(hauteurBarreÉtat + 24.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))),
        )
    }
}
