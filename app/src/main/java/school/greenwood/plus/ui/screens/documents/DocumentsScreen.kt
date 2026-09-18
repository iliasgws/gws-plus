package school.greenwood.plus.ui.screens.documents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.htmlToPlainSingleLine

/*
 * L'espace documents (DESIGN.md §4). Le serveur ne propose aujourd'hui que
 * des ressources par matière (souvent des quiz) : liste groupée, recherche
 * locale. Pas d'invention de téléchargement — les URLs n'existent pas ici.
 */

@Composable
fun DocumentsScreen(
    container: AppContainer,
    padding: PaddingValues,
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
        }

        when {
            état.chargement -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RegistreTheme.colors.ink)
            }
            état.erreur != null -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorInline(message = état.erreur ?: "")
                Button(
                    onClick = { vm.charger() },
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
                val requête = état.recherche.trim()
                val filtrées = if (requête.isEmpty()) {
                    état.ressources
                } else {
                    état.ressources.filter {
                        it.label.contains(requête, ignoreCase = true) ||
                            it.matiere.contains(requête, ignoreCase = true)
                    }
                }
                if (état.ressources.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            titre = "Aucun document",
                            message = "Les ressources de la classe apparaîtront ici.",
                        )
                    }
                } else if (filtrées.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            titre = "Rien trouvé",
                            message = "Essaie un autre mot.",
                        )
                    }
                } else {
                    // Groupement par matière, ordre du serveur préservé.
                    val groupes = filtrées.groupBy { it.matiere.ifBlank { "Général" } }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
                                LigneRessource(items[i])
                            }
                        }
                    }
                }
            }
        }
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
private fun LigneRessource(ressource: Ressource) {
    GwsCard(modifier = Modifier.fillMaxWidth()) {
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
