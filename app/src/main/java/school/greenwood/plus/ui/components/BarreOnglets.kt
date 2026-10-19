package school.greenwood.plus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * La barre basse « École vivante » : verre doux arrondi, liseré lumineux,
 * capsule d'accent translucide et réponse élastique au toucher.
 * Ce traitement est purement Compose : il ne prétend pas réfracter le fond.
 * Les six destinations et le contrat de navigation de AppNav.kt §3 restent
 * inchangés, y compris le geste de retour prédictif.
 */

private val HauteurBarre = 64.dp

/** Sous cette largeur d'entrée, la barre passe en gabarit serré. */
private val SeuilSerré = 60.dp

@Composable
fun BarreOnglets(
    onglets: List<Onglet>,
    routeSélectionnée: String?,
    accents: Map<String, GwsAccent>,
    onOnglet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sombre = isSystemInDarkTheme()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(RegistreTheme.colors.paper)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = PiluleShape,
        color = RegistreTheme.colors.page.copy(alpha = if (sombre) 0.92f else 0.86f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (sombre) 0.48f else 0.98f),
                    RegistreTheme.colors.ink.copy(alpha = if (sombre) 0.25f else 0.12f),
                ),
            ),
        ),
        shadowElevation = 8.dp,
    ) {
        Column {
            // Le reflet supérieur suggère une tranche de verre.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (sombre) 0.32f else 0.78f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                    val interaction = remember { MutableInteractionSource() }
                    val pressé by interaction.collectIsPressedAsState()
                    val pression by animateFloatAsState(
                        targetValue = if (pressé) 0.94f else 1f,
                        animationSpec = RessortVif,
                        label = "pressionOnglet",
                    )

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
                                interactionSource = interaction,
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
                            modifier = Modifier.graphicsLayer {
                                scaleX = pression
                                scaleY = pression
                            },
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
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        accent.conteneur.copy(alpha = 0.96f),
                                                        accent.conteneur.copy(alpha = 0.65f),
                                                    ),
                                                ),
                                                PiluleShape,
                                            )
                                            .border(
                                                1.dp,
                                                Color.White.copy(alpha = if (sombre) 0.26f else 0.72f),
                                                PiluleShape,
                                            ),
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
