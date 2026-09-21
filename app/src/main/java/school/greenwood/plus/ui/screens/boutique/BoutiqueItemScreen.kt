package school.greenwood.plus.ui.screens.boutique

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import school.greenwood.plus.AppContainer
import school.greenwood.plus.ui.ProduitViewModel
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.SquelettePostDetail
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.tabulaire
import school.greenwood.plus.ui.prixUnitaire
import school.greenwood.plus.util.htmlToPlainMultiline

/*
 * Le détail d'un produit (GET `shop?product=…`). Le POST crée la commande
 * immédiatement (sonde du 21/09/2026) : la confirmation est demandée avant
 * l'envoi, le succès affiche l'alerte du serveur puis rebascule en arrière.
 * En mode modification (commandeId non nul), les choix serveurs préremplissent
 * l'écran et le bouton met la commande à jour.
 */

@Composable
fun BoutiqueItemScreen(
    container: AppContainer,
    padding: PaddingValues,
    produitId: String,
    commandeId: String? = null,
    retour: () -> Unit,
) {
    val vm: ProduitViewModel = viewModel(key = "boutique_produit_${produitId}_$commandeId") {
        ProduitViewModel(container, produitId, commandeId)
    }
    val état by vm.état.collectAsStateWithLifecycle()
    var confirmerOuverte by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = retour) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Retour",
                    tint = RegistreTheme.colors.ink,
                )
            }
            Column {
                Text(
                    text = if (commandeId != null) "Modifier la commande" else "Produit",
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(AnnotationShape)
                        .background(RegistreTheme.accent.conteneur),
                )
            }
        }

        état.erreur?.let { erreur ->
            ErrorInline(message = erreur)
        }

        when {
            état.chargement && état.produit == null -> SquelettePostDetail()
            état.produit == null -> {}
            else -> {
                val produit = état.produit ?: return

                // La photo du produit : page pleine, fond sage sous l'image.
                GwsCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.25f)
                            .background(RegistreTheme.colors.sage),
                    ) {
                        produit.image?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = produit.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = produit.label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = RegistreTheme.colors.ink,
                    )
                    Text(
                        text = "${prixUnitaire(produit, état.varianteId) ?: "?"} DH",
                        style = MaterialTheme.typography.titleLarge.tabulaire(),
                        color = RegistreTheme.accent.teinte,
                    )
                }

                produit.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc.htmlToPlainMultiline(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RegistreTheme.colors.chalk,
                    )
                }

                if (produit.variantes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Taille",
                            style = MaterialTheme.typography.titleMedium,
                            color = RegistreTheme.colors.ink,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            produit.variantes.forEach { variante ->
                                PuceTaille(
                                    label = variante.label,
                                    sélectionnée = variante.id == état.varianteId,
                                    onClick = { vm.choisirVariante(variante.id) },
                                )
                            }
                        }
                        état.varianteId?.let { id ->
                            produit.variantes.firstOrNull { it.id == id }?.stock?.let { stock ->
                                Text(
                                    text = "En stock : $stock",
                                    style = MaterialTheme.typography.labelSmall.tabulaire(),
                                    color = RegistreTheme.colors.chalk,
                                )
                            }
                        }
                    }
                }

                // La quantité, sous le doigt : moins, chiffre, plus.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Quantité",
                        style = MaterialTheme.typography.titleMedium,
                        color = RegistreTheme.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = ControlShape,
                        color = RegistreTheme.colors.page,
                        border = BorderStroke(1.dp, RegistreTheme.colors.sage),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.modifierQuantité(-1) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Moins",
                                    tint = RegistreTheme.colors.ink,
                                )
                            }
                            Text(
                                text = état.quantité.toString(),
                                style = MaterialTheme.typography.titleMedium.tabulaire(),
                                color = RegistreTheme.colors.ink,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            IconButton(onClick = { vm.modifierQuantité(+1) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Plus",
                                    tint = RegistreTheme.colors.ink,
                                )
                            }
                        }
                    }
                }

                // Le commentaire de la commande (une précision pour l'école).
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Commentaire",
                        style = MaterialTheme.typography.titleMedium,
                        color = RegistreTheme.colors.ink,
                    )
                    Surface(
                        shape = ControlShape,
                        color = RegistreTheme.colors.page,
                        border = BorderStroke(1.dp, RegistreTheme.colors.sage),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BasicTextField(
                            value = état.commentaire,
                            onValueChange = vm::modifierCommentaire,
                            minLines = 2,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = RegistreTheme.colors.ink,
                            ),
                            cursorBrush = SolidColor(RegistreTheme.colors.ink),
                            modifier = Modifier.padding(12.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (état.commentaire.isEmpty()) {
                                        Text(
                                            text = "Une précision pour l'école ? (facultatif)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = RegistreTheme.colors.chalk,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // L'action : le serveur commande immédiatement — la
                // confirmation est demandée avant l'envoi, jamais de
                // commande par accident. Un produit fermé à la commande
                // (can_add_to_cart, hors modification) reste affiché mais
                // désactivé.
                val commandable = produit.peutCommander || commandeId != null
                Button(
                    onClick = { confirmerOuverte = true },
                    enabled = commandable && !état.envoi,
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistreTheme.colors.ink,
                        contentColor = RegistreTheme.colors.page,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = boutonCommander(
                            prixUnitaire = prixUnitaire(produit, état.varianteId),
                            quantité = état.quantité,
                            modification = commandeId != null,
                        ),
                        style = MaterialTheme.typography.labelLarge.tabulaire(),
                    )
                }
                if (!commandable) {
                    Text(
                        text = "Ce produit ne se commande pas pour le moment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // La confirmation avant envoi, puis l'alerte de succès du serveur.
    val produit = état.produit
    if (confirmerOuverte && produit != null) {
        val prixUnitaire = prixUnitaire(produit, état.varianteId)
        val total = prixUnitaire?.toDoubleOrNull()?.times(état.quantité)
        AlertDialog(
            onDismissRequest = { confirmerOuverte = false },
            title = {
                Text(
                    text = if (commandeId != null) "Mettre à jour la commande ?" else "Confirmer la commande ?",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = buildString {
                        append(produit.label)
                        état.taille?.let { append(" — $it") }
                        append(", ×${état.quantité}")
                        total?.let { append(" — ${libelléPrix(it)}") }
                    },
                    style = MaterialTheme.typography.bodyMedium.tabulaire(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmerOuverte = false
                        vm.commander()
                    },
                    shape = ControlShape,
                    enabled = !état.envoi,
                ) {
                    Text(if (commandeId != null) "Mettre à jour" else "Commander")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmerOuverte = false }) {
                    Text("Annuler")
                }
            },
        )
    }

    état.succès?.let { résultat ->
        AlertDialog(
            onDismissRequest = { vm.acquis(); retour() },
            title = {
                Text(
                    text = résultat.titre ?: if (résultat.succès) "Commande passée" else "Commande impossible",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = résultat.message ?: "Ton retour est dans l'historique des commandes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.acquis()
                        retour()
                    },
                    shape = ControlShape,
                ) {
                    Text("Fermer")
                }
            },
        )
    }
}

/** Le libellé du bouton : total lisible (« Commander — 300 DH ») ou commande
 *  simple quand le prix ne se lit pas (testé). */
fun boutonCommander(prixUnitaire: String?, quantité: Int, modification: Boolean): String {
    val verbe = if (modification) "Mettre à jour" else "Commander"
    val total = prixUnitaire?.toDoubleOrNull()?.times(quantité) ?: return verbe
    return "$verbe — ${libelléPrix(total)}"
}

/** Le prix en libellé français : entier sans décimales, décimales sinon. */
fun libelléPrix(total: Double): String {
    val arrondi = (total * 100).toInt() / 100.0
    return if (arrondi == arrondi.toInt().toDouble()) {
        "${arrondi.toInt()} DH"
    } else {
        "$arrondi DH"
    }
}

/** Puce de taille — la variante choisie se teinte de l'accent de l'onglet. */
@Composable
private fun PuceTaille(
    label: String,
    sélectionnée: Boolean,
    onClick: () -> Unit,
) {
    val accent = RegistreTheme.accent
    Surface(
        shape = ControlShape,
        color = if (sélectionnée) accent.conteneur else RegistreTheme.colors.page,
        border = if (sélectionnée) null else BorderStroke(1.dp, RegistreTheme.colors.sage),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium.tabulaire(),
            color = if (sélectionnée) accent.surConteneur else RegistreTheme.colors.chalk,
        )
    }
}
