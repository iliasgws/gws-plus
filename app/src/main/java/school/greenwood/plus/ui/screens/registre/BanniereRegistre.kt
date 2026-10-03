package school.greenwood.plus.ui.screens.registre

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import school.greenwood.plus.R
import school.greenwood.plus.ui.components.BanniereOnglet
import school.greenwood.plus.ui.theme.GwsPlusTheme

@Composable
internal fun BanniereRegistre(hauteurBarreÉtat: Dp, modifier: Modifier = Modifier) {
    BanniereOnglet(R.drawable.registre_banner, hauteurBarreÉtat, modifier)
}

@Preview(widthDp = 400)
@Composable
private fun BanniereRegistrePreview() {
    GwsPlusTheme { BanniereRegistre(hauteurBarreÉtat = 28.dp) }
}
