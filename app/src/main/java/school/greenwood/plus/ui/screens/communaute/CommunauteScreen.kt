package school.greenwood.plus.ui.screens.communaute

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.CibleSignalement
import school.greenwood.plus.model.CorrectionHoraire
import school.greenwood.plus.model.DevoirSuggéré
import school.greenwood.plus.model.FiltreHoraire
import school.greenwood.plus.model.ProblèmeHoraire
import school.greenwood.plus.model.SignalementAbus
import school.greenwood.plus.model.TriDevoirs
import school.greenwood.plus.ui.CiblePage
import school.greenwood.plus.ui.CommunauteViewModel
import school.greenwood.plus.ui.CommunauteÉtat
import school.greenwood.plus.ui.DialogueCommunautaire
import school.greenwood.plus.ui.OngletCommunautaire
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.components.SqueletteCommunaute
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.frenchShort
import java.time.Instant
import java.time.ZoneId

/*
 * La section communautaire (issue #88 — APP.md du serveur gws-community-server).
 * Trois onglets : suggestions de devoirs entre familles, emploi du temps
 * (problèmes + corrections) et signalements d'abus. Les listes sont publiques
 * et anonymes côté serveur (empreinte `auteurId`, jamais le jeton) ; les
 * écritures passent par le cycle de vie du compte — notice, 401, 429 —
 * géré par le ViewModel.
 */

@Composable
fun CommunauteScreen(
    container: AppContainer,
    padding: PaddingValues,
    retour: () -> Unit,
    ouvrirCréationDevoir: Boolean = false,
) {
    val vm: CommunauteViewModel = viewModel { CommunauteViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()

    LaunchedEffect(ouvrirCréationDevoir) {
        if (ouvrirCréationDevoir) vm.ouvrirCréationDevoir()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        // En-tête identique aux sous-écrans : retour, titre, surligneur.
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = retour) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Retour",
                    tint = RegistreTheme.colors.ink,
                )
            }
            Text(
                text = "Communauté",
                style = MaterialTheme.typography.displayLarge,
                color = RegistreTheme.colors.ink,
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(width = 56.dp, height = 5.dp)
                    .clip(AnnotationShape)
                    .background(RegistreTheme.accent.conteneur),
            )
        }

        OngletsCommunautaires(choisi = état.onglet, choisir = vm::choisirOnglet)
        LigneActions(onglet = état.onglet, vm = vm)

        when {
            état.chargement -> SqueletteCommunaute()
            vide(état) && état.erreur != null -> Column(
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
            vide(état) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ÉtatVide(état.onglet)
            }
            else -> ListeCommunautaire(état = état, vm = vm)
        }
    }

    // Les dialogues : notice, créations, signalement, suppression.
    if (état.noticeChargement && état.dialogue == null) {
        DialogueNoticeEnChargement()
    }
    état.dialogue?.let { dialogue ->
        VueDialogue(dialogue = dialogue, vm = vm, envoi = état.envoi)
    }
}

private fun vide(état: CommunauteÉtat): Boolean = when (état.onglet) {
    OngletCommunautaire.Devoirs -> état.devoirs.isEmpty()
    OngletCommunautaire.EmploiDuTemps -> état.problèmes.isEmpty() && état.corrections.isEmpty()
    OngletCommunautaire.Abus -> état.abus.isEmpty()
}

// — En-tête : onglets et actions -------------------------------------------

@Composable
private fun OngletsCommunautaires(
    choisi: OngletCommunautaire,
    choisir: (OngletCommunautaire) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OngletCommunautaire.entries.forEach { onglet ->
            val actif = onglet == choisi
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(ControlShape)
                    .background(if (actif) RegistreTheme.accent.conteneur else RegistreTheme.colors.sage)
                    .clickable { choisir(onglet) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = onglet.libellé,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight(600),
                    color = if (actif) RegistreTheme.accent.surConteneur else RegistreTheme.colors.ink,
                )
            }
        }
    }
}

/** Les actions d'écriture de l'onglet — toujours visibles, même vide. */
@Composable
private fun LigneActions(onglet: OngletCommunautaire, vm: CommunauteViewModel) {
    if (onglet == OngletCommunautaire.Abus) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (onglet) {
            OngletCommunautaire.Devoirs -> BoutonAction(
                libellé = "Proposer un devoir",
                icone = Icons.Rounded.Add,
                onClick = vm::ouvrirCréationDevoir,
                modifier = Modifier.weight(1f),
            )
            OngletCommunautaire.EmploiDuTemps -> {
                BoutonAction(
                    libellé = "Signaler un problème",
                    icone = Icons.Rounded.Flag,
                    onClick = vm::ouvrirCréationProblème,
                    modifier = Modifier.weight(1f),
                )
                BoutonAction(
                    libellé = "Proposer une correction",
                    icone = Icons.Rounded.Add,
                    onClick = { vm.ouvrirCréationCorrection() },
                    modifier = Modifier.weight(1f),
                )
            }
            OngletCommunautaire.Abus -> Unit
        }
    }
}

@Composable
private fun BoutonAction(
    libellé: String,
    icone: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = RegistreTheme.colors.ink,
            contentColor = RegistreTheme.colors.page,
        ),
        shape = ControlShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(libellé, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// — La liste ---------------------------------------------------------------

@Composable
private fun ListeCommunautaire(état: CommunauteÉtat, vm: CommunauteViewModel) {
    Column(Modifier.fillMaxSize()) {
        // Bandeaux : échec d'écriture, confirmation, échec de lecture (issue #21).
        état.échec?.let { Avis(message = it, surAcquitter = vm::acquitter, erreur = true) }
        état.message?.let { Avis(message = it, surAcquitter = vm::acquitter, erreur = false) }
        état.erreur?.let { message ->
            BandeauErreur(
                message = message,
                réessayer = { vm.charger(force = true) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "filtres") { LigneFiltres(état = état, vm = vm) }

            when (état.onglet) {
                OngletCommunautaire.Devoirs -> {
                    items(état.devoirs, key = { "devoir-${it.id}" }) { devoir ->
                        CarteDevoir(
                            devoir = devoir,
                            voteLocal = état.votes[devoir.id.toString()] ?: 0,
                            monAuteurId = état.monAuteurId,
                            signalé = état.signalés.contains("devoir:${devoir.id}"),
                            vm = vm,
                        )
                    }
                    item(key = "pied-devoirs") {
                        PiedListe(
                            total = état.totalDevoirs,
                            affichés = état.devoirs.size,
                            libellé = "suggestion(s) de devoir",
                            enCours = état.chargementSuite,
                            plusDisponible = état.totalDevoirs.let { it != null && état.devoirs.size < it },
                            voirPlus = { vm.chargerSuite(CiblePage.Devoirs) },
                        )
                    }
                }
                OngletCommunautaire.EmploiDuTemps -> {
                    item(key = "titre-problemes") {
                        SectionLabel(
                            text = "Problèmes signalés" + (état.totalProblèmes?.let { " · $it" } ?: ""),
                            pointAccent = true,
                        )
                    }
                    items(état.problèmes, key = { "probleme-${it.id}" }) { problème ->
                        CarteProblème(problème = problème, monAuteurId = état.monAuteurId, signalé = état.signalés.contains("probleme:${problème.id}"), vm = vm)
                    }
                    if (état.totalProblèmes.let { it != null && état.problèmes.size < it }) {
                        item(key = "suite-problemes") {
                            BoutonVoirPlus(enCours = état.chargementSuite) { vm.chargerSuite(CiblePage.Problèmes) }
                        }
                    }
                    item(key = "titre-corrections") {
                        SectionLabel(
                            text = "Corrections proposées" + (état.totalCorrections?.let { " · $it" } ?: ""),
                            pointAccent = true,
                        )
                    }
                    items(état.corrections, key = { "correction-${it.id}" }) { correction ->
                        CarteCorrection(correction = correction, monAuteurId = état.monAuteurId, signalé = état.signalés.contains("correction:${correction.id}"), vm = vm)
                    }
                    if (état.totalCorrections.let { it != null && état.corrections.size < it }) {
                        item(key = "suite-corrections") {
                            BoutonVoirPlus(enCours = état.chargementSuite) { vm.chargerSuite(CiblePage.Corrections) }
                        }
                    }
                }
                OngletCommunautaire.Abus -> {
                    items(état.abus, key = { "abus-${it.id}" }) { signalement ->
                        CarteAbus(signalement)
                    }
                    item(key = "pied-abus") {
                        Text(
                            text = "${état.abus.size} signalement(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = RegistreTheme.colors.chalk,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }

            item(key = "note") {
                Text(
                    text = "Contenu partagé entre familles, pas par l'école — signale " +
                        "ce qui cloche depuis le menu de chaque carte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** Tri (devoirs) ou filtre d'état (emploi du temps) — les choix pilotent le
 *  prochain rafraîchissement complet. */
@Composable
private fun LigneFiltres(état: CommunauteÉtat, vm: CommunauteViewModel) {
    when (état.onglet) {
        OngletCommunautaire.Devoirs -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriDevoirs.entries.forEach { tri ->
                FiltrePuce(
                    libellé = tri.libellé,
                    choisi = tri == état.tri,
                    onChoisir = { vm.choisirTri(tri) },
                )
            }
        }
        OngletCommunautaire.EmploiDuTemps -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FiltreHoraire.entries.forEach { filtre ->
                FiltrePuce(
                    libellé = filtre.libellé,
                    choisi = filtre == état.filtre,
                    onChoisir = { vm.choisirFiltre(filtre) },
                )
            }
        }
        OngletCommunautaire.Abus -> Unit
    }
}

@Composable
private fun FiltrePuce(libellé: String, choisi: Boolean, onChoisir: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(AnnotationShape)
            .background(if (choisi) RegistreTheme.accent.conteneur else RegistreTheme.colors.sage)
            .clickable(onClick = onChoisir)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = libellé,
            style = MaterialTheme.typography.labelMedium,
            color = if (choisi) RegistreTheme.accent.surConteneur else RegistreTheme.colors.ink,
        )
    }
}

// — Cartes -----------------------------------------------------------------

@Composable
private fun CarteDevoir(
    devoir: DevoirSuggéré,
    voteLocal: Int,
    monAuteurId: String?,
    signalé: Boolean,
    vm: CommunauteViewModel,
) {
    val mien = monAuteurId != null && devoir.auteurId == monAuteurId && monAuteurId.isNotBlank()
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (devoir.matière.isNotBlank()) {
                    Puce(devoir.matière, accent = RegistreTheme.accent)
                }
                devoir.dateRemise?.let {
                    Text(
                        text = "Remise le ${it.frenchShort()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                if (mien) {
                    Puce("Mien")
                }
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = devoir.contenu,
                style = MaterialTheme.typography.bodyMedium,
                color = RegistreTheme.colors.ink,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (mien) {
                    // Sur son propre contenu : pas de vote (le serveur refuse 403),
                    // juste le total.
                    Text(
                        text = "${devoir.votes} vote(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                } else {
                    IconButton(onClick = { vm.voter(devoir.id, 1) }) {
                        Icon(
                            imageVector = Icons.Rounded.ThumbUp,
                            contentDescription = "Voter pour",
                            tint = if (voteLocal == 1) RegistreTheme.accent.teinte else RegistreTheme.colors.chalk,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = "${devoir.votes}",
                        style = MaterialTheme.typography.labelMedium,
                        color = RegistreTheme.colors.ink,
                    )
                    IconButton(onClick = { vm.voter(devoir.id, -1) }) {
                        Icon(
                            imageVector = Icons.Rounded.ThumbDown,
                            contentDescription = "Voter contre",
                            tint = if (voteLocal == -1) RegistreTheme.accent.teinte else RegistreTheme.colors.chalk,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                horodatage(devoir.crééÀ)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                MenuCarte(
                    mien = mien,
                    signalé = signalé,
                    intitulé = devoir.contenu.take(60),
                    onSignaler = { vm.ouvrirSignalement(CibleSignalement.Devoir, devoir.id) },
                    onSupprimer = { vm.ouvrirSuppression(CibleSignalement.Devoir, devoir.id, devoir.contenu.take(60)) },
                    onCorrection = null,
                )
            }
        }
    }
}

@Composable
private fun CarteProblème(
    problème: ProblèmeHoraire,
    monAuteurId: String?,
    signalé: Boolean,
    vm: CommunauteViewModel,
) {
    val mien = monAuteurId != null && problème.auteurId == monAuteurId && monAuteurId.isNotBlank()
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                problème.date?.let {
                    Text(
                        text = it.frenchShort(),
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                val résolu = problème.état.equals("résolu", ignoreCase = true)
                Puce(
                    label = if (résolu) "Résolu" else "Ouvert",
                    tintRed = !résolu,
                    accent = if (résolu) RegistreTheme.accent else null,
                )
                if (mien) Puce("Mien")
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = problème.description,
                style = MaterialTheme.typography.bodyMedium,
                color = RegistreTheme.colors.ink,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                horodatage(problème.crééÀ)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                Spacer(Modifier.weight(1f))
                MenuCarte(
                    mien = mien,
                    signalé = signalé,
                    intitulé = problème.description.take(60),
                    onSignaler = { vm.ouvrirSignalement(CibleSignalement.Problème, problème.id) },
                    onSupprimer = { vm.ouvrirSuppression(CibleSignalement.Problème, problème.id, problème.description.take(60)) },
                    onCorrection = {
                        vm.ouvrirCréationCorrection(
                            problèmeId = problème.id,
                            date = problème.date?.toString() ?: "",
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CarteCorrection(
    correction: CorrectionHoraire,
    monAuteurId: String?,
    signalé: Boolean,
    vm: CommunauteViewModel,
) {
    val mien = monAuteurId != null && correction.auteurId == monAuteurId && monAuteurId.isNotBlank()
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                correction.date?.let {
                    Text(
                        text = it.frenchShort(),
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                correction.problèmeId?.let {
                    Puce("Rattachée à #$it")
                }
                if (mien) Puce("Mien")
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = correction.description,
                style = MaterialTheme.typography.bodyMedium,
                color = RegistreTheme.colors.ink,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                horodatage(correction.crééÀ)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                Spacer(Modifier.weight(1f))
                MenuCarte(
                    mien = mien,
                    signalé = signalé,
                    intitulé = correction.description.take(60),
                    onSignaler = { vm.ouvrirSignalement(CibleSignalement.Correction, correction.id) },
                    onSupprimer = { vm.ouvrirSuppression(CibleSignalement.Correction, correction.id, correction.description.take(60)) },
                    onCorrection = null,
                )
            }
        }
    }
}

@Composable
private fun CarteAbus(signalement: SignalementAbus) {
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Puce(cibleLibellé(signalement.cible), tintRed = true)
                horodatage(signalement.crééÀ)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = signalement.raison,
                style = MaterialTheme.typography.bodyMedium,
                color = RegistreTheme.colors.ink,
            )
            Text(
                text = "Contenu n° ${signalement.cibleId} — retiré après vérification de la modération.",
                style = MaterialTheme.typography.labelSmall,
                color = RegistreTheme.colors.chalk,
            )
        }
    }
}

private fun cibleLibellé(cible: String): String = when (cible) {
    "devoir" -> "Devoir"
    "probleme" -> "Emploi du temps"
    "correction" -> "Correction"
    else -> "Signalement"
}

/** Menu ⋮ d'une carte : signaler (si pas déjà fait et pas le sien), proposer
 *  une correction (problèmes), supprimer (contenu propre). */
@Composable
private fun MenuCarte(
    mien: Boolean,
    signalé: Boolean,
    intitulé: String,
    onSignaler: () -> Unit,
    onSupprimer: () -> Unit,
    onCorrection: (() -> Unit)?,
) {
    var ouvert by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { ouvert = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = RegistreTheme.colors.chalk,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            onCorrection?.let {
                DropdownMenuItem(
                    text = { Text("Proposer une correction") },
                    onClick = {
                        ouvert = false
                        it()
                    },
                )
            }
            if (!mien && !signalé) {
                DropdownMenuItem(
                    text = { Text("Signaler") },
                    onClick = {
                        ouvert = false
                        onSignaler()
                    },
                )
            }
            if (mien) {
                DropdownMenuItem(
                    text = { Text("Supprimer") },
                    onClick = {
                        ouvert = false
                        onSupprimer()
                    },
                )
            }
        }
    }
}

// — Pieds de liste, bandeaux, états vides ----------------------------------

@Composable
private fun PiedListe(
    total: Int?,
    affichés: Int,
    libellé: String,
    enCours: Boolean,
    plusDisponible: Boolean,
    voirPlus: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = (total ?: affichés).toString() + " " + libellé,
            style = MaterialTheme.typography.labelMedium,
            color = RegistreTheme.colors.chalk,
        )
        if (plusDisponible) {
            BoutonVoirPlus(enCours = enCours, onClick = voirPlus)
        }
    }
}

@Composable
private fun BoutonVoirPlus(enCours: Boolean, onClick: () -> Unit) {
    if (enCours) {
        Text(
            text = "Chargement…",
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.chalk,
        )
    } else {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = RegistreTheme.colors.ink,
                contentColor = RegistreTheme.colors.page,
            ),
            shape = ControlShape,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("Voir plus", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Bandeau d'information passagère (confirmation ou échec d'écriture) — un
 *  geste dessus l'acquitte, sans faux bouton « Réessayer ». */
@Composable
private fun Avis(message: String, surAcquitter: () -> Unit, erreur: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(ControlShape)
            .background(
                if (erreur) MaterialTheme.colorScheme.errorContainer
                else RegistreTheme.accent.conteneur,
            )
            .clickable(onClick = surAcquitter)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (erreur) MaterialTheme.colorScheme.onErrorContainer
            else RegistreTheme.accent.surConteneur,
        )
    }
}

@Composable
private fun ÉtatVide(onglet: OngletCommunautaire) {
    when (onglet) {
        OngletCommunautaire.Devoirs -> EmptyState(
            titre = "Aucune suggestion",
            message = "Propose le premier devoir aux autres familles.",
            icone = Icons.AutoMirrored.Rounded.Assignment,
        )
        OngletCommunautaire.EmploiDuTemps -> EmptyState(
            titre = "Rien de signalé",
            message = "Personne n'a encore de problème avec l'emploi du temps.",
            icone = Icons.Rounded.Event,
        )
        OngletCommunautaire.Abus -> EmptyState(
            titre = "Aucun signalement",
            message = "Rien à modérer pour l'instant.",
            icone = Icons.Rounded.Flag,
        )
    }
}

/** « le 22/09/2026 à 14:30 » — null si le serveur n'a pas horodaté. */
private fun horodatage(millis: Long): String? =
    if (millis <= 0) null
    else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().frenchFull()
