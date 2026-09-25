package school.greenwood.plus.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Le panneau IA du composeur (issue #56), inspiré des Writing Tools d'Apple :
 * panneau flottant arrondi posé au-dessus du champ de saisie — un champ
 * « Décrivez votre modification », les actions rapides (Relire / Réécrire),
 * les tons (Amical / Professionnel / Concis) et les transformations
 * (Résumé / Points clés / Tableau / Liste). Le résultat s'affiche dans le
 * panneau : Remplacer renvoie le texte au composeur, Annuler referme.
 */

/** Une action proposée avec son icône. */
private data class TransformationIA(val action: ActionIA, val icône: ImageVector)

private val Transformations = listOf(
    TransformationIA(ActionIA.RÉSUMÉ, Icons.Rounded.Subject),
    TransformationIA(ActionIA.POINTS, Icons.Rounded.FormatListBulleted),
    TransformationIA(ActionIA.TABLEAU, Icons.Rounded.GridView),
    TransformationIA(ActionIA.LISTE, Icons.Rounded.ShortText),
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
    var enCours by remember { mutableStateOf<ActionIA?>(null) }
    var dernièreAction by remember { mutableStateOf<ActionIA>(ActionIA.RÉÉCRIRE) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var résultat by remember { mutableStateOf<String?>(null) }

    fun lancer(action: ActionIA) {
        if (enCours != null) return
        dernièreAction = action
        portée.launch {
            enCours = action
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
            enCours = null
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
        Column(modifier = Modifier.padding(12.dp)) {
            // Le résultat prend le dessus du panneau, s'il y en a un.
            résultat?.let { texteRésultat ->
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
                        surClic = {
                            onRemplacer(texteRésultat)
                            onFermer()
                        },
                    )
                    BoutonPanneau(
                        texte = "Réessayer",
                        icône = Icons.Rounded.RestartAlt,
                        surClic = {
                            résultat = null
                            lancer(dernièreAction)
                        },
                    )
                    BoutonPanneau(
                        texte = "Annuler",
                        icône = Icons.Rounded.Close,
                        surClic = {
                            résultat = null
                            onFermer()
                        },
                    )
                }
                // Marquage « Généré par IA » (issue #58) : le résultat vient
                // d'un modèle, ça doit se voir.
                BadgeGénéréIA(modifier = Modifier.padding(top = 6.dp))
            } ?: run {
                // Champ « Décrivez votre modification » (Writing Tools).
                androidx.compose.foundation.text.BasicTextField(
                    value = consigne,
                    onValueChange = { consigne = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp),
                    textStyle = TextStyle(
                        color = RegistreTheme.colors.ink,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(RegistreTheme.colors.ink),
                    decorationBox = { champInterne ->
                        if (consigne.isEmpty()) {
                            Text(
                                text = "Décrivez votre modification…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RegistreTheme.colors.chalk,
                                maxLines = 1,
                            )
                        }
                        champInterne()
                    },
                    maxLines = 2,
                )

                // Actions rapides : Relire / Réécrire.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoutonPanneau(
                        texte = ActionIA.RELIRE.libellé,
                        icône = Icons.Rounded.AutoAwesome,
                        surClic = { lancer(ActionIA.RELIRE) },
                        chargement = enCours == ActionIA.RELIRE,
                        modifier = Modifier.weight(1f),
                    )
                    BoutonPanneau(
                        texte = ActionIA.RÉÉCRIRE.libellé,
                        icône = Icons.Rounded.Edit,
                        surClic = { lancer(ActionIA.RÉÉCRIRE) },
                        chargement = enCours == ActionIA.RÉÉCRIRE,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Les tons prédéfinis.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
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

                // Les transformations, une rangée d'icônes à la Apple.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Transformations.forEach { (action, icône) ->
                        IconButton(onClick = { lancer(action) }, enabled = enCours == null) {
                            if (enCours == action) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = RegistreTheme.colors.ink,
                                )
                            } else {
                                Icon(
                                    imageVector = icône,
                                    contentDescription = action.libellé,
                                    tint = RegistreTheme.colors.chalk,
                                )
                            }
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
            }
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

/** Une puce de ton : contour, pleine quand choisie. */
@Composable
private fun PuceTon(
    ton: TonIA,
    choisi: Boolean,
    onChoisir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = AnnotationShape,
        color = if (choisi) RegistreTheme.colors.ink else RegistreTheme.colors.sage,
        modifier = modifier
            .clip(AnnotationShape)
            .clickable(onClick = onChoisir),
    ) {
        Text(
            text = ton.libellé,
            style = MaterialTheme.typography.labelMedium,
            color = if (choisi) RegistreTheme.colors.page else RegistreTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
