package school.greenwood.plus.ui.components

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.RésultatOuvertureChatGPT
import school.greenwood.plus.util.ouvrirChatGPT

/*
 * Issue #150 : « Ouvrir dans ChatGPT », le voisin du bouton « Copier » — le
 * même texte part en prompt pré-rempli dans l'app ChatGPT Android, jamais
 * envoyé. ChatGPT absent → feuille de partage système + message ; rien ne
 * s'ouvre → message d'échec. Le bouton Copier reste seul maître de son texte.
 */

/**
 * @param texte le texte exact que « Copier » met dans le presse-papiers ;
 *   `null` (annonce ou devoir pas encore chargé) désactive le bouton.
 */
@Composable
fun BoutonOuvrirChatGPT(texte: String?) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            val contenu = texte ?: return@IconButton
            when (ouvrirChatGPT(context, contenu)) {
                RésultatOuvertureChatGPT.CHATGPT -> Unit
                RésultatOuvertureChatGPT.PARTAGE ->
                    Toast.makeText(context, "ChatGPT n'est pas installé", Toast.LENGTH_SHORT).show()
                RésultatOuvertureChatGPT.ÉCHEC ->
                    Toast.makeText(context, "Impossible d'ouvrir ChatGPT", Toast.LENGTH_SHORT).show()
            }
        },
        enabled = texte != null,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = "Ouvrir dans ChatGPT",
            tint = RegistreTheme.colors.ink,
        )
    }
}
