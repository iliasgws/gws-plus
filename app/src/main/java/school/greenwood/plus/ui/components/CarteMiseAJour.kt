package school.greenwood.plus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import school.greenwood.plus.data.repo.MiseÀJourÉtat
import school.greenwood.plus.data.repo.TéléchargementMaj
import school.greenwood.plus.data.repo.prendreRésumé
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * La carte « Mise à jour disponible » (issue #46) : posée en tête du
 * Registre quand une publication GitHub plus récente existe, elle porte la
 * version, un résumé des notes et le bouton unique « Mettre à jour » —
 * téléchargement depuis le lien direct GitHub, puis installateur du
 * système. État de téléchargement, d'échec et de ré-ouverture de
 * l'installateur inclus. Utilisée aussi dans les Paramètres.
 */

@Composable
fun CarteMiseÀJour(
    état: MiseÀJourÉtat,
    peutInstaller: Boolean,
    surMettreÀJour: () -> Unit,
    surInstaller: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val publication = état.disponible ?: return
    val uris = LocalUriHandler.current

    GwsCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Mise à jour disponible",
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Puce(if (publication.estBêta) "Bêta" else "Nouvelle version")
            }
            Text(
                text = "GWS+ ${publication.version}" + if (état.canalBêta && publication.estBêta) " · canal bêta" else "",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
            )
            publication.notes?.let { notes ->
                Text(
                    text = notes.prendreRésumé(320),
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!peutInstaller) {
                Text(
                    text = "Au premier lancement, l'installateur du système demande " +
                        "l'autorisation « Installer des apps inconnues » pour GWS+ — " +
                        "accordez-la une fois, puis revenez et touchez à nouveau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (val téléchargement = état.téléchargement) {
                    is TéléchargementMaj.EnCours -> Button(
                        onClick = {},
                        enabled = false,
                        shape = ControlShape,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = RegistreTheme.colors.page,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Téléchargement…")
                    }
                    is TéléchargementMaj.Réussi -> Button(
                        onClick = surInstaller,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RegistreTheme.colors.ink,
                            contentColor = RegistreTheme.colors.page,
                        ),
                        shape = ControlShape,
                    ) {
                        Text("Installer")
                    }
                    else -> Button(
                        onClick = surMettreÀJour,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RegistreTheme.colors.ink,
                            contentColor = RegistreTheme.colors.page,
                        ),
                        shape = ControlShape,
                    ) {
                        Text(if (téléchargement is TéléchargementMaj.Échec) "Réessayer" else "Mettre à jour")
                    }
                }
                TextButton(onClick = { uris.openUri(publication.page) }) {
                    Text(
                        text = "Voir sur GitHub",
                        style = MaterialTheme.typography.labelMedium,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
            if (état.téléchargement is TéléchargementMaj.Échec) {
                ErrorInline(message = "Téléchargement impossible pour le moment")
            }
        }
    }
}
