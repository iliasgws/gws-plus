package school.greenwood.plus.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Confirmation avant de quitter la session (issue #101) : un appui
 * accidentel ne déconnecte jamais. Le rouge stylo reste réservé à l'action
 * réellement requise (docs/product/DESIGN.md §2) — « Se déconnecter » est
 * une décision, pas une alerte : le bouton reste en encre.
 *
 * [enCours] fige les deux boutons pendant l'appel : la déconnexion ne part
 * qu'une fois, quel que soit le nombre d'appuis.
 */
@Composable
fun DialogueDéconnexion(
    enCours: Boolean,
    onConfirmer: () -> Unit,
    onAnnuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!enCours) onAnnuler() },
        title = {
            Text(
                text = "Se déconnecter ?",
                color = RegistreTheme.colors.ink,
            )
        },
        text = {
            Text(
                text = "Vous devrez vous reconnecter pour accéder à votre espace.",
                color = RegistreTheme.colors.ink,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmer, enabled = !enCours) {
                Text("Se déconnecter", color = RegistreTheme.colors.ink)
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler, enabled = !enCours) {
                Text("Annuler", color = RegistreTheme.colors.chalk)
            }
        },
    )
}
