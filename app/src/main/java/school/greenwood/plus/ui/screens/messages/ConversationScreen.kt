package school.greenwood.plus.ui.screens.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.FeuilleVerre
import school.greenwood.plus.ui.components.GwsBouton
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.LiquidButton
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SqueletteConversation
import school.greenwood.plus.ui.components.givre
import school.greenwood.plus.ui.theme.BubbleInShape
import school.greenwood.plus.ui.theme.BubbleOutShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.frenchLongDay
import school.greenwood.plus.util.formatHour

/*
 * Fil de conversation (docs/product/DESIGN.md §5, issue #16).
 *
 * GET  `message?id={id}`   : lecture du fil complet
 * POST `message` (form)    : nouvel envoi (sujet + corps requis)
 * POST `message` (audio)   : vocal (WAV 16 kHz mono)
 * POST `message` (fichiers): pièces jointes (RFC 7578 multipart)
 *
 * Supporte le retry inline si un envoi échoue (issue #21) et la lecture
 * des notes vocales (MediaPlayer local avec suivi de position).
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

    // Le fil défile sous la barre flottante (Shell y a ajouté sa hauteur).
    val bas = padding.calculateBottomPadding()
    val hautStatut = padding.calculateTopPadding()

    Box(
        Modifier
            .fillMaxSize()
            // Le clavier repousse le contenu (composeur visible au-dessus).
            .imePadding(),
    ) {
        when {
            état.chargement -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = hautStatut + 80.dp, bottom = bas),
            ) {
                SqueletteConversation()
            }
            état.erreur != null && état.conversation == null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = hautStatut + 80.dp, start = 16.dp, end = 16.dp, bottom = bas),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorInline(message = état.erreur ?: "")
                GwsBouton(
                    texte = "Réessayer",
                    onClick = { vm.charger(force = true) },
                )
            }
            état.conversation?.messages?.isEmpty() != false ->
                Column(Modifier.fillMaxSize()) {
                    état.erreur?.let { message ->
                        // Le fil reste en place malgré l'échec (issue #21).
                        BandeauErreur(
                            message = message,
                            réessayer = { vm.charger(force = true) },
                            modifier = Modifier.padding(
                                top = hautStatut + 80.dp,
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 8.dp,
                            ),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = bas),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            titre = "Aucun message",
                            message = "Les échanges de ce fil apparaîtront ici.",
                        )
                    }
                }
            else -> {
                val conversation = état.conversation
                val messages = conversation?.messages ?: emptyList()
                val liste = rememberLazyListState()

                // Nombre d'éléments rendus (bandeau + séparateurs + bulles +
                // envois en attente), pour l'ouverture en bas de fil comme une
                // vraie messagerie.
                val compteÉléments = remember(conversation, état.envois, état.erreur) {
                    var n = if (état.erreur != null) 1 else 0
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

                // Le fil défile sous l'en-tête teinté et le composeur de verre.
                LazyColumn(
                    state = liste,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = hautStatut + 80.dp,
                        bottom = bas + 104.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    état.erreur?.let { message ->
                        item(key = "erreur") {
                            BandeauErreur(
                                message = message,
                                réessayer = { vm.charger(force = true) },
                            )
                        }
                    }
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
                    // Le composeur flotte au-dessus de la barre de verre,
                    // verre réel qui échantillonne la scène.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bas + 8.dp),
                    ) {
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

        // En-tête flottant teinté : bouton retour rond en verre + le fil.
        FeuilleVerre(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = hautStatut + 8.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            teinte = RegistreTheme.colors.glass.bar,
            flou = 12.dp,
            réfraction = 8.dp,
            vibrant = false,
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidButton(
                    onClick = retour,
                    modifier = Modifier.size(44.dp),
                    hauteur = 44.dp,
                    paddingHorizontal = 0.dp,
                    surfaceColor = givre(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Retour",
                        tint = RegistreTheme.colors.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
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
