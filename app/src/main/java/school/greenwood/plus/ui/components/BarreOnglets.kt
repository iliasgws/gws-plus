package school.greenwood.plus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.unit.dp
import school.greenwood.plus.ui.Onglet
import school.greenwood.plus.ui.theme.GwsAccent
import school.greenwood.plus.ui.theme.FonduCouleur
import school.greenwood.plus.ui.theme.PiluleShape
import school.greenwood.plus.ui.theme.RessortDoux
import school.greenwood.plus.ui.theme.RessortVif
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * La barre basse « École vivante » : chaque onglet porte sa capsule d'accent,
 * les libellés restent sur une ligne (plus de « Actualit és »), la sélection
 * rebondit en ressort. Le contrat de navigation de AppNav.kt §3 est inchangé —
 * la barre est purement visuelle, aucun geste de retour intercepté ici.
 */

private val HauteurBarre = 64.dp

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
    ) {
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

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 30.dp)
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
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = échelle
                                        scaleY = échelle
                                    },
                            )
                        }
                        Text(
                            text = onglet.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = couleurLibellé,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
