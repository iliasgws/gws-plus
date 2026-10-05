package school.greenwood.plus.ui.screens.devoirs

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import school.greenwood.plus.AppContainer
import school.greenwood.plus.R
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.DevoirSuggéré
import school.greenwood.plus.ui.DevoirsViewModel
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.EcranBanniere
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SqueletteDevoirs
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.frenchLongDay
import school.greenwood.plus.util.htmlToPlainSingleLine

/*
 * Issue #78 : le point de suivi des devoirs par jour. Vert (accent « registre »)
 * quand tout le travail du jour est marqué fait, rouge stylo dès qu'un devoir
 * reste à faire — pas d'état intermédiaire. Rien si le jour n'a pas de devoirs.
 * Issue #82 : orange signet quand le jour n'est fait que « pour soi » — le
 * marquage local couvre tout, mais l'école ne le sait pas encore.
 */

/** Suivi d'un jour : [Vert] = jour passé ou tout est fait aussi pour
 *  l'école, [Orange] = fait pour soi seulement, [Rouge] = au moins un
 *  reste. */
internal enum class ÉtatJour { Vert, Orange, Rouge, Communauté }

/*
 * L'onglet Devoirs (docs/product/DESIGN.md §4) : liste par jour, sélection côté client sur
 * `date_remise` — le paramètre `date` du serveur est ignoré. Une pièce jointe
 * se télécharge dans l'espace privé de l'app puis s'ouvre en natif.
 */

@Composable
fun DevoirsScreen(
    container: AppContainer,
    padding: PaddingValues,
    onOuvrirDevoir: (String) -> Unit = {},
    onProposerDevoirManquant: () -> Unit = {},
) {
    val vm: DevoirsViewModel = viewModel { DevoirsViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()
    val aujourdhui = LocalDate.now()
    val bannièreActivée by container.session.bannièreDevoirsActivée.collectAsStateWithLifecycle(initialValue = true)
    val liste = rememberLazyListState()

    val étatsJours = remember(état.tous, état.propositionsCommunautaires, aujourdhui) {
        val officiels = état.tous.filter { it.dateRemise != null }.groupBy { it.dateRemise!! }
        val propositions = état.propositionsCommunautaires.groupBy { it.dateRemise ?: aujourdhui }
        (officiels.keys + propositions.keys).associateWith { jour ->
            val duJour = officiels[jour].orEmpty()
            when {
                duJour.isEmpty() -> ÉtatJour.Communauté
                jour.isBefore(aujourdhui) || duJour.all { it.fait } -> ÉtatJour.Vert
                duJour.all { it.fait || it.faitLocal } -> ÉtatJour.Orange
                else -> ÉtatJour.Rouge
            }
        }
    }
    val duJour = état.tous.filter { it.dateRemise == état.jourChoisi }
    val propositions = état.propositionsCommunautaires.filter {
        it.dateRemise == état.jourChoisi || (it.dateRemise == null && état.jourChoisi == aujourdhui)
    }

    EcranBanniere(R.drawable.devoirs_banner, bannièreActivée, padding, liste) {
        item(key = "titre") {
            Column {
                Text(
                    text = "Devoirs",
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
                    text = "Le calendrier des devoirs",
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
        }
        item(key = "calendrier") {
            CalendrierDevoirs(
                jourChoisi = état.jourChoisi,
                aujourdhui = aujourdhui,
                étatsJours = étatsJours,
                onChoisirJour = vm::choisirJour,
            )
        }
        item(key = "proposer") {
            Button(
                onClick = onProposerDevoirManquant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RegistreTheme.colors.ink,
                    contentColor = RegistreTheme.colors.page,
                ),
                shape = ControlShape,
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Proposer un devoir manquant")
            }
        }
        item(key = "jour") {
            Column {
                Text(
                    état.jourChoisi.frenchLongDay(),
                    style = MaterialTheme.typography.titleLarge,
                    color = RegistreTheme.colors.ink,
                )
                if (!état.chargement) Text(
                    "${duJour.size} devoir(s) · ${propositions.size} proposition(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
        }
        when {
            état.chargement -> item(key = "chargement") { SqueletteDevoirs(défilable = false) }
            état.erreur != null && état.tous.isEmpty() && état.propositionsCommunautaires.isEmpty() -> item {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
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
            }
            else -> {
                // Bandeau discret au-dessus de la liste quand un échec réseau
                // laisse le contenu connu affiché (issue #21).
                état.erreur?.let { message ->
                    item(key = "erreur") {
                        BandeauErreur(
                            message = message,
                            réessayer = { vm.charger(force = true) },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                if (duJour.isEmpty() && propositions.isEmpty()) {
                    item(key = "vide") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                titre = "Rien ce jour-là",
                                message = "Aucun devoir pour cette date.",
                            )
                        }
                    }
                } else {
                    if (propositions.isNotEmpty()) {
                        item(key = "familles") {
                            Text(
                                "Propositions des familles",
                                style = MaterialTheme.typography.titleMedium,
                                color = RegistreTheme.colors.ink,
                            )
                        }
                        items(propositions, key = { "communaute-${it.id}" }) { proposition ->
                            CarteProposition(proposition)
                        }
                    }
                    items(duJour, key = { "devoir-${it.id}" }) { devoir ->
                            CarteDevoir(
                                devoir = devoir,
                                aujourdhui = aujourdhui,
                                onOuvrir = { onOuvrirDevoir(devoir.id) },
                                onBasculerFaitLocal = { vm.basculerFaitLocal(devoir) },
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteProposition(devoir: DevoirSuggéré) {
    val uriHandler = LocalUriHandler.current
    GwsCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(devoir.matière, style = MaterialTheme.typography.titleSmall, color = RegistreTheme.colors.ink)
                Puce("Communauté")
            }
            Text(devoir.contenu, style = MaterialTheme.typography.bodyMedium, color = RegistreTheme.colors.ink)
            devoir.piècesJointes.forEach { pièce ->
                Text(
                    text = "📎 ${pièce.nom}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.accents.getValue("registre").teinte,
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri(pièce.url) } },
                )
            }
        }
    }
}

@Composable
private fun CarteDevoir(
    devoir: Devoir,
    aujourdhui: LocalDate,
    onOuvrir: () -> Unit,
    onBasculerFaitLocal: () -> Unit,
) {
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOuvrir),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (devoir.matiere.isNotBlank()) Puce(devoir.matiere)
                devoir.categorie?.takeIf { it.isNotBlank() }?.let { Puce(it) }
                Spacer(Modifier.weight(1f))
                // Bascule « fait pour moi » (issue #82) : local, réversible
                // d'un clic, invisible pour l'école. Orange quand le travail
                // est fait pour soi mais pas encore pour l'école ; vert
                // Greenwood quand l'école le sait aussi.
                IconButton(
                    onClick = onBasculerFaitLocal,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (devoir.faitLocal) Icons.Rounded.CheckCircle
                        else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (devoir.faitLocal)
                            "Retirer le marquage « fait pour moi »"
                        else "Marquer fait pour moi (local, invisible à l'école)",
                        tint = when {
                            devoir.faitLocal && devoir.fait ->
                                RegistreTheme.colors.accents.getValue("registre").teinte
                            devoir.faitLocal -> RegistreTheme.colors.signetVif
                            else -> RegistreTheme.colors.chalk
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
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
            // Le marquage local « fait pour moi » (issue #82) éteint le rouge :
            // le travail est fait au regard de l'utilisateur, sans toucher au
            // suivi officiel. Un devoir passé n'est plus « à faire » — l'échéance
            // est derrière, comme le point du jour qui devient vert.
            when {
                devoir.fait -> Puce("Travail fait")
                devoir.faitLocal -> Puce("Fait pour moi")
                devoir.dateRemise != null && !devoir.dateRemise.isBefore(aujourdhui) ->
                    Puce("À faire", tintRed = true)
            }

            // Aperçu d'une ligne du corps (HTML aplati) — le détail complet
            // s'ouvre dans le panneau dédié (issue #60).
            devoir.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it.htmlToPlainSingleLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (devoir.attachments.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Attachment,
                        contentDescription = null,
                        tint = RegistreTheme.colors.chalk,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${devoir.attachments.size} pièce(s) jointe(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
        }
    }
}
