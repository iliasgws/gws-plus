package school.greenwood.plus.ui.screens.messages

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import school.greenwood.plus.model.Message
import school.greenwood.plus.ui.MessagesViewModel
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.frenchTime
import school.greenwood.plus.util.htmlToPlainSingleLine

/*
 * Messages avec l'administration (DESIGN.md §4) — lus comme une conversation,
 * pas comme un formulaire. L'envoi n'est pas ouvert en v1 : les champs du POST
 * nouveau-message ne sont pas vérifiés, et un envoi raté ferait croire à un
 * parent que l'école a été prévenue. La carte contact rend l'administration
 * joignable tout de suite (téléphone, Facebook, site).
 */

@Composable
fun MessagesScreen(
    container: AppContainer,
    padding: PaddingValues,
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.displayLarge,
                color = RegistreTheme.colors.ink,
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (état.conversations.isEmpty()) {
                        item {
                            EmptyState(
                                titre = "Aucun message",
                                message = "Les échanges avec l'administration apparaîtront ici.",
                            )
                        }
                    }
                    items(état.conversations, key = { it.id }) { conversation ->
                        CarteConversation(conversation = conversation)
                    }
                    état.contact?.let { contact ->
                        item(key = "contact") {
                            SectionLabel("Joindre l'administration")
                            CarteContact(
                                texte = contact.texte,
                                tel = contact.tel,
                                facebook = contact.facebook,
                                siteWeb = contact.siteWeb,
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

@Composable
private fun CarteConversation(conversation: Conversation) {
    val ouverts = remember { mutableStateMapOf<String, Boolean>() }
    val déroulé = ouverts[conversation.id] ?: false
    val dernier = conversation.messages.lastOrNull()

    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { ouverts[conversation.id] = !déroulé }
            .animateContentSize(animationSpec = spring()),
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

            if (déroulé) {
                conversation.messages.forEach { message ->
                    BulleMessage(message)
                }
            }
        }
    }
}

@Composable
private fun BulleMessage(message: Message) {
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
            Text(
                text = message.texte,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = RegistreTheme.colors.ink,
            )
        }
        message.date?.let {
            Text(
                text = it.frenchTime(),
                style = MaterialTheme.typography.labelSmall,
                color = RegistreTheme.colors.chalk,
            )
        }
        message.attachments.forEach { pièce ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = RegistreTheme.colors.chalk,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = pièce.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
        color = RegistreTheme.colors.sage,
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
