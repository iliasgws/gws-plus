package school.greenwood.plus.ui.screens.parametres

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.data.repo.UpdatesRepository
import school.greenwood.plus.BuildConfig
import school.greenwood.plus.ui.MiseÀJourViewModel
import school.greenwood.plus.ui.ParametresViewModel
import school.greenwood.plus.ui.components.CarteMiseÀJour
import school.greenwood.plus.ui.components.ErrorInline
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
    // Mises à jour (issue #46) — l'état partagé vit dans UpdatesRepository ;
    // le VM du panneau ne porte que le canal bêta et les actions.
    val majVm: MiseÀJourViewModel = viewModel { MiseÀJourViewModel(container) }
    val majÉtat by container.misesÀJour.état.collectAsStateWithLifecycle()
    val canalBêta by majVm.canalBêta.collectAsStateWithLifecycle()
    val contexte = LocalContext.current
    var notificationsAccordées by remember { mutableStateOf(container.misesÀJour.notificationsAccordées()) }
    val demandeurNotification = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { accordé -> notificationsAccordées = accordé }

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

            SectionLabel("Mises à jour")

            // La même carte que le Registre quand une publication plus
            // récente attend (issue #46).
            if (majÉtat.disponible != null) {
                CarteMiseÀJour(
                    état = majÉtat,
                    peutInstaller = container.misesÀJour.peutInstaller(),
                    surMettreÀJour = majVm::mettreÀJour,
                    surInstaller = majVm::relancerInstallation,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            GwsCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Version installée, avec le lien vers la page GitHub —
                    // un rappel discret de l'origine de l'app (issue #46).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Version installée",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RegistreTheme.colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        val uris = androidx.compose.ui.platform.LocalUriHandler.current
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodySmall,
                            color = RegistreTheme.colors.chalk,
                            modifier = Modifier.clickable {
                                uris.openUri(UpdatesRepository.PAGE_RELEASES)
                            },
                        )
                    }
                    // Contrôle manuel.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ControlShape)
                            .clickable(enabled = !majÉtat.contrôle) { majVm.vérifier() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (majÉtat.contrôle) "Vérification…" else "Vérifier les mises à jour",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RegistreTheme.colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        if (majÉtat.contrôle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = RegistreTheme.colors.chalk,
                            )
                        }
                    }
                    // Canal bêta — stable par défaut, opt-in (issue #46).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ControlShape)
                            .clickable { majVm.définirCanalBêta(!canalBêta) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Participer aux bêtas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RegistreTheme.colors.ink,
                            )
                            Text(
                                text = "Proposer aussi les préversions, avant leur publication stable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RegistreTheme.colors.chalk,
                            )
                        }
                        if (canalBêta) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Actif",
                                tint = RegistreTheme.accent.teinte,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    // Permission notifications — utile seulement sous Android 13+.
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ControlShape)
                                .clickable(enabled = !notificationsAccordées) {
                                    demandeurNotification.launch(
                                        android.Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Alertes de mise à jour",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RegistreTheme.colors.ink,
                                )
                                Text(
                                    text = if (notificationsAccordées) "Accordées" else "Autoriser la notification quand une version sort",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RegistreTheme.colors.chalk,
                                )
                            }
                            if (notificationsAccordées) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Accordées",
                                    tint = RegistreTheme.accent.teinte,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            majÉtat.erreur?.let { message ->
                ErrorInline(
                    message = message,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
            }

            Text(
                text = "L'application vérifie les nouvelles versions sur la page GitHub du projet " +
                    "(au plus une fois par 12 h, à l'ouverture) — aucun serveur intermédiaire, " +
                    "aucune donnée personnelle. Le téléchargement passe par le lien direct de la " +
                    "publication, puis l'installateur du système prend le relais.",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}
