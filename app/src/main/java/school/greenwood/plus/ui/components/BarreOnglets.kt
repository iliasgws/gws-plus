package school.greenwood.plus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import school.greenwood.plus.ui.Onglet
import school.greenwood.plus.ui.theme.FonduCouleur
import school.greenwood.plus.ui.theme.GwsAccent
import school.greenwood.plus.ui.theme.PiluleShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.RessortDoux
import school.greenwood.plus.ui.theme.RessortVif

/*
 * La barre basse « École vivante » : surface blanche posée sur un filet de
 * 1 dp, capsule d'accent sur l'onglet actif, libellés alignés sur une ligne.
 * Six entrées réelles ; quand elles se tassent (320 dp), la typographie se
 * resserre au lieu de perdre une destination. Le contrat de navigation de
 * AppNav.kt §3 est inchangé — la barre est purement visuelle, aucun geste de
 * retour n'est intercepté ici.
 */

private val HauteurBarre = 64.dp

/** Sous cette largeur d'entrée, la barre passe en gabarit serré. */
private val SeuilSerré = 60.dp

/** The same destinations and callbacks as the compact bar; no separate stack. */
@Composable
internal fun RailOnglets(
    onglets: List<Onglet>,
    routeSélectionnée: String?,
    accents: Map<String, GwsAccent>,
    onOnglet: (String) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.width(104.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
        containerColor = RegistreTheme.colors.page,
    ) {
        onglets.forEach { onglet ->
            val accent = accents.getValue(onglet.route)
            NavigationRailItem(
                selected = routeSélectionnée == onglet.route,
                onClick = { onOnglet(onglet.route) },
                icon = { Icon(onglet.icone, contentDescription = null) },
                label = { Text(onglet.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = accent.surConteneur,
                    selectedTextColor = accent.teinte,
                    indicatorColor = accent.conteneur,
                    unselectedIconColor = RegistreTheme.colors.chalk,
                    unselectedTextColor = RegistreTheme.colors.chalk,
                ),
            )
        }
    }
}

@Composable
fun BarreOnglets(
    onglets: List<Onglet>,
    routeSélectionnée: String?,
    accents: Map<String, GwsAccent>,
    onOnglet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = RegistreTheme.colors.page,
        shadowElevation = 2.dp,
    ) {
        Column {
            // Le filet de séparation : 1 dp, aucun halo.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(HauteurBarre),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onglets.forEach { onglet ->
                    val accent = accents[onglet.route] ?: GwsAccent(
                        RegistreTheme.colors.ink,
                        RegistreTheme.colors.sage,
                        RegistreTheme.colors.ink,
                    )
                    val sélectionné = routeSélectionnée == onglet.route

                    // Sélection animée : la capsule apparaît en ressort, l'icône
                    // rebondit, les couleurs fondent.
                    val alphaCapsule by animateFloatAsState(
                        targetValue = if (sélectionné) 1f else 0f,
                        animationSpec = RessortDoux,
                        label = "capsule",
                    )
                    val échelle by animateFloatAsState(
                        targetValue = if (sélectionné) 1.08f else 1f,
                        animationSpec = RessortVif,
                        label = "échelle",
                    )
                    val couleurIcône by animateColorAsState(
                        targetValue = if (sélectionné) accent.surConteneur else RegistreTheme.colors.chalk,
                        animationSpec = FonduCouleur,
                        label = "icône",
                    )
                    val couleurLibellé by animateColorAsState(
                        targetValue = if (sélectionné) accent.teinte else RegistreTheme.colors.chalk,
                        animationSpec = FonduCouleur,
                        label = "libellé",
                    )

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .height(HauteurBarre)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) { onOnglet(onglet.route) }
                            .semantics {
                                stateDescription =
                                    if (sélectionné) "Sélectionné" else "Non sélectionné"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Six entrées sur 320 dp : la typo se resserre au lieu
                        // d'effacer une destination (issue #101 §6).
                        val serré = maxWidth < SeuilSerré
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = if (serré) 40.dp else 44.dp,
                                        height = if (serré) 28.dp else 30.dp,
                                    )
                                    .clip(PiluleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                // La capsule : invisible quand l'onglet dort.
                                if (alphaCapsule > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .graphicsLayer { alpha = alphaCapsule }
                                            .background(accent.conteneur, PiluleShape),
                                    )
                                }
                                Icon(
                                    imageVector = onglet.icone,
                                    contentDescription = onglet.label,
                                    tint = couleurIcône,
                                    modifier = Modifier
                                        .size(if (serré) 22.dp else 24.dp)
                                        .graphicsLayer {
                                            scaleX = échelle
                                            scaleY = échelle
                                        },
                                )
                            }
                            Text(
                                text = onglet.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (serré) 10.sp else 11.sp,
                                ),
                                color = couleurLibellé,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
