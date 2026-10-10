package school.greenwood.plus.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import school.greenwood.plus.data.repo.PartageChatGPT
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.RésultatOuvertureChatGPT
import school.greenwood.plus.util.ouvrirChatGPT

/*
 * Issue #150 : « Ouvrir dans ChatGPT », le voisin du bouton « Copier ». Le
 * prompt est préparé À LA VOLÉE (élément sélectionné en entier + index
 * complets des autres devoirs et des actualités + pièces jointes téléchargées
 * pour partir en flux — data/repo/ChatGPTRepository.kt) : le bouton affiche un
 * indicateur pendant ce temps, puis ouvre ChatGPT avec le texte pré-rempli et
 * les fichiers en pièce jointe. Rien n'est jamais envoyé automatiquement.
 */

/**
 * @param actif false tant que l'élément ouvert n'est pas chargé.
 * @param préparer construit le partage (réseau + téléchargements) ; null si
 *   rien n'est préparable.
 */
@Composable
fun BoutonOuvrirChatGPT(
    actif: Boolean,
    préparer: suspend () -> PartageChatGPT?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val étendue = rememberCoroutineScope()
    var enCours by remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            if (!actif || enCours) return@IconButton
            étendue.launch {
                enCours = true
                val partage = runCatching { préparer() }.getOrNull()
                enCours = false
                val résultat = partage?.let { ouvrirChatGPT(context, it.texte, it.fichiers) }
                when {
                    partage == null ->
                        Toast.makeText(context, "Préparation du partage impossible", Toast.LENGTH_SHORT).show()
                    résultat == RésultatOuvertureChatGPT.PARTAGE ->
                        Toast.makeText(context, "ChatGPT n'est pas installé", Toast.LENGTH_SHORT).show()
                    résultat == RésultatOuvertureChatGPT.ÉCHEC ->
                        Toast.makeText(context, "Impossible d'ouvrir ChatGPT", Toast.LENGTH_SHORT).show()
                }
            }
        },
        enabled = actif && !enCours,
        modifier = modifier,
    ) {
        if (enCours) {
            GwsLoadingIndicator(
                color = RegistreTheme.colors.ink,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "Ouvrir dans ChatGPT",
                tint = RegistreTheme.colors.ink,
            )
        }
    }
}
