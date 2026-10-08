package school.greenwood.plus.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Confirmation avant d'oublier le compte communautaire (issue #139) :
 * le jeton EST le compte — sans lui, l'identité (votes, auteur) ne peut
 * pas être retrouvée. L'avertissement dit l'irréversibilité avant que
 * l'appui ne détruise quoi que ce soit ; « Annuler » est toujours là.
 *
 * [enCours] fige les boutons pendant la révocation : l'action ne part
 * qu'une fois.
 */
@Composable
fun DialogueOublierCompte(
    enCours: Boolean,
    onConfirmer: () -> Unit,
    onAnnuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!enCours) onAnnuler() },
        title = {
            Text(
                text = "Oublier le compte communautaire ?",
                color = RegistreTheme.colors.ink,
            )
        },
        text = {
            Text(
                text = "Ce compte est indépendant du compte de l'école : il vit sur " +
                    "cet appareil et survit aux changements de compte scolaire. " +
                    "Le supprimer détruit définitivement cette identité — votes et " +
                    "contenus publiés resteront sur le serveur sans pouvoir être " +
                    "reliés à nouveau. Action irréversible.",
                color = RegistreTheme.colors.ink,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmer, enabled = !enCours) {
                Text("Oublier le compte", color = RegistreTheme.colors.redPen)
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler, enabled = !enCours) {
                Text("Annuler", color = RegistreTheme.colors.chalk)
            }
        },
    )
}
