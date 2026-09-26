package school.greenwood.plus.ui.screens.boutique

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.ArticleCommande
import school.greenwood.plus.model.CommandeBoutique
import school.greenwood.plus.ui.HistoriqueViewModel
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SquelettePostDetail
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.tabulaire

/*
 * L'historique des commandes de la boutique (GET `shop?new_history=true`).
 * Tant qu'une commande est « en-cours », le serveur laisse l'éditer (elle
 * repart par le détail du produit) et supprimer (POST delete_history) — les
 * drapeaux `can_edit` / `can_delete` viennent de lui, jamais de nous.
 */

@Composable
fun BoutiqueHistoriqueScreen(
    container: AppContainer,
    padding: PaddingValues,
    ouvrirModifier: (produitId: String, commandeId: String) -> Unit,
    retour: () -> Unit,
) {
    val vm: HistoriqueViewModel = viewModel { HistoriqueViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()

    // La suppression attend une confirmation — un effacement ne part pas d'un
    // appui de travers. Pair(commandeId, articleId nullable).
    var confirmation by remember { mutableStateOf<Pair<String, String?>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = retour) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Retour",
                        tint = RegistreTheme.colors.ink,
                    )
                }
                Column {
                    Text(
                        text = "Mes commandes",
                        style = MaterialTheme.typography.titleLarge,
                        color = RegistreTheme.colors.ink,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(AnnotationShape)
                            .background(RegistreTheme.accent.conteneur),
                    )
                }
            }
        }

        when {
            état.chargement -> Column(Modifier.padding(horizontal = 16.dp)) {
                repeat(3) {
                    SquelettePostDetail()
                    Spacer(Modifier.height(12.dp))
                }
            }
            état.erreur != null && état.commandes.isEmpty() -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorInline(message = état.erreur ?: "")
                Button(
                    onClick = { vm.charger(force = true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistreTheme.colors.ink,
                        contentColor = RegistreTheme.colors.page,
                    ),
                    shape = ControlShape,
                ) {
                    Text("Réessayer")
                }
            }
            état.commandes.isEmpty() -> Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    titre = "Aucune commande",
                    message = "Les commandes passées à la boutique apparaîtront ici.",
                    icone = Icons.Rounded.ReceiptLong,
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(état.commandes, key = { it.id }) { commande ->
                    CarteCommande(
                        commande = commande,
                        suppressionEnCours = état.suppression == commande.id,
                        onModifier = { article ->
                            commande.id.let { c ->
                                article.produitId?.let { p -> ouvrirModifier(p, c) }
                            }
                        },
                        onSupprimerArticle = { article ->
                            confirmation = commande.id to article.id
                        },
                        onSupprimerCommande = {
                            confirmation = commande.id to null
                        },
                    )
                }
            }
        }
    }

    confirmation?.let { (commandeId, articleId) ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    text = if (articleId == null) "Supprimer la commande ?" else "Retirer cet article ?",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = if (articleId == null) {
                        "La commande entière sera effacée de l'historique."
                    } else {
                        "L'article sera retiré de la commande."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.supprimer(commandeId, articleId)
                        confirmation = null
                    },
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistreTheme.colors.redPen,
                        contentColor = RegistreTheme.colors.page,
                    ),
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text("Annuler")
                }
            },
        )
    }
}

/** Une commande passée : sa date, son état, ses articles, ses gestes. */
@Composable
private fun CarteCommande(
    commande: CommandeBoutique,
    suppressionEnCours: Boolean,
    onModifier: (ArticleCommande) -> Unit,
    onSupprimerArticle: (ArticleCommande) -> Unit,
    onSupprimerCommande: () -> Unit,
) {
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = commande.date ?: "Commande",
                        style = MaterialTheme.typography.titleSmall.tabulaire(),
                        color = RegistreTheme.colors.ink,
                    )
                    Text(
                        text = "${commande.articles.size} article(s)",
                        style = MaterialTheme.typography.labelSmall.tabulaire(),
                        color = RegistreTheme.colors.chalk,
                    )
                }
                // L'état serveur : « validée » se félicite du vert du Registre,
                // les autres états restent en annotation neutre.
                val vert = RegistreTheme.colors.accents["registre"]
                commande.étatLabel?.let { étatLabel ->
                    val validée = commande.étatAlias == "validée" || commande.étatAlias == "validee"
                    Puce(
                        label = étatLabel,
                        accent = if (validée) vert else null,
                    )
                }
                commande.prix?.let { prix ->
                    Text(
                        text = "$prix DH",
                        style = MaterialTheme.typography.titleSmall.tabulaire(),
                        color = RegistreTheme.accent.teinte,
                    )
                }
            }

            commande.articles.forEach { article ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(AnnotationShape)
                            .background(RegistreTheme.colors.sage),
                    ) {
                        article.image?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = article.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = article.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RegistreTheme.colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val détails = listOfNotNull(
                            article.taille,
                            article.quantité?.let { "×$it" },
                        ).joinToString(" · ")
                        if (détails.isNotBlank()) {
                            Text(
                                text = détails,
                                style = MaterialTheme.typography.labelSmall.tabulaire(),
                                color = RegistreTheme.colors.chalk,
                            )
                        }
                    }
                    if (article.modifiable) {
                        IconButton(onClick = { onModifier(article) }) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Modifier l'article",
                                tint = RegistreTheme.colors.chalk,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (article.supprimable) {
                        IconButton(
                            onClick = { onSupprimerArticle(article) },
                            enabled = !suppressionEnCours,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Supprimer l'article",
                                tint = RegistreTheme.colors.chalk,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            if (commande.supprimable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButtonSmall(
                        label = "Supprimer la commande",
                        onClick = onSupprimerCommande,
                    )
                }
            }
        }
    }
}

/** Le geste discret de suppression d'une commande entière. */
@Composable
private fun TextButtonSmall(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = RegistreTheme.colors.chalk,
        )
    }
}
