package school.greenwood.plus.ui.components

import android.os.Build
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import school.greenwood.plus.ui.Onglet
import school.greenwood.plus.ui.theme.GwsAccent
import school.greenwood.plus.ui.theme.RegistreTheme

/** Six real destinations, navigation-owned selection; no Back handler here. */
@Composable
fun BarreOnglets(
    onglets: List<Onglet>,
    routeSélectionnée: String?,
    accents: Map<String, GwsAccent>,
    onOnglet: (String) -> Unit,
    modifier: Modifier = Modifier,
): Unit = WithGlassScene {
    BoxWithConstraints(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val serré = maxWidth / onglets.size < 60.dp
        val index = onglets.indexOfFirst { it.route == routeSélectionnée }.coerceAtLeast(0)
        LiquidBottomTabs(
            selectedTabIndex = { index },
            onTabSelected = { onOnglet(onglets[it].route) },
            backdrop = LocalScreenBackdrop.current ?: LocalGlassScene.current!!.backdrop,
            tabsCount = onglets.size,
            modifier = Modifier.fillMaxWidth(),
            containerColor = RegistreTheme.colors.page.copy(alpha = glassPolicy(Build.VERSION.SDK_INT).chromeOpacity),
            accentColor = RegistreTheme.accent.teinte,
        ) {
            onglets.forEachIndexed { i, onglet ->
                val accent = accents.getValue(onglet.route)
                val tint = if (i == index) accent.teinte else RegistreTheme.colors.chalk
                LiquidBottomTab(
                    onClick = { onOnglet(onglet.route) },
                    modifier = Modifier.semantics { selected = i == index },
                ) {
                    Icon(onglet.icone, null, tint = tint, modifier = Modifier.size(if (serré) 22.dp else 24.dp))
                    Text(
                        onglet.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = if (serré) 10.sp else 11.sp),
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
