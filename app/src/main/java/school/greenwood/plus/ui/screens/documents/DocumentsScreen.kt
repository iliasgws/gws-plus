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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.FicheBibliotheque
import school.greenwood.plus.model.Ressource
import school.greenwood.plus.ui.DocumentsViewModel
import school.greenwood.plus.ui.FiltreDocuments
import school.greenwood.plus.ui.estQuiz
import school.greenwood.plus.ui.filtrerFiches
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
import school.greenwood.plus.util.Fichiers
import school.greenwood.plus.util.htmlToPlainSingleLine
import java.io.File

/*
 * L'espace documents (docs/product/DESIGN.md §4). Deux sources (issue #43) :
 * la Bibliothèque des enseignants (GET `bibliotheque` — documents datés,
 * téléchargeables) et les exercices interactifs (GET `ressources_v2` —
 * souvent des quiz). Liste groupée par matière, recherche locale, filtre par
 * nature — quiz ou documents (issue #17). Les quiz s'ouvrent sur l'écran de
 * quiz (GET `quiz` vérifié le 19/09/2026) ; les autres ressources restent
 * inertes. Les fiches de la Bibliothèque se téléchargent via leur détail
 * (GET `ressource_details?ressource=<id>` vérifié le 22/09/2026) qui porte
 * l'URL média signée.
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
            // Le surligneur : le trait de l'onglet, sous le titre.
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(width = 56.dp, height = 5.dp)
                    .clip(AnnotationShape)
                    .background(RegistreTheme.accent.conteneur),
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
            état.erreur != null && état.ressources.isEmpty() && état.bibliotheque.isEmpty() -> Column(
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
                val fiches = filtrerFiches(état.bibliotheque, état.recherche, état.filtre)
                val context = LocalContext.current
                Column(Modifier.fillMaxSize()) {
                    état.erreur?.let { message ->
                        BandeauErreur(
                            message = message,
                            réessayer = { vm.charger(force = true) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (état.ressources.isEmpty() && état.bibliotheque.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                titre = "Aucun document",
                                message = "Les ressources de la classe apparaîtront ici.",
                                icone = Icons.Rounded.Folder,
                            )
                        }
                    } else if (filtrées.isEmpty() && fiches.isEmpty()) {
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
                                icone = Icons.Rounded.SearchOff,
                            )
                        }
                    } else {
                        // Groupement par matière, ordre du serveur préservé.
                        // Les fiches de la Bibliothèque passent d'abord dans
                        // chaque matière (issue #43) : ce sont les documents
                        // frais des enseignants, les exercices suivent.
                        val groupesFiches = fiches.groupBy { it.matiere.ifBlank { "Général" } }
                        val groupesRessources = filtrées.groupBy { it.matiere.ifBlank { "Général" } }
                        val matières = groupesFiches.keys.toList() +
                            groupesRessources.keys.filter { it !in groupesFiches }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            matières.forEach { matiere ->
                                item(key = "section-$matiere") {
                                    SectionLabel(matiere)
                                }
                                groupesFiches[matiere].orEmpty().forEach { fiche ->
                                    item(key = "fiche-$matiere-${fiche.id}") {
                                        LigneFiche(
                                            fiche,
                                            enCours = état.téléchargementsFiche[fiche.id] == true,
                                            onTélécharger = { résultat ->
                                                vm.téléchargerFiche(fiche, context) { fichier ->
                                                    if (fichier != null) {
                                                        Fichiers.intentionOuvrir(context, fichier)?.let {
                                                            context.startActivity(it)
                                                        }
                                                        résultat(true)
                                                    } else {
                                                        résultat(false)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                                val items = groupesRessources[matiere].orEmpty()
                                items(
                                    items.size,
                                    key = { i -> "$matiere-${items[i].id}-$i" },
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
    val accent = RegistreTheme.accent
    Surface(
        shape = ControlShape,
        color = if (sélectionné) accent.conteneur else RegistreTheme.colors.page,
        border = if (sélectionné) null else BorderStroke(1.dp, RegistreTheme.colors.sage),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (sélectionné) accent.surConteneur else RegistreTheme.colors.chalk,
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
            // Badge du type sur l'accent de l'onglet — icône quiz, monogramme sinon.
            Surface(
                shape = AnnotationShape,
                color = RegistreTheme.accent.conteneur,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (quiz) {
                        Icon(
                            imageVector = Icons.Rounded.Quiz,
                            contentDescription = null,
                            tint = RegistreTheme.accent.surConteneur,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Text(
                            text = ressource.type?.take(1)?.uppercase()?.ifEmpty { "•" } ?: "•",
                            style = MaterialTheme.typography.titleMedium,
                            color = RegistreTheme.accent.surConteneur,
                        )
                    }
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

/** Une fiche de la Bibliothèque (issue #43) : document mis en ligne par un
 *  enseignant, daté et signé. Le bouton passe par le détail
 *  (`ressource_details`) qui porte l'URL média signée, puis télécharge et
 *  ouvre dans le lecteur du système. */
@Composable
private fun LigneFiche(
    fiche: FicheBibliotheque,
    enCours: Boolean,
    onTélécharger: (résultat: (Boolean) -> Unit) -> Unit,
) {
    var échec by remember(fiche.id) { mutableStateOf(false) }
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Badge du type sur l'accent de l'onglet — monogramme de la
                // matière, même gabarit que les ressources.
                Surface(
                    shape = AnnotationShape,
                    color = RegistreTheme.accent.conteneur,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = fiche.matiere.take(1).uppercase().ifEmpty { "•" },
                            style = MaterialTheme.typography.titleMedium,
                            color = RegistreTheme.accent.surConteneur,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = fiche.titre,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RegistreTheme.colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sousTitre = listOfNotNull(
                        fiche.date,
                        fiche.par?.takeIf { it.isNotBlank() }?.let { "Par $it" },
                    ).joinToString(" · ")
                    if (sousTitre.isNotBlank()) {
                        Text(
                            text = sousTitre,
                            style = MaterialTheme.typography.bodySmall,
                            color = RegistreTheme.colors.chalk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Puce("Bibliothèque")
                if (enCours) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = RegistreTheme.colors.chalk,
                    )
                } else {
                    IconButton(onClick = {
                        échec = false
                        onTélécharger { réussi -> if (!réussi) échec = true }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Télécharger",
                            tint = RegistreTheme.colors.chalk,
                        )
                    }
                }
            }
            if (échec) {
                ErrorInline(
                    message = "Fichier indisponible pour le moment",
                    modifier = Modifier.padding(start = 72.dp, end = 12.dp, bottom = 8.dp),
                )
            }
        }
    }
}
