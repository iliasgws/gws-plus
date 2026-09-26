package school.greenwood.plus.ui.screens.messages

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Forum
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.ui.MessagesViewModel
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.components.SqueletteMessages
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.frenchTime
import school.greenwood.plus.util.htmlToPlainSingleLine

/*
 * Messages avec l'administration (docs/product/DESIGN.md §4) — la liste des fils s'ouvre
 * sur l'écran de conversation dédié (issue #10, première partie) et une carte
 * « Écrire » ouvre un fil vierge (composeur, seconde partie).
 *
 * Le composeur est actif par défaut : l'envoi réel a été validé le 19/09/2026
 * (test vers l'administration). L'icône de réglage de la barre de titre sert
 * d'interrupteur pour le désactiver/réactiver. La carte contact rend
 * l'administration joignable tout de suite (téléphone, Facebook, site) ; elle
 * ne s'affiche que si le serveur renseigne quelque chose ou si le composeur
 * est actif.
 */

@Composable
fun MessagesScreen(
    container: AppContainer,
    padding: PaddingValues,
    onOuvrirConversation: (String) -> Unit,
    onNouveauMessage: () -> Unit,
) {
    val vm: MessagesViewModel = viewModel { MessagesViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Messages",
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
            }
        }

        when {
            état.chargement -> SqueletteMessages()
            état.erreur != null && état.conversations.isEmpty() -> Column(
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Bandeau discret en tête de liste quand un échec réseau
                    // laisse les fils connus affichés (issue #21).
                    état.erreur?.let { message ->
                        item(key = "erreur") {
                            BandeauErreur(
                                message = message,
                                réessayer = { vm.charger(force = true) },
                            )
                        }
                    }
                    if (état.conversations.isEmpty()) {
                        item {
                            EmptyState(
                                titre = "Aucun message",
                                message = "Les échanges avec l'administration apparaîtront ici.",
                                icone = Icons.Rounded.Forum,
                            )
                        }
                    }
                    items(état.conversations, key = { it.id }) { conversation ->
                        CarteConversation(
                            conversation = conversation,
                            onOuvrir = { onOuvrirConversation(conversation.id) },
                        )
                    }
                    état.contact?.let { contact ->
                        val contactRenseigné = listOfNotNull(
                            contact.texte, contact.tel, contact.facebook, contact.siteWeb,
                        ).any { it.isNotBlank() }
                        if (contactRenseigné) {
                            item(key = "contact") {
                                SectionLabel("Joindre l'administration")
                                CarteContact(
                                    texte = contact.texte,
                                    tel = contact.tel,
                                    facebook = contact.facebook,
                                    siteWeb = contact.siteWeb,
                                    onÉcrire = onNouveauMessage,
                                    onOuvrir = { url ->
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                                    onAppeler = {
                                        contact.tel?.let {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")))
                                            }
                                        }
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
private fun CarteConversation(
    conversation: Conversation,
    onOuvrir: () -> Unit,
) {
    val dernier = conversation.messages.lastOrNull()

    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOuvrir),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Point de direction : sage = l'école a parlé en dernier,
                // encre = le parent. Subtil, jamais un badge d'alarme.
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (dernier?.deLAdmin == true) {
                                RegistreTheme.colors.sage
                            } else {
                                RegistreTheme.colors.ink
                            },
                        ),
                )
                Text(
                    text = conversation.sujet,
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.messages.size > 1) {
                    Puce("${conversation.messages.size}", accent = RegistreTheme.accent)
                }
            }
            dernier?.let {
                Text(
                    text = it.texte.htmlToPlainSingleLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                it.date?.let { d ->
                    Text(
                        text = d.frenchFull(),
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
        }
    }
}

@Composable
private fun CarteContact(
    texte: String?,
    tel: String?,
    facebook: String?,
    siteWeb: String?,
    onÉcrire: () -> Unit,
    onOuvrir: (String) -> Unit,
    onAppeler: () -> Unit,
) {
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            texte?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PuceAction(
                    label = "Écrire",
                    icone = Icons.AutoMirrored.Rounded.Send,
                    onClick = onÉcrire,
                )
                if (tel != null) {
                    PuceAction(label = "Appeler", icone = Icons.Rounded.Call, onClick = onAppeler)
                }
                if (facebook != null) {
                    PuceAction(label = "Facebook", onClick = { onOuvrir(facebook) })
                }
                if (siteWeb != null) {
                    PuceAction(label = "Site", onClick = { onOuvrir(siteWeb) })
                }
            }
        }
    }
}

@Composable
private fun PuceAction(
    label: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = ControlShape,
        color = RegistreTheme.accent.conteneur,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icone?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = RegistreTheme.accent.surConteneur,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = RegistreTheme.accent.surConteneur,
            )
        }
    }
}
