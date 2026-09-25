package school.greenwood.plus.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import school.greenwood.plus.data.ai.ActionIA
import school.greenwood.plus.data.ai.ComposeurIA
import school.greenwood.plus.data.ai.RéglagesIA
import school.greenwood.plus.data.ai.TonIA
import school.greenwood.plus.ui.components.BadgeGénéréIA
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * L'assistant d'écriture du composeur (issue #56), repensé après l'essai
 * réel de la bêta 3 : une feuille basse compacte (~250 dp fermée), à la
 * hiérarchie claire —
 *  - poignée de glissement : vers le haut déplie les transformations,
 *    vers le bas les replie ;
 *  - aperçu du message en cours (« Message sélectionné ») ;
 *  - champ d'instruction encadré (« Que voulez-vous modifier ? ») ;
 *  - deux actions principales : Corriger / Réécrire ;
 *  - les tons : Amical / Professionnel / Neutre ;
 *  - déplié : Raccourcir / Développer / Structurer / Simplifier ;
 *  - le bouton principal « ✨ Générer » lance l'action choisie.
 * L'accent du panneau est celui de l'onglet (le rose du ✨), partout pareil.
 */

/** Une transformation proposée, avec son icône. */
private data class TransformationIA(val action: ActionIA, val icône: ImageVector)

private val Transformations = listOf(
    TransformationIA(ActionIA.RACCOURCIR, Icons.Rounded.ContentCut),
    TransformationIA(ActionIA.DÉVELOPPER, Icons.Rounded.UnfoldMore),
    TransformationIA(ActionIA.STRUCTURER, Icons.Rounded.FormatAlignLeft),
    TransformationIA(ActionIA.SIMPLIFIER, Icons.Rounded.LinearScale),
)

@Composable
fun PanneauIA(
    réglages: RéglagesIA,
    texte: String,
    onRemplacer: (String) -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val portée = rememberCoroutineScope()
    var consigne by remember { mutableStateOf("") }
    var ton by remember { mutableStateOf(réglages.ton) }
    var actionChoisie by remember { mutableStateOf(ActionIA.CORRIGER) }
    var déplié by remember { mutableStateOf(false) }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var résultat by remember { mutableStateOf<String?>(null) }

    fun lancer(action: ActionIA) {
        if (enCours) return
        portée.launch {
            enCours = true
            erreur = null
            runCatching {
                ComposeurIA.transformer(
                    réglages = réglages.copy(ton = ton),
                    action = action,
                    consigne = consigne.takeIf { it.isNotBlank() },
                    texte = texte,
                )
            }.onSuccess { résultat = it }
                .onFailure { erreur = it.message ?: "Une erreur est survenue." }
            enCours = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = RegistreTheme.colors.page,
        border = androidx.compose.foundation.BorderStroke(1.dp, RegistreTheme.colors.sage),
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // La poignée : glisse vers le haut pour déplier, vers le bas pour
            // replier (un tap marche aussi).
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(AnnotationShape)
                    .background(RegistreTheme.colors.chalk)
                    .pointerInput(déplié) {
                        detectVerticalDragGestures { _, déplacement ->
                            if (déplacement < -6) déplié = true
                            if (déplacement > 6) déplié = false
                        }
                    }
                    .clickable { déplié = !déplié },
            )

            résultat?.let { texteRésultat ->
                RésultatIA(
                    texteRésultat = texteRésultat,
                    enCours = enCours,
                    surRéessayer = { résultat = null },
                    surRemplacer = {
                        onRemplacer(texteRésultat)
                        onFermer()
                    },
                    surAnnuler = {
                        résultat = null
                        onFermer()
                    },
                )
            } ?: run {
                // L'aperçu : de quel message on parle.
                Text(
                    text = "Message sélectionné",
                    style = MaterialTheme.typography.labelSmall,
                    color = RegistreTheme.colors.chalk,
                )
                Text(
                    text = "« " + texte.trim().take(90) + (if (texte.length > 90) "…" else "") + " »",
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )

                // Le champ d'instruction, encadré, point de départ clair.
                Surface(
                    shape = ControlShape,
                    color = RegistreTheme.colors.paper,
                    border = androidx.compose.foundation.BorderStroke(1.dp, RegistreTheme.colors.sage),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = consigne,
                        onValueChange = { consigne = it },
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        textStyle = TextStyle(
                            color = RegistreTheme.colors.ink,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(RegistreTheme.colors.ink),
                        decorationBox = { champInterne ->
                            Column {
                                Text(
                                    text = "Que voulez-vous modifier ?",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RegistreTheme.colors.chalk,
                                )
                                if (consigne.isEmpty()) {
                                    Text(
                                        text = "Ex. Rends ce message plus professionnel",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RegistreTheme.colors.chalk,
                                        maxLines = 1,
                                    )
                                } else {
                                    champInterne()
                                }
                            }
                        },
                        maxLines = 2,
                    )
                }

                // Les deux actions principales.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionPuce(
                        texte = ActionIA.CORRIGER.libellé,
                        icône = Icons.Rounded.Spellcheck,
                        choisi = actionChoisie == ActionIA.CORRIGER,
                        surClic = { actionChoisie = ActionIA.CORRIGER },
                        modifier = Modifier.weight(1f),
                    )
                    ActionPuce(
                        texte = ActionIA.RÉÉCRIRE.libellé,
                        icône = Icons.Rounded.Edit,
                        choisi = actionChoisie == ActionIA.RÉÉCRIRE,
                        surClic = { actionChoisie = ActionIA.RÉÉCRIRE },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Les tons.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TonIA.entries.forEach { candidat ->
                        PuceTon(
                            ton = candidat,
                            choisi = candidat == ton,
                            onChoisir = { ton = candidat },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Déplié : les transformations explicites, icône + libellé.
                if (déplié) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Transformations.forEach { (action, icône) ->
                            ActionPuce(
                                texte = action.libellé,
                                icône = icône,
                                choisi = actionChoisie == action,
                                surClic = { actionChoisie = action },
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                erreur?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.accent.teinte,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }

                // L'action principale : ce qui va se passer ne fait aucun doute.
                Surface(
                    shape = ControlShape,
                    color = RegistreTheme.accent.teinte,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(ControlShape)
                        .clickable(enabled = !enCours) { lancer(actionChoisie) },
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (enCours) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = RegistreTheme.colors.page,
                            )
                        } else {
                            Text(
                                text = "✨ Générer",
                                style = MaterialTheme.typography.labelLarge,
                                color = RegistreTheme.colors.page,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Le résultat, une fois généré : texte, badge « Généré par IA », actions. */
@Composable
private fun RésultatIA(
    texteRésultat: String,
    enCours: Boolean,
    surRéessayer: () -> Unit,
    surRemplacer: () -> Unit,
    surAnnuler: () -> Unit,
) {
    Surface(
        shape = AnnotationShape,
        color = RegistreTheme.colors.sage,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = texteRésultat,
            style = MaterialTheme.typography.bodyMedium,
            color = RegistreTheme.colors.ink,
            modifier = Modifier
                .padding(10.dp)
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoutonPanneau(
            texte = "Remplacer",
            icône = Icons.Rounded.Check,
            surClic = surRemplacer,
            modifier = Modifier.weight(1f),
        )
        BoutonPanneau(
            texte = "Réessayer",
            icône = Icons.Rounded.RestartAlt,
            surClic = surRéessayer,
            chargement = enCours,
            modifier = Modifier.weight(1f),
        )
        BoutonPanneau(
            texte = "Annuler",
            icône = Icons.Rounded.Close,
            surClic = surAnnuler,
            modifier = Modifier.weight(1f),
        )
    }
    // Marquage « Généré par IA » (issue #58) : le résultat vient d'un modèle,
    // ça doit se voir.
    BadgeGénéréIA(modifier = Modifier.padding(top = 6.dp))
}

/** Une puce d'action : sélection dans l'accent du ✨, simple sinon. */
@Composable
private fun ActionPuce(
    texte: String,
    icône: ImageVector,
    choisi: Boolean,
    surClic: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        shape = ControlShape,
        color = if (choisi) RegistreTheme.accent.conteneur else RegistreTheme.colors.sage,
        modifier = modifier
            .clip(ControlShape)
            .clickable(onClick = surClic),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icône,
                contentDescription = null,
                tint = if (choisi) RegistreTheme.accent.teinte else RegistreTheme.colors.chalk,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = texte,
                style = MaterialTheme.typography.labelMedium,
                color = RegistreTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Un bouton de panneau : puce pleine, texte + icône. */
@Composable
private fun BoutonPanneau(
    texte: String,
    icône: ImageVector,
    surClic: () -> Unit,
    chargement: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ControlShape,
        color = RegistreTheme.colors.sage,
        modifier = modifier.clip(ControlShape).clickable(onClick = surClic),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (chargement) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = RegistreTheme.colors.ink,
                )
            } else {
                Icon(
                    imageVector = icône,
                    contentDescription = null,
                    tint = RegistreTheme.colors.ink,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = texte,
                style = MaterialTheme.typography.labelMedium,
                color = RegistreTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Une puce de ton : dans l'accent quand choisie. */
@Composable
private fun PuceTon(
    ton: TonIA,
    choisi: Boolean,
    onChoisir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = AnnotationShape,
        color = if (choisi) RegistreTheme.accent.conteneur else RegistreTheme.colors.sage,
        modifier = modifier
            .clip(AnnotationShape)
            .clickable(onClick = onChoisir),
    ) {
        Text(
            text = ton.libellé,
            style = MaterialTheme.typography.labelMedium,
            color = if (choisi) RegistreTheme.accent.teinte else RegistreTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
