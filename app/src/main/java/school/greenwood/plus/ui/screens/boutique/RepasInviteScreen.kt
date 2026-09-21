package school.greenwood.plus.ui.screens.boutique

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.CantineJour
import school.greenwood.plus.ui.RepasViewModel
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.SqueletteDocuments
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Le raccourci « Repas invité » depuis le Registre (docs/product/DESIGN.md
 * §4) : le planning des menus, on tape un jour, on confirme — le POST est
 * celui de la boutique (vérifié en sonde le 21/09/2026) et le jour part
 * dans le commentaire de la commande.
 */

@Composable
fun RepasInviteScreen(
    container: AppContainer,
    padding: PaddingValues,
    retour: () -> Unit,
) {
    val vm: RepasViewModel = viewModel { RepasViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()
    var jourChoisi by remember { mutableStateOf<CantineJour?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = retour) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Retour",
                        tint = RegistreTheme.colors.ink,
                    )
                }
                Column {
                    Text(
                        text = "Repas invité",
                        style = MaterialTheme.typography.titleLarge,
                        color = RegistreTheme.colors.ink,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(AnnotationShape)
                            .background(RegistreTheme.accent.conteneur),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choisis le jour, l'école reçoit la commande",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
                modifier = Modifier.padding(start = 48.dp),
            )
        }

        when {
            état.chargement -> SqueletteDocuments()
            état.erreur != null && état.jours.isEmpty() -> Column(
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
            état.jours.isEmpty() -> Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    titre = "Aucun menu",
                    message = "Les prochains repas invités apparaîtront ici.",
                    icone = Icons.Rounded.Restaurant,
                )
            }
            else -> Column(Modifier.fillMaxSize()) {
                état.erreur?.let { message ->
                    BandeauErreur(
                        message = message,
                        réessayer = { vm.charger(force = true) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                PlanningCantine(
                    jours = état.jours,
                    envoi = état.envoi,
                    surRéserver = { jour -> jourChoisi = jour },
                )
            }
        }
    }

    jourChoisi?.let { jour ->
        DialogueConfirmationRepas(
            jour = jour,
            envoi = état.envoi,
            onConfirmer = {
                vm.réserver(jour)
                jourChoisi = null
            },
            onFermer = { jourChoisi = null },
        )
    }

    état.succès?.let { résultat ->
        DialogueSuccèsRepas(
            résultat = résultat,
            onFermer = { vm.acquis() },
        )
    }
}
