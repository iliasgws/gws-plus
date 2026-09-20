package school.greenwood.plus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import school.greenwood.plus.AppContainer
import school.greenwood.plus.ui.ConnexionViewModel
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.FeuilleVerre
import school.greenwood.plus.ui.components.GwsBouton
import school.greenwood.plus.ui.components.LiquidToggle
import school.greenwood.plus.ui.components.LiquidButton
import school.greenwood.plus.ui.components.givre
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Connexion (docs/product/DESIGN.md §4) : téléphone + mot de passe, un champ « retenir »,
 * pas de case « privacy » exposée (elle part à true), pas de création de
 * compte — l'inscription passe par l'école. Erreurs en ligne, stylo rouge.
 */
@Composable
fun LoginScreen(
    onConnecté: () -> Unit,
    container: AppContainer,
) {
    val vm: ConnexionViewModel = viewModel { ConnexionViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()


    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "GWS+",
                style = MaterialTheme.typography.displayLarge,
                color = RegistreTheme.colors.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Connexion à l'espace famille.",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
            )

            FeuilleVerre(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(top = 24.dp),
                teinte = RegistreTheme.colors.glass.card,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = état.téléphone,
                        onValueChange = vm::modifierTéléphone,
                        label = { Text("Numéro de téléphone") },
                        shape = ControlShape,
                        singleLine = true,
                        enabled = !état.chargement,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = état.motDePasse,
                        onValueChange = vm::modifierMotDePasse,
                        label = { Text("Mot de passe") },
                        shape = ControlShape,
                        singleLine = true,
                        enabled = !état.chargement,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiquidToggle(
                            selected = { état.retenir },
                            onSelect = vm::modifierRetenir,
                        )
                        Text(
                            text = "Retenir ma session",
                            style = MaterialTheme.typography.bodySmall,
                            color = RegistreTheme.colors.ink,
                        )
                    }

                    GwsBouton(
                        texte = "Se connecter",
                        onClick = vm::seConnecter,
                        enabled = !état.chargement,
                        chargement = état.chargement,
                        modifier = Modifier.fillMaxWidth(),
                    )


                    état.erreur?.let { message ->
                        ErrorInline(message = message, icone = Icons.Rounded.Error)
                    }

                    LiquidButton(
                        onClick = vm::demanderRappel,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        surfaceColor = givre(),
                        hauteur = 40.dp,
                    ) {
                        Text(
                            text = "Mot de passe oublié ?",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (état.rappelEnvoyé) {
                        Text(
                            text = "L'école t'enverra un rappel par SMS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RegistreTheme.colors.chalk,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
