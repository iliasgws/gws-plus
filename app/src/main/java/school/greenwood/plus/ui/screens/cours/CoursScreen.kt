package school.greenwood.plus.ui.screens.cours

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.Créneau
import school.greenwood.plus.model.JournéeCours
import school.greenwood.plus.ui.CoursViewModel
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.components.SqueletteCours
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.frenchLongDay
import school.greenwood.plus.util.frenchShort
import school.greenwood.plus.util.htmlToPlainMultiline

/*
 * L'onglet Emploi du temps (GET `cours_v2`, ENDPOINT-MAP 2026-09-20) :
 * navigation entre semaines — le paramètre `date` est NON vérifié, la semaine
 * affichée reste celle que le serveur renvoie —, résumé de la semaine en puces
 * de jours, puis les créneaux du jour choisi. La forme des créneaux est
 * inconnue du sondage (`seances[]` vide) : parsing défensif côté dépôt,
 * affichage tolérant ici.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursScreen(
    container: AppContainer,
    padding: PaddingValues,
) {
    val vm: CoursViewModel = viewModel { CoursViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()

    // « 14 sept. – 20 sept. » — dérivé du lundi connu, jamais du libellé serveur.
    val plageSemaine = état.semaine?.lundi?.let { lundi ->
        "${lundi.frenchShort()} – ${lundi.plusDays(6).frenchShort()}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Emploi du temps",
                style = MaterialTheme.typography.displayLarge,
                color = RegistreTheme.colors.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "La semaine de l'école, jour par jour",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
            )
        }

        // Navigation ←/→ entre semaines
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val précédentePossible = état.semaine?.semainePrécédente != null
            val suivantePossible = état.semaine?.semaineSuivante != null
            IconButton(
                onClick = vm::semainePrécédente,
                enabled = précédentePossible && !état.navigation,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Semaine précédente",
                    tint = if (précédentePossible) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (état.navigation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = RegistreTheme.colors.ink,
                    )
                } else {
                    Text(
                        text = plageSemaine ?: "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
            IconButton(
                onClick = vm::semaineSuivante,
                enabled = suivantePossible && !état.navigation,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Semaine suivante",
                    tint = if (suivantePossible) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                )
            }
        }

        état.erreur?.let { err ->
            BandeauErreur(
                message = err,
                réessayer = { vm.charger(force = true) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        PullToRefreshBox(
            isRefreshing = état.rafraîchissement,
            onRefresh = { vm.rafraîchir() },
            modifier = Modifier.fillMaxSize(),
        ) {
            val semaine = état.semaine
            when {
                état.chargement && semaine == null -> SqueletteCours()

                semaine == null -> EmptyState(
                    titre = "Pas encore d'emploi du temps",
                    message = "L'école n'a pas publié d'emploi du temps pour ce compte.",
                    modifier = Modifier.padding(top = 40.dp, start = 16.dp, end = 16.dp),
                )

                else -> {
                    val journée = semaine.journées.firstOrNull { it.jour == état.jourChoisi }
                        ?: semaine.journées.firstOrNull()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Le résumé de la semaine : une puce par jour, avec son nombre de cours.
                        item(key = "jours") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(semaine.journées, key = { it.jour }) { jour ->
                                    PuceJourCours(
                                        journée = jour,
                                        sélectionné = jour.jour == journée?.jour,
                                        onClick = { vm.choisirJour(jour.jour) },
                                    )
                                }
                            }
                        }

                        if (semaine.restreint) {
                            item(key = "restriction") {
                                CarteRestriction(message = semaine.messageRestriction?.htmlToPlainMultiline())
                            }
                        }

                        if (journée == null || journée.créneaux.isEmpty()) {
                            item(key = "vide-jour") {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    EmptyState(
                                        titre = "Aucun cours",
                                        message = semaine.aucunCours
                                            ?: "Rien de prévu ce jour-là — l'école n'a pas publié de créneau.",
                                    )
                                }
                            }
                        } else {
                            item(key = "jour-label") {
                                SectionLabel(
                                    text = journée.date?.frenchLongDay()
                                        ?: journée.label?.let { "Jour $it" }
                                        ?: "Jour",
                                )
                            }
                            itemsIndexed(journée.créneaux) { _, créneau ->
                                CarteCréneau(créneau = créneau)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Puce d'un jour de la semaine : lettre, date courte et nombre de cours. */
@Composable
private fun PuceJourCours(
    journée: JournéeCours,
    sélectionné: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = ControlShape,
        color = if (sélectionné) RegistreTheme.colors.sage else RegistreTheme.colors.page,
        border = if (sélectionné) null else BorderStroke(1.dp, RegistreTheme.colors.sage),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = journée.label ?: "${journée.jour}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight(600),
                color = if (sélectionné) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
            )
            journée.date?.let { date ->
                Text(
                    text = date.frenchShort(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sélectionné) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                )
            }
            if (journée.créneaux.isNotEmpty()) {
                Text(
                    text = "${journée.créneaux.size} cours",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sélectionné) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                )
            }
        }
    }
}

/** Un créneau : horaire à gauche, matière, salle et enseignant. */
@Composable
private fun CarteCréneau(
    créneau: Créneau,
    modifier: Modifier = Modifier,
) {
    GwsCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.width(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = créneau.début ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                )
                créneau.fin?.let { fin ->
                    Text(
                        text = fin,
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = créneau.matière ?: "Cours",
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                )
                créneau.salle?.let { salle ->
                    Text(
                        text = "Salle $salle",
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                créneau.enseignant?.let { enseignant ->
                    Text(
                        text = enseignant,
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
        }
    }
}

/** Avertissement du serveur quand l'emploi du temps est restreint. */
@Composable
private fun CarteRestriction(message: String?) {
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionLabel(text = "Accès restreint")
            Text(
                text = message ?: "L'accès à l'emploi du temps est restreint — contactez l'administration.",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.ink,
            )
        }
    }
}
