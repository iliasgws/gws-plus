package school.greenwood.plus.ui.screens.demandes

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.Demande
import school.greenwood.plus.ui.DemandesViewModel
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Les demandes administratives (DESIGN.md §4), atteintes depuis le Registre.
 * Lecture seule en v1 : la soumission (POST nouvelle-demande) a des champs non
 * vérifiés — pas d'invention. Le statut « Traitée » reste sage ; tout statut
 * en attente porte le stylo rouge : quelque chose attend une réponse ou une
 * action.
 */

@Composable
fun DemandesScreen(
    container: AppContainer,
    padding: PaddingValues,
    retour: () -> Unit,
) {
    val vm: DemandesViewModel = viewModel { DemandesViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = retour) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Retour",
                    tint = RegistreTheme.colors.ink,
                )
            }
            Text(
                text = "Demandes",
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
            état.demandes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    titre = "Aucune demande",
                    message = "Tes demandes apparaîtront ici.",
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(état.demandes, key = { it.id }) { demande ->
                        CarteDemande(demande)
                    }
                    item(key = "note") {
                        Text(
                            text = "Une nouvelle demande se fait auprès de " +
                                "l'administration — par message ou sur place.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RegistreTheme.colors.chalk,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteDemande(demande: Demande) {
    val traitée = demande.statut.equals("Traitée", ignoreCase = true)
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            demande.cree?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
            Text(
                text = demande.titre,
                style = MaterialTheme.typography.titleMedium,
                color = RegistreTheme.colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                demande.statut?.takeIf { it.isNotBlank() }?.let {
                    Puce(it, tintRed = !traitée)
                }
                demande.dateAffichee?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
            demande.reponses.forEach { réponse ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    réponse.label?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = RegistreTheme.colors.chalk,
                        )
                    }
                    Text(
                        text = réponse.reponse,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RegistreTheme.colors.ink,
                    )
                }
            }
            if (demande.file != null) {
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
                        text = "Pièce jointe",
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.ink,
                    )
                }
            }
        }
    }
}
