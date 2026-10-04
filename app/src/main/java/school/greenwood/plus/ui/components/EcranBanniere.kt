package school.greenwood.plus.ui.components

import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import school.greenwood.plus.ui.theme.RegistreTheme

/** One scroll viewport for the title, controls, and data, under a collapsing banner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EcranBanniere(
    @DrawableRes image: Int,
    activée: Boolean,
    padding: PaddingValues,
    liste: LazyListState,
    rafraîchissement: Boolean = false,
    surActualiser: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val activité = LocalContext.current as? ComponentActivity
    val pageClaire = RegistreTheme.colors.page.luminance() > 0.5f
    val seuil = with(LocalDensity.current) {
        (HauteurBannièreComplète - HauteurBannièreRéduite).roundToPx()
    }
    DisposableEffect(activité, activée, pageClaire) {
        val contrôleur = activité?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val précédente = contrôleur?.isAppearanceLightStatusBars
        contrôleur?.isAppearanceLightStatusBars = !activée && pageClaire
        onDispose {
            if (précédente != null) contrôleur.isAppearanceLightStatusBars = précédente
        }
    }
    val flux: @Composable () -> Unit = {
        LazyColumn(
            state = liste,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = if (activée) HauteurBannièreComplète + 12.dp else 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
    Box(Modifier.fillMaxSize().clipToBounds().background(RegistreTheme.colors.paper)) {
        if (activée) {
            BanniereOnglet(
                image = image,
                hauteurBarreÉtat = padding.calculateTopPadding(),
                modifier = Modifier.zIndex(1f).offset {
                    val défilement = if (liste.firstVisibleItemIndex == 0) {
                        liste.firstVisibleItemScrollOffset.coerceAtMost(seuil)
                    } else seuil
                    IntOffset(0, -défilement)
                },
            )
        }
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (surActualiser != null) {
                PullToRefreshBox(
                    isRefreshing = rafraîchissement,
                    onRefresh = surActualiser,
                    modifier = Modifier.fillMaxSize(),
                ) { flux() }
            } else flux()
        }
    }
}
