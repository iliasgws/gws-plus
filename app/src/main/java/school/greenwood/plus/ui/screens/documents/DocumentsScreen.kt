package school.greenwood.plus.ui.screens.documents

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.Ressource
import school.greenwood.plus.ui.DocumentsViewModel
import school.greenwood.plus.ui.FiltreDocuments
import school.greenwood.plus.ui.estQuiz
import school.greenwood.plus.ui.filtrerRessources
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.components.SqueletteDocuments
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.htmlToPlainSingleLine

/*
 * L'espace documents (docs/product/DESIGN.md §4). Le serveur ne propose aujourd'hui que
 * des ressources par matière (souvent des quiz) : liste groupée, recherche
 * locale, filtre par nature — quiz ou documents (issue #17). Les quiz
 * s'ouvrent sur l'écran de quiz (GET `quiz` vérifié le 19/09/2026) ; les
 * autres ressources restent inertes (ressource_details non vérifié).
 */

@Composable
fun DocumentsScreen(
    container: AppContainer,
    padding: PaddingValues,
    onOuvrirQuiz: (String) -> Unit,
) {
    val vm: DocumentsViewModel = viewModel { DocumentsViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Documents",
                style = MaterialTheme.typography.displayLarge,
                color = RegistreTheme.colors.ink,
            )
            Spacer(Modifier.height(12.dp))
            ChampRecherche(
                valeur = état.recherche,
                onChange = vm::modifierRecherche,
            )
            Spacer(Modifier.height(10.dp))
            FiltreNature(
                choisi = état.filtre,
                onChange = vm::choisirFiltre,
            )
        }

        when {
            état.chargement -> SqueletteDocuments()
            état.erreur != null && état.ressources.isEmpty() -> Column(
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
                // Bandeau discret sous la recherche et les filtres quand un
                // échec réseau laisse le contenu connu affiché (issue #21).
                val filtrées = filtrerRessources(état.ressources, état.recherche, état.filtre)
                Column(Modifier.fillMaxSize()) {
                    état.erreur?.let { message ->
                        BandeauErreur(
                            message = message,
                            réessayer = { vm.charger(force = true) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (état.ressources.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                titre = "Aucun document",
                                message = "Les ressources de la classe apparaîtront ici.",
                            )
                        }
                    } else if (filtrées.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                titre = "Rien trouvé",
                                message = if (état.recherche.isBlank()) {
                                    "Aucune ressource de ce type pour le moment."
                                } else {
                                    "Essaie un autre mot."
                                },
                            )
                        }
                    } else {
                        // Groupement par matière, ordre du serveur préservé.
                        val groupes = filtrées.groupBy { it.matiere.ifBlank { "Général" } }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            groupes.forEach { (matiere, items) ->
                                item(key = "section-$matiere") {
                                    SectionLabel(matiere)
                                }
                                items(
                                    items.size,
                                    key = { i -> "${items[i].id}-$i" },
                                ) { i ->
                                    LigneRessource(
                                        items[i],
                                        ouvrirQuiz = onOuvrirQuiz,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Filtre par nature : Tout / Quiz / Documents (issue #17), même gabarit que
 *  le sélecteur de jour des Devoirs. */
@Composable
private fun FiltreNature(
    choisi: FiltreDocuments,
    onChange: (FiltreDocuments) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FiltreDocuments.entries.forEach { filtre ->
            PuceFiltre(
                label = filtre.label,
                sélectionné = filtre == choisi,
                onClick = { onChange(filtre) },
            )
        }
    }
}

@Composable
private fun PuceFiltre(
    label: String,
    sélectionné: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = ControlShape,
        color = if (sélectionné) RegistreTheme.colors.sage else RegistreTheme.colors.page,
        border = if (sélectionné) null else BorderStroke(1.dp, RegistreTheme.colors.sage),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (sélectionné) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
        )
    }
}

/** Champ de recherche — Surface + BasicTextField, gabarit du thème (12 dp). */
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
                                text = "Rechercher",
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

@Composable
private fun LigneRessource(
    ressource: Ressource,
    ouvrirQuiz: (String) -> Unit,
) {
    val quiz = estQuiz(ressource)
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (quiz) {
                    Modifier.clickable { ouvrirQuiz(ressource.id) }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Monogramme du type sur annotation sage (6 dp).
            Surface(
                shape = AnnotationShape,
                color = RegistreTheme.colors.sage,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = ressource.type?.take(1)?.uppercase()?.ifEmpty { "•" } ?: "•",
                        style = MaterialTheme.typography.titleMedium,
                        color = RegistreTheme.colors.ink,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = ressource.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ressource.presentation?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it.htmlToPlainSingleLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ressource.type?.takeIf { it.isNotBlank() }?.let {
                Puce(it)
            }
        }
    }
}
