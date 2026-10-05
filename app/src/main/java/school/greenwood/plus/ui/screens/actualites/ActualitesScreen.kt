package school.greenwood.plus.ui.screens.actualites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.R
import school.greenwood.plus.ui.ActualitesViewModel
import school.greenwood.plus.ui.components.AperçuImage
import school.greenwood.plus.ui.components.AperçuImageRapide
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.EcranBanniere
import school.greenwood.plus.ui.components.CarteActualité
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.GwsLoadingIndicator
import school.greenwood.plus.ui.components.SqueletteActualites
import school.greenwood.plus.ui.components.VisualisationImage
import school.greenwood.plus.ui.components.VisualiseurImages
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * L'onglet Actualités : flux d'annonces de l'école paginé (1-based),
 * avec pull-to-refresh et défilement infini.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActualitesScreen(
    container: AppContainer,
    padding: PaddingValues,
    ouvrirPost: (String) -> Unit,
) {
    val vm: ActualitesViewModel = viewModel { ActualitesViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val bannièreActivée by container.session.bannièreActualitésActivée.collectAsStateWithLifecycle(initialValue = true)

    // Défilement infini : déclenche la page suivante à l'approche de la fin (3 derniers éléments)
    val actualitésPrésentes = état.liste.isNotEmpty()
    val chargerPlus by remember(actualitésPrésentes) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            actualitésPrésentes && totalItems > 0 && lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(chargerPlus) {
        if (chargerPlus) {
            vm.pageSuivante()
        }
    }

    // Vignettes : plein écran (visualiseur) ou aperçu rapide au-dessus du flux.
    var visualisation: VisualisationImage? by remember { mutableStateOf(null) }
    var aperçu: AperçuImage? by remember { mutableStateOf(null) }

    EcranBanniere(
        image = R.drawable.actualites_banner,
        activée = bannièreActivée,
        padding = padding,
        liste = listState,
        rafraîchissement = état.rafraîchissement,
        surActualiser = { vm.rafraîchir() },
    ) {
        item(key = "entete") {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Text(
                text = "Actualités",
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
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Nouvelles et annonces de l'école",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
            )
        }
        }

        état.erreur?.let { err ->
            item(key = "erreur") {
            BandeauErreur(
                message = err,
                réessayer = { vm.charger(force = true) },
                modifier = Modifier.padding(vertical = 4.dp),
            )
            }
        }

            when {
                état.chargement && état.liste.isEmpty() -> {
                    item(key = "chargement") { SqueletteActualites(défilable = false) }
                }

                !état.chargement && état.liste.isEmpty() -> {
                    item(key = "vide") { EmptyState(
                        titre = "Aucune actualité",
                        message = "L'école n'a pas encore publié d'actualités.",
                        modifier = Modifier.padding(top = 40.dp),
                        icone = Icons.Rounded.Newspaper,
                    ) }
                }

                else -> {
                        items(état.liste, key = { it.id }) { post ->
                            CarteActualité(
                                post = post,
                                onClick = { ouvrirPost(post.id) },
                                ouvrirImage = { url ->
                                    visualisation = VisualisationImage(listOf(url), 0, post.title)
                                },
                                aperçuImage = { url ->
                                    aperçu = AperçuImage(url, post.title)
                                },
                            )
                        }

                        if (état.chargementPageSuivante) {
                            item(key = "chargement_suite") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    GwsLoadingIndicator(
                                        color = RegistreTheme.accent.teinte,
                                    )
                                }
                            }
                        }
                }
            }
    }

    // Surfaces plein écran, rendues au-dessus de tout l'écran.
    visualisation?.let { v ->
        VisualiseurImages(
            images = v.images,
            indexInitial = v.index,
            titre = v.titre,
            fermer = { visualisation = null },
        )
    }
    aperçu?.let { a ->
        AperçuImageRapide(
            url = a.url,
            titre = a.titre,
            agrandir = {
                visualisation = VisualisationImage(listOf(a.url), 0, a.titre)
                aperçu = null
            },
            fermer = { aperçu = null },
        )
    }
}
