package school.greenwood.plus.ui.screens.boutique

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import school.greenwood.plus.model.CantineJour
import school.greenwood.plus.model.RésultatCommande
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.tabulaire
import school.greenwood.plus.util.extractDate
import school.greenwood.plus.util.frenchLongDay

/*
 * Le planning des repas invités (`cantines[]` du GET `shop?rubrique=2`,
 * sonde du 21/09/2026) : un jour par carte, l'état de réservation du
 * serveur, un seul geste — réserver le jour tapé. Le jour part dans le
 * commentaire de la commande (POST vérifié) ; le libellé « Réservé » du
 * planning ne compte que les commandes validées.
 */

@Composable
fun PlanningCantine(
    jours: List<CantineJour>,
    envoi: Boolean,
    surRéserver: (CantineJour) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "titre-planning") {
            SectionLabel(text = "Menus des prochains jours", pointAccent = true)
        }
        items(jours, key = { "${it.id}-${it.jourValeur ?: it.jourDate ?: ""}" }) { jour ->
            CarteJour(
                jour = jour,
                envoi = envoi,
                surRéserver = surRéserver,
            )
        }
    }
}

/** Un jour du planning : réservable si le serveur le dit et pas déjà pris. */
@Composable
private fun CarteJour(
    jour: CantineJour,
    envoi: Boolean,
    surRéserver: (CantineJour) -> Unit,
) {
    val réservable = jour.peutRéserver && !jour.déjàRéservé && !envoi
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = réservable) { surRéserver(jour) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AnnotationShape)
                    .background(RegistreTheme.colors.sage),
            ) {
                jour.image?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = jour.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = jour.jourLabel?.takeIf { it.isNotBlank() }
                        ?: jour.jourDate ?: "Repas invité",
                    style = MaterialTheme.typography.titleSmall,
                    color = RegistreTheme.colors.ink,
                )
                jour.jourLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = jour.jourDate ?: "",
                        style = MaterialTheme.typography.labelSmall.tabulaire(),
                        color = RegistreTheme.colors.chalk,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    jour.dispoLabel?.let { dispo ->
                        PuceDisponibilité(
                            label = dispo,
                            fond = jour.dispoCouleur,
                        )
                    }
                    jour.prix?.let { prix ->
                        Text(
                            text = prix,
                            style = MaterialTheme.typography.titleSmall.tabulaire(),
                            color = RegistreTheme.accent.teinte,
                        )
                    }
                }
            }
            when {
                jour.déjàRéservé -> Puce(label = jour.réservéLibellé ?: "Réservé")
                jour.peutRéserver -> Surface(
                    shape = ControlShape,
                    color = RegistreTheme.accent.conteneur,
                ) {
                    Text(
                        text = jour.réservéLibellé?.takeIf { !it.startsWith("Réservé") }
                            ?: "Réserver",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = RegistreTheme.accent.surConteneur,
                    )
                }
            }
        }
    }
}

/** La disponibilité du jour, teintée des couleurs du serveur (#RRGGBB). */
@Composable
private fun PuceDisponibilité(
    label: String,
    fond: String?,
) {
    val couleur = couleurHex(fond)
    if (couleur == null) {
        Puce(label = label)
    } else {
        Box(
            modifier = Modifier
                .clip(AnnotationShape)
                .background(couleur)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = RegistreTheme.colors.ink,
            )
        }
    }
}

/** #RRGGBB (ou #AARRGGBB) → Color, null si illisible (testé, sans
 *  `android.graphics` pour rester pur Kotlin). */
fun couleurHex(hex: String?): Color? {
    val brut = hex?.removePrefix("#") ?: return null
    if (brut.isEmpty()) return null
    val digits = brut.toLongOrNull(16) ?: return null
    return when (brut.length) {
        6 -> Color(0xFF000000L or digits)
        8 -> Color(digits)
        else -> null
    }
}

/** Le libellé du jour pour le dialogue : « mercredi 23 septembre ». */
fun libelléJourRepas(jour: CantineJour): String =
    jour.jourValeur?.let { extractDate(it) }?.frenchLongDay()
        ?: jour.jourDate ?: jour.jourValeur ?: jour.jourLabel ?: ""

/** La confirmation avant le POST : le jour est relu noir sur blanc. */
@Composable
fun DialogueConfirmationRepas(
    jour: CantineJour,
    envoi: Boolean,
    onConfirmer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = {
            Text(
                text = "Réserver ce repas ?",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = buildString {
                    append("Repas invité du ")
                    append(libelléJourRepas(jour))
                    jour.prix?.let { append(" — $it") }
                    append(".")
                    append("\nLe jour sera précisé dans le commentaire de la commande.")
                },
                style = MaterialTheme.typography.bodyMedium.tabulaire(),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmer,
                enabled = !envoi,
                shape = ControlShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RegistreTheme.colors.ink,
                    contentColor = RegistreTheme.colors.page,
                ),
            ) {
                Text("Réserver")
            }
        },
        dismissButton = {
            TextButton(onClick = onFermer) {
                Text("Annuler")
            }
        },
    )
}

/** Le succès du POST, alerte du serveur. */
@Composable
fun DialogueSuccèsRepas(
    résultat: RésultatCommande,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = {
            Text(
                text = résultat.titre
                    ?: if (résultat.succès) "Commande passée" else "Réservation impossible",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = résultat.message ?: "",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onFermer,
                shape = ControlShape,
            ) {
                Text("Fermer")
            }
        },
    )
}
