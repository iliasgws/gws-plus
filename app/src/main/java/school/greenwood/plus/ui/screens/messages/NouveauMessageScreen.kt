package school.greenwood.plus.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.ui.NouveauMessageViewModel
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Nouveau fil vers l'administration (issue #10, seconde partie). Sujet +
 * catégorie (serveur themes[]) + composeur complet. Limite officielle de 1 Mo
 * par pièce jointe (le chemin « nouveau message » seulement). Après l'envoi,
 * retour à la liste : le fil apparaît au rafraîchissement, comme dans l'app
 * d'origine (navigate back du bundle).
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NouveauMessageScreen(
    container: AppContainer,
    padding: PaddingValues,
    retour: () -> Unit,
    onEnvoyé: () -> Unit,
) {
    val vm: NouveauMessageViewModel = viewModel { NouveauMessageViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()
    // Réglages du composeur IA (issue #56) — null tant que non configuré.
    val réglagesIA by container.session.réglagesIA.collectAsStateWithLifecycle(initialValue = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            // Le clavier repousse le contenu (composeur visible au-dessus).
            .padding(padding)
            // Les insets du Scaffold (barre d'onglets, barre système) sont
            // consommés : le clavier ne s'ajoute plus par-dessus — le
            // composeur colle au clavier, sans vide.
            .consumeWindowInsets(padding)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
                text = "Nouveau message",
                style = MaterialTheme.typography.titleMedium,
                color = RegistreTheme.colors.ink,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Sujet",
                        style = MaterialTheme.typography.labelMedium,
                        color = RegistreTheme.colors.chalk,
                    )
                    OutlinedTextField(
                        value = état.sujet,
                        onValueChange = vm::modifierSujet,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text("Objet de ta demande…", style = MaterialTheme.typography.bodyMedium, color = RegistreTheme.colors.chalk)
                        },
                        shape = ControlShape,
                    )
                }

                if (état.themes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Catégorie",
                            style = MaterialTheme.typography.labelMedium,
                            color = RegistreTheme.colors.chalk,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            état.themes.forEach { theme ->
                                val choisi = état.themeChoisi?.id == theme.id
                                PuceThème(
                                    label = theme.label,
                                    choisi = choisi,
                                    onChoisir = { vm.choisirTheme(if (choisi) null else theme) },
                                )
                            }
                        }
                    }
                }

                état.erreur?.let {
                    ErrorInline(message = it)
                }
            }
        }

        Composeur(
            texte = état.texte,
            onTexte = vm::modifierTexte,
            pièces = état.pièces,
            onAjouterPièces = vm::ajouterPièces,
            onRetirerPièce = vm::retirerPièce,
            audio = état.audio,
            onRetirerAudio = vm::retirerAudio,
            enregistre = état.enregistre,
            onDémarrerEnregistrement = vm::démarrerEnregistrement,
            onArrêterEnregistrement = vm::arrêterEnregistrement,
            onAnnulerEnregistrement = vm::annulerEnregistrement,
            envoiPossible = état.sujet.isNotBlank() && état.texte.isNotBlank(),
            onEnvoyer = vm::envoyer,
            enCours = état.envoi,
            limitePièces = true,
            ia = réglagesIA,
        )

        // L'envoi réussi rebascule sur la liste (bundle : navigate back).
        LaunchedEffect(état.envoyé) {
            if (état.envoyé) onEnvoyé()
        }
    }
}

@Composable
private fun PuceThème(label: String, choisi: Boolean, onChoisir: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(AnnotationShape)
            .background(if (choisi) RegistreTheme.colors.ink else RegistreTheme.colors.sage)
            .clickable(onClick = onChoisir)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (choisi) RegistreTheme.colors.page else RegistreTheme.colors.ink,
        )
    }
}
