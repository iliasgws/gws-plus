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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.CantineJour
import school.greenwood.plus.model.ProduitBoutique
import school.greenwood.plus.model.RubriqueBoutique
import school.greenwood.plus.ui.BoutiqueViewModel
import school.greenwood.plus.ui.filtrerProduits
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.BlocSquelette
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.tabulaire

/*
 * La Boutique de l'école (docs/product/DESIGN.md §4), son catalogue :
 * rubriques en puces de filtre, recherche, grille de produits. Une commande
 * se passe depuis le détail — le serveur crée la commande immédiatement
 * (sonde du 21/09/2026), le panier ne participe pas dans ce déploiement.
 */

@Composable
fun BoutiqueScreen(
    container: AppContainer,
    padding: PaddingValues,
    ouvrirProduit: (String) -> Unit,
    ouvrirHistorique: () -> Unit,
) {
    val vm: BoutiqueViewModel = viewModel { BoutiqueViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()
    var jourChoisi by remember { mutableStateOf<CantineJour?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Boutique",
                        style = MaterialTheme.typography.displayLarge,
                        color = RegistreTheme.colors.ink,
                    )
                    // Le surligneur : le trait de l'onglet, sous le titre.
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(width = 56.dp, height = 5.dp)
                            .clip(AnnotationShape)
                            .background(RegistreTheme.accent.conteneur),
                    )
                }
                IconButton(onClick = ouvrirHistorique) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = "Historique des commandes",
                        tint = RegistreTheme.colors.chalk,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (état.rubriques.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    état.rubriques.forEach { rubrique ->
                        PuceRubrique(
                            rubrique = rubrique,
                            sélectionnée = rubrique.id == état.rubriqueActive,
                            onClick = { vm.choisirRubrique(rubrique.id) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            ChampRecherche(
                valeur = état.recherche,
                onChange = vm::modifierRecherche,
            )
        }

        when {
            état.chargement -> SqueletteBoutique()
            état.erreur != null && état.produits.isEmpty() -> Column(
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
            else -> {
                val filtrés = filtrerProduits(état.produits, état.recherche)
                Column(Modifier.fillMaxSize()) {
                    état.erreur?.let { message ->
                        BandeauErreur(
                            message = message,
                            réessayer = { vm.charger(force = true) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    when {
                        // La rubrique « Repas invité » ne porte pas de produits :
                        // le serveur y met le planning des menus (cantines[]).
                        filtrés.isEmpty() && état.cantines.isNotEmpty() ->
                            PlanningCantine(
                                jours = état.cantines,
                                envoi = état.envoiRepas,
                                surRéserver = { jour -> jourChoisi = jour },
                            )
                        filtrés.isEmpty() && état.recherche.isNotBlank() ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyState(
                                    titre = "Rien trouvé",
                                    message = "Essaie un autre mot.",
                                    icone = Icons.Rounded.SearchOff,
                                )
                            }
                        filtrés.isEmpty() ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyState(
                                    titre = "Boutique vide",
                                    message = "Aucun produit dans cette rubrique pour le moment.",
                                    icone = Icons.Rounded.ShoppingBag,
                                )
                            }
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 16.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(filtrés, key = { it.id }) { produit ->
                                    CarteProduit(
                                        produit = produit,
                                        onClick = { ouvrirProduit(produit.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    jourChoisi?.let { jour ->
        DialogueConfirmationRepas(
            jour = jour,
            envoi = état.envoiRepas,
            onConfirmer = {
                vm.réserverRepas(jour)
                jourChoisi = null
            },
            onFermer = { jourChoisi = null },
        )
    }

    état.succèsRepas?.let { résultat ->
        DialogueSuccèsRepas(
            résultat = résultat,
            onFermer = { vm.acquisRepas() },
        )
    }
}

/** Puce de rubrique — même gabarit que le filtre des Documents, accent
 *  de l'onglet Boutique quand la rubrique est celle qu'on regarde. */
@Composable
private fun PuceRubrique(
    rubrique: RubriqueBoutique,
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
            text = rubrique.label.ifBlank { "Tout" },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (sélectionnée) accent.surConteneur else RegistreTheme.colors.chalk,
        )
    }
}

/** Champ de recherche — Surface + BasicTextField, gabarit du thème. */
@Composable
private fun ChampRecherche(
    valeur: String,
    onChange: (String) -> Unit,
) {
    Surface(
        shape = ControlShape,
        color = RegistreTheme.colors.page,
        border = BorderStroke(1.dp, RegistreTheme.colors.sage),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = RegistreTheme.colors.chalk,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = valeur,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = RegistreTheme.colors.ink,
                ),
                cursorBrush = SolidColor(RegistreTheme.colors.ink),
                decorationBox = { inner ->
                    Box {
                        if (valeur.isEmpty()) {
                            Text(
                                text = "Rechercher un produit",
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
}

/** Une carte produit : la photo d'abord, l'étiquette et le prix ensuite. */
@Composable
private fun CarteProduit(
    produit: ProduitBoutique,
    onClick: () -> Unit,
) {
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = produit.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                produit.prix?.let { prix ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = prix,
                        style = MaterialTheme.typography.titleSmall.tabulaire(),
                        color = RegistreTheme.accent.teinte,
                    )
                }
            }
        }
    }
}

/** Squelette du catalogue : puces, recherche, grille de pages pulsées. */
@Composable
private fun SqueletteBoutique() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                BlocSquelette(
                    modifier = Modifier.width(90.dp).height(32.dp),
                    forme = ControlShape,
                )
            }
        }
        BlocSquelette(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            forme = ControlShape,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                BlocSquelette(
                    modifier = Modifier.weight(1f).height(170.dp),
                    forme = PageShape,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                BlocSquelette(
                    modifier = Modifier.weight(1f).height(170.dp),
                    forme = PageShape,
                )
            }
        }
        // Marge du bas, respire le même air que la grille réelle.
        Box(Modifier.size(1.dp).clip(CircleShape))
    }
}
