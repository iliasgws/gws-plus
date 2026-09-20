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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import school.greenwood.plus.ui.components.GlassSurface
import school.greenwood.plus.ui.components.GwsBouton
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.components.SqueletteMessages
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

    // La liste défile sous la barre flottante (Shell y a ajouté sa hauteur).
    val bas = padding.calculateBottomPadding()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = padding.calculateTopPadding() + 12.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.displayLarge,
                color = RegistreTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.définirComposeur(!état.composeurActivé) }) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = if (état.composeurActivé) "Désactiver le composeur" else "Activer le composeur",
                    tint = if (état.composeurActivé) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                )
            }
        }

        when {
            état.chargement -> Box(Modifier.fillMaxSize().padding(bottom = bas)) {
                SqueletteMessages()
            }
            état.erreur != null && état.conversations.isEmpty() -> Column(
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 12.dp + bas,
                    ),
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
                        if (contactRenseigné || état.composeurActivé) {
                            item(key = "contact") {
                                SectionLabel("Joindre l'administration")
                                CarteContact(
                                    texte = contact.texte,
                                    tel = contact.tel,
                                    facebook = contact.facebook,
                                    siteWeb = contact.siteWeb,
                                    composeurActif = état.composeurActivé,
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
                    Puce("${conversation.messages.size}")
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
    composeurActif: Boolean,
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
                if (composeurActif) {
                    PuceAction(
                        label = "Écrire",
                        icone = Icons.AutoMirrored.Rounded.Send,
                        onClick = onÉcrire,
                    )
                }
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
    GlassSurface(
        shape = ControlShape,
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
                    tint = RegistreTheme.colors.ink,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = RegistreTheme.colors.ink,
            )
        }
    }
}
