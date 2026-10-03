package school.greenwood.plus.ui.screens.registre

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import school.greenwood.plus.R
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.GwsPlusTheme

internal val HauteurBannièreRéduite = 40.dp
internal val HauteurBannièreComplète = 140.dp

/** Full illustration, scrolling until its reduced lower strip pins above the feed. */
@Composable
internal fun BanniereRegistre(
    hauteurBarreÉtat: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(HauteurBannièreComplète + hauteurBarreÉtat)) {
        Image(
            painter = painterResource(R.drawable.registre_banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, RegistreTheme.colors.paper),
                    ),
                ),
        )
        // Keep the white clock and system icons readable over the sky.
        Box(
            Modifier
                .fillMaxWidth()
                .height(hauteurBarreÉtat + 24.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                    ),
                ),
        )
    }
}

@Preview(widthDp = 400)
@Composable
private fun BanniereRegistrePreview() {
    GwsPlusTheme {
        BanniereRegistre(hauteurBarreÉtat = 28.dp)
    }
}
