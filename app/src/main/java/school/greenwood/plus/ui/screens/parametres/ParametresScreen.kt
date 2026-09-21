package school.greenwood.plus.ui.screens.parametres

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.ui.ParametresViewModel
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Les réglages de l'app, atteints depuis le Registre. Une seule section en
 * v1 : la durée d'absence à partir de laquelle le retour au premier plan
 * relance le chargement des données (data/session/Veille.kt). Le réglage
 * survit à la déconnexion (préférence d'app, comme le composeur).
 */

/** Une durée possible : minutes d'absence (0 = « jamais ») et son libellé. */
private data class OptionActualisation(val minutes: Int, val libellé: String)

private val OptionsActualisation = listOf(
    OptionActualisation(0, "Jamais"),
    OptionActualisation(1, "1 minute"),
    OptionActualisation(2, "2 minutes"),
    OptionActualisation(5, "5 minutes"),
    OptionActualisation(10, "10 minutes"),
)

@Composable
fun ParametresScreen(
    container: AppContainer,
    padding: PaddingValues,
    retour: () -> Unit,
) {
    val vm: ParametresViewModel = viewModel { ParametresViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        // En-tête identique aux autres sous-écrans : retour, titre, surligneur.
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
                text = "Paramètres",
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel("Actualisation des données")

            GwsCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    OptionsActualisation.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ControlShape)
                                .clickable { vm.choisirDurée(option.minutes) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = option.libellé,
                                style = MaterialTheme.typography.bodyMedium,
                                color = RegistreTheme.colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                            if (état.minutesRetour == option.minutes) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Choisi",
                                    tint = RegistreTheme.accent.teinte,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Quand l'application reste en arrière-plan pendant au moins la durée choisie, " +
                    "sa réouverture relance le chargement des données — ce qui est déjà affiché " +
                    "reste visible pendant l'actualisation.",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}
