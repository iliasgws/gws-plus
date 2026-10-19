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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    val backdrop = LocalScreenBackdrop.current ?: LocalGlassScene.current!!.backdrop
    // Icons flip to a light tint as soon as dark content touches their own region —
    // partial coverage counts (GlassBackdropProbe's per-pixel probe, not a bar average).
    val probe = rememberGlassBackdropProbe(backdrop)
    BoxWithConstraints(
        modifier.then(probe.modifier).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val serré = maxWidth / onglets.size < 60.dp
        val index = onglets.indexOfFirst { it.route == routeSélectionnée }.coerceAtLeast(0)
        LiquidBottomTabs(
            selectedTabIndex = { index },
            onTabSelected = { i -> onOnglet(onglets[i].route) },
            backdrop = backdrop,
            tabsCount = onglets.size,
            modifier = Modifier.fillMaxWidth(),
            containerColor = RegistreTheme.colors.page.copy(alpha = glassPolicy(Build.VERSION.SDK_INT).chromeOpacity),
            accentColor = RegistreTheme.accent.teinte,
        ) {
            onglets.forEachIndexed { i, onglet ->
                val accent = accents.getValue(onglet.route)
                val sombre = probe.lightForeground["onglet-$i"] == true
                val tint = when {
                    i == index && sombre -> lerp(accent.teinte, Color.White, 0.45f)
                    i == index -> accent.teinte
                    sombre -> RegistreTheme.colors.paper
                    else -> RegistreTheme.colors.chalk
                }
                LiquidBottomTab(
                    onClick = { onOnglet(onglet.route) },
                    modifier = Modifier.semantics { selected = i == index },
                ) {
                    Icon(
                        onglet.icone,
                        null,
                        tint = tint,
                        modifier = Modifier.size(if (serré) 22.dp else 24.dp)
                            .then(probe.track("onglet-$i")),
                    )
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
