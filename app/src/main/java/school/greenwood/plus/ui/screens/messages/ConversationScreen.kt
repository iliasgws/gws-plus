package school.greenwood.plus.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Message
import school.greenwood.plus.model.MessageEnvoi
import school.greenwood.plus.ui.ConversationViewModel
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.Fichiers
import school.greenwood.plus.util.LecteurAudio
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.frenchLongDay
import school.greenwood.plus.util.frenchTime
import java.time.LocalDate
import java.util.Locale

/*
 * Fil de conversation avec l'administration (issue #10). Bulles — fond sage =
 * administration, page bordée sage = parent ; séparateurs de date, horodatages
 * et accusés de lecture (`vu_le`) sur les messages du parent. Pièces jointes
 * et messages vocaux joués en ligne.
 *
 * Composeur (seconde partie) — gated par le kill switch de session (désactivé
 * par défaut tant que l'envoi réel n'a pas été validé une fois). Envoi
 * optimiste : l'élément en attente s'affiche au bas du fil, remplacé par la
 * version serveur à la confirmation, marqué Échec (relançable) sinon.
 */

@Composable
fun ConversationScreen(
    container: AppContainer,
    padding: PaddingValues,
    conversationId: String,
    retour: () -> Unit,
) {
    val vm: ConversationViewModel = viewModel { ConversationViewModel(container, conversationId) }
    val état by vm.état.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            // Le clavier repousse le contenu (composeur visible au-dessus).
            .imePadding()
            .padding(padding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = retour) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Retour",
                    tint = RegistreTheme.colors.ink,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
            ) {
                Text(
                    text = état.conversation?.sujet ?: "…",
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Administration",
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
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
            état.conversation?.messages?.isEmpty() != false ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        titre = "Aucun message",
                        message = "Les échanges de ce fil apparaîtront ici.",
                    )
                }
            else -> {
                val conversation = état.conversation
                val messages = conversation?.messages ?: emptyList()
                val liste = rememberLazyListState()

                // Nombre d'éléments rendus (séparateurs + bulles + envois en
                // attente), pour l'ouverture en bas de fil comme une vraie
                // messagerie.
                val compteÉléments = remember(conversation, état.envois) {
                    var n = 0
                    var précédent: java.time.LocalDate? = null
                    messages.forEach { m ->
                        val jour = m.date?.toLocalDate()
                        if (jour != null && jour != précédent) n++
                        n++
                        précédent = m.date?.toLocalDate()
                    }
                    n + état.envois.size
                }
                LaunchedEffect(conversation?.id, compteÉléments) {
                    if (compteÉléments > 0) liste.scrollToItem(compteÉléments - 1)
                }

                LazyColumn(
                    state = liste,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    messages.forEachIndexed { index, message ->
                        val jour = message.date?.toLocalDate()
                        val précédent = messages.getOrNull(index - 1)?.date?.toLocalDate()
                        if (jour != null && jour != précédent) {
                            item(key = "sep-$index") { SéparateurDate(jour) }
                        }
                        item(key = "msg-$index") {
                            Bulle(
                                message = message,
                                onTélécharger = { pièce, onFait ->
                                    vm.téléchargerPièce(pièce, context, onFait)
                                },
                            )
                        }
                    }
                    état.envois.forEachIndexed { i, envoi ->
                        item(key = "envoi-$i-${envoi.texte.hashCode()}") {
                            BulleEnvoi(
                                envoi = envoi,
                                onRelancer = { vm.relancer(envoi) },
                            )
                        }
                    }
                }

                if (état.composeurActif) {
                    Composeur(
                        texte = état.texte,
                        onTexte = vm::modifierTexte,
                        pièces = état.pièces,
                        onAjouterPièces = vm::ajouterPièces,
                        onRetirerPièce = vm::retirerPièce,
                        audio = état.audio,
                        onRetirerAudio = vm::retirerAudio,
                        enregistre = état.enregistre,
                        onDémarrerEnregistrement = vm::démarrerEnregistrement,
                        // Arrêter = repasser en composeur avec le vocal prêt
                        // (puce « Message vocal prêt ») ; l'envoi reste un
                        // geste séparé, comme pour une pièce jointe.
                        onArrêterEnregistrement = vm::arrêterEnregistrement,
                        onAnnulerEnregistrement = vm::annulerEnregistrement,
                        envoiPossible = état.erreur == null,
                        onEnvoyer = vm::envoyer,
                        enCours = état.envois.any { it.statut == MessageEnvoi.Statut.EnCours },
                    )
                }
            }
        }
    }
}

@Composable
private fun SéparateurDate(jour: java.time.LocalDate) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = if (jour == java.time.LocalDate.now()) "Aujourd'hui" else jour.frenchLongDay(),
            style = MaterialTheme.typography.labelMedium,
            color = RegistreTheme.colors.chalk,
        )
    }
}

/** Message en attente d'envoi — même bulle que le parent, spinner ou état
 *  d'échec relançable à la place de l'horodatage. */
@Composable
private fun BulleEnvoi(
    envoi: MessageEnvoi,
    onRelancer: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            shape = ControlShape,
            color = RegistreTheme.colors.page,
            border = BorderStroke(1.dp, RegistreTheme.colors.sage),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (envoi.texte.isNotBlank()) {
                    Text(
                        text = envoi.texte,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RegistreTheme.colors.ink,
                    )
                }
                envoi.pièces.forEach { fichier ->
                    Text(
                        text = fichier.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                envoi.audio?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = RegistreTheme.colors.chalk,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Message vocal",
                            style = MaterialTheme.typography.bodySmall,
                            color = RegistreTheme.colors.chalk,
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (envoi.statut) {
                MessageEnvoi.Statut.EnCours -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = RegistreTheme.colors.chalk,
                    )
                    Text(
                        text = "Envoi…",
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                MessageEnvoi.Statut.Échec -> {
                    Text(
                        text = "Échec de l'envoi",
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.redPen,
                    )
                    androidx.compose.material3.TextButton(onClick = onRelancer) {
                        Text(
                            text = "Réessayer",
                            style = MaterialTheme.typography.labelSmall,
                            color = RegistreTheme.colors.ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Bulle(
    message: Message,
    onTélécharger: (Attachment, (java.io.File?) -> Unit) -> Unit,
) {
    val alignéDroite = !message.deLAdmin
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (alignéDroite) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            shape = ControlShape,
            color = if (message.deLAdmin) RegistreTheme.colors.sage else RegistreTheme.colors.page,
            border = if (message.deLAdmin) null else BorderStroke(1.dp, RegistreTheme.colors.sage),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = message.texte.replace("\r\n", "\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.ink,
                )
                message.attachments.forEach { pièce ->
                    LignePièceJointe(pièce = pièce, onTélécharger = onTélécharger)
                }
                message.audio?.let { pièce -> LigneAudio(pièce) }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            message.date?.let {
                Text(
                    text = it.frenchTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
            // Accusé de lecture : uniquement sur les messages du parent.
            if (!message.deLAdmin) {
                message.vuLe?.let {
                    Text(
                        text = "Vu ${it.frenchFull()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
        }
    }
}

@Composable
private fun LignePièceJointe(
    pièce: Attachment,
    onTélécharger: (Attachment, (java.io.File?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var enCours by remember(pièce.url) { mutableIntStateOf(0) }
    val occupé = enCours > 0

    Row(
        modifier = Modifier
            .clip(ControlShape)
            .clickable(enabled = !occupé) {
                enCours++
                onTélécharger(pièce) { fichier ->
                    enCours--
                    if (fichier != null) {
                        Fichiers.intentionOuvrir(context, fichier)?.let {
                            context.startActivity(it)
                        }
                    }
                }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (occupé) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = RegistreTheme.colors.chalk,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Attachment,
                contentDescription = null,
                tint = RegistreTheme.colors.chalk,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = pièce.name,
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LigneAudio(pièce: Attachment) {
    var version by remember { mutableIntStateOf(0) }
    val lecteur = remember(pièce.url) { LecteurAudio(pièce.url) { version++ } }
    DisposableEffect(lecteur) {
        onDispose { lecteur.libérer() }
    }
    LaunchedEffect(lecteur) {
        while (true) {
            kotlinx.coroutines.delay(200)
            version++
        }
    }

    Row(
        modifier = Modifier
            .clip(ControlShape)
            .clickable { lecteur.basculer() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (lecteur.enLecture) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (lecteur.enLecture) "Pause" else "Lire",
            tint = RegistreTheme.colors.ink,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = pièce.name.ifBlank { "Message vocal" },
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = "${mmSs(lecteur.position())} / ${mmSs(lecteur.duréeMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = RegistreTheme.colors.chalk,
        )
    }
}

/** « 1:07 » à partir de millisecondes. */
private fun mmSs(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.FRENCH, "%d:%02d", total / 60, total % 60)
}