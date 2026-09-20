package school.greenwood.plus.ui.screens.devoirs

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.ui.DevoirsViewModel
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsBouton
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.PuceChoix
import school.greenwood.plus.ui.components.SqueletteDevoirs
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.Fichiers
import school.greenwood.plus.util.frenchShort

/*
 * L'onglet Devoirs (docs/product/DESIGN.md §4) : liste par jour, sélection côté client sur
 * `date_remise` — le paramètre `date` du serveur est ignoré. Une pièce jointe
 * se télécharge dans l'espace privé de l'app puis s'ouvre en natif.
 */

@Composable
fun DevoirsScreen(
    container: AppContainer,
    padding: PaddingValues,
) {
    val vm: DevoirsViewModel = viewModel { DevoirsViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val aujourdhui = LocalDate.now()

    // La liste défile sous la barre flottante (Shell y a ajouté sa hauteur).
    val bas = padding.calculateBottomPadding()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.padding(
                top = padding.calculateTopPadding() + 12.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 12.dp,
            ),
        ) {
            Text(
                text = "Devoirs",
                style = MaterialTheme.typography.displayLarge,
                color = RegistreTheme.colors.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Par jour de rentrée",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
            )
        }

        // Sélecteur de jour : les échéances présentes, plus aujourd'hui et demain.
        val jours = remember(état.tous, aujourdhui) {
            (état.tous.mapNotNull { it.dateRemise } + aujourdhui + aujourdhui.plusDays(1))
                .distinct()
                .sorted()
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(jours) { jour ->
                PuceChoix(
                    label = if (jour == LocalDate.now()) "Aujourd'hui" else jour.frenchShort(),
                    sélectionné = jour == état.jourChoisi,
                    onClick = { vm.choisirJour(jour) },
                )
            }
        }

        when {
            état.chargement -> Box(Modifier.fillMaxSize().padding(bottom = bas)) {
                SqueletteDevoirs()
            }
            état.erreur != null && état.tous.isEmpty() -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = bas),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorInline(message = état.erreur ?: "")
                GwsBouton(
                    texte = "Réessayer",
                    onClick = { vm.charger(force = true) },
                )
            }
            else -> {
                // Bandeau discret au-dessus de la liste quand un échec réseau
                // laisse le contenu connu affiché (issue #21).
                val duJour = état.tous.filter { it.dateRemise == état.jourChoisi }
                Column(Modifier.fillMaxSize()) {
                    état.erreur?.let { message ->
                        BandeauErreur(
                            message = message,
                            réessayer = { vm.charger(force = true) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (duJour.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                titre = "Rien ce jour-là",
                                message = "Aucun devoir pour cette date.",
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 12.dp + bas,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(duJour, key = { it.id }) { devoir ->
                                CarteDevoir(
                                    devoir = devoir,
                                    aujourdhui = aujourdhui,
                                    onTélécharger = { url, nom, onFait ->
                                        vm.téléchargerPièceJointe(devoir, url, nom, context, onFait)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteDevoir(
    devoir: Devoir,
    aujourdhui: LocalDate,
    onTélécharger: (url: String, nom: String, onFait: (java.io.File?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val téléchargements = remember { mutableStateMapOf<String, Boolean>() }
    // Replié quand il y a un texte à dérouler ; ouvert sinon.
    val ouverts = remember(devoir.id) { mutableStateMapOf<String, Boolean>() }
    val déroulé = ouverts[devoir.id] ?: devoir.description.isNullOrBlank()
    val aDuContenu = !devoir.description.isNullOrBlank() || devoir.attachments.isNotEmpty()

    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = aDuContenu) {
                ouverts[devoir.id] = !déroulé
            }
            .animateContentSize(animationSpec = spring()),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (devoir.matiere.isNotBlank()) Puce(devoir.matiere)
                devoir.categorie?.takeIf { it.isNotBlank() }?.let { Puce(it) }
            }
            Text(
                text = devoir.title,
                style = MaterialTheme.typography.titleMedium,
                color = RegistreTheme.colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            devoir.enseignant?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
            // Le rouge ne marque que l'action requise (docs/product/DESIGN.md §2).
            when {
                devoir.fait -> Puce("Travail fait")
                devoir.dateRemise != null && !devoir.dateRemise.isAfter(aujourdhui) ->
                    Puce("À faire", tintRed = true)
            }

            if (déroulé) {
                devoir.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RegistreTheme.colors.ink,
                    )
                }
                devoir.attachments.forEach { pièce ->
                    LignePièceJointe(
                        pièce = pièce,
                        enCours = téléchargements[pièce.url] == true,
                        onTélécharger = {
                            téléchargements[pièce.url] = true
                            onTélécharger(pièce.url, pièce.name) { fichier ->
                                téléchargements[pièce.url] = false
                                if (fichier != null) {
                                    val intention = Fichiers.intentionOuvrir(context, fichier)
                                    if (intention != null) context.startActivity(intention)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LignePièceJointe(
    pièce: Attachment,
    enCours: Boolean,
    onTélécharger: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Attachment,
            contentDescription = null,
            tint = RegistreTheme.colors.chalk,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = pièce.name,
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (enCours) {
            CircularProgressIndicator(
                color = RegistreTheme.colors.ink,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            IconButton(onClick = onTélécharger, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Télécharger ${pièce.name}",
                    tint = RegistreTheme.colors.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
