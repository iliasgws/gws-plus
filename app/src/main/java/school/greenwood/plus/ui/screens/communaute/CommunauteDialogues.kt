package school.greenwood.plus.ui.screens.communaute

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import school.greenwood.plus.ui.CommunauteViewModel
import school.greenwood.plus.ui.DialogueCommunautaire
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme

private const val LIMITE_TEXTE = 2_000
private const val LIMITE_MATIÈRE = 60

/*
 * Les dialogues de la section communautaire (issue #88) : la notice légale
 * avant la première écriture (APP.md §2 — ni refermable ni contournable),
 * les trois créations, le signalement d'abus et la confirmation de
 * suppression. La saisie vit dans le ViewModel : elle survit à un
 * redimensionnement, et la validation serveur s'y affiche.
 */

@Composable
fun VueDialogue(
    dialogue: DialogueCommunautaire,
    vm: CommunauteViewModel,
    envoi: Boolean,
) {
    val context = LocalContext.current
    val sélecteurFichiers = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> -> vm.ajouterFichiersDevoir(context, uris) }
    when (dialogue) {
        is DialogueCommunautaire.Notice -> DialogueNotice(
            mentions = dialogue.mentions,
            envoi = envoi,
            accepter = vm::accepterNotice,
            refuser = vm::refuserNotice,
        )
        is DialogueCommunautaire.CréerDevoir -> FormulaireDialogue(
            titre = "Proposer un devoir",
            explanation = "Partagé avec les autres familles — ni l'école ni les professeurs ne le reçoivent.",
            envoi = envoi,
            erreur = dialogue.erreur,
            annuler = vm::fermerDialogue,
            envoyer = vm::envoyer,
            champs = {
                ChampTexte(
                    label = "Matière",
                    valeur = dialogue.matière,
                    surChangement = { vm.saisirDevoir(it, dialogue.contenu, dialogue.dateRemise) },
                    placeholder = "Ex. Mathématiques",
                    max = LIMITE_MATIÈRE,
                    monoLigne = true,
                )
                ChampTexte(
                    label = "Contenu",
                    valeur = dialogue.contenu,
                    surChangement = { vm.saisirDevoir(dialogue.matière, it, dialogue.dateRemise) },
                    placeholder = "Ex. Exercices 12 à 18 p. 45",
                    max = LIMITE_TEXTE,
                )
                SélecteurDateRemise(dialogue.dateRemise) { vm.saisirDevoir(dialogue.matière, dialogue.contenu, it) }
                OutlinedButton(onClick = { sélecteurFichiers.launch(arrayOf("*/*")) }, enabled = !envoi) {
                    Text("Ajouter des pièces jointes (5 Mo maximum chacune)")
                }
                dialogue.fichiers.forEach { fichier ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("${fichier.name} · ${"%.1f".format(fichier.length() / 1_048_576.0)} Mo", modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.retirerFichierDevoir(fichier) }, enabled = !envoi) { Text("Retirer") }
                    }
                }
            },
        )
        is DialogueCommunautaire.CréerProblème -> FormulaireDialogue(
            titre = "Signaler un problème",
            explanation = "Décris ce qui cloche dans l'emploi du temps — la date permet de retrouver la journée concernée.",
            envoi = envoi,
            erreur = dialogue.erreur,
            annuler = vm::fermerDialogue,
            envoyer = vm::envoyer,
            champs = {
                ChampTexte(
                    label = "Description",
                    valeur = dialogue.description,
                    surChangement = { vm.saisirProblème(it, dialogue.date) },
                    placeholder = "Ex. Cours de maths déplacé mercredi sans préavis",
                    max = LIMITE_TEXTE,
                )
                ChampTexte(
                    label = "Date concernée",
                    valeur = dialogue.date,
                    surChangement = { vm.saisirProblème(dialogue.description, it) },
                    placeholder = "JJ/MM/AAAA ou AAAA-MM-JJ",
                    max = 10,
                    monoLigne = true,
                )
            },
        )
        is DialogueCommunautaire.CréerCorrection -> FormulaireDialogue(
            titre = "Proposer une correction",
            explanation = if (dialogue.problèmeId != null) {
                "Rattachée au signalement n° ${dialogue.problèmeId}."
            } else {
                "Une version corrigée d'une séance de l'emploi du temps."
            },
            envoi = envoi,
            erreur = dialogue.erreur,
            annuler = vm::fermerDialogue,
            envoyer = vm::envoyer,
            champs = {
                ChampTexte(
                    label = "Description",
                    valeur = dialogue.description,
                    surChangement = { vm.saisirCorrection(it, dialogue.date) },
                    placeholder = "Ex. Le cours de physique est en salle B12, pas B21",
                    max = LIMITE_TEXTE,
                )
                ChampTexte(
                    label = "Date concernée",
                    valeur = dialogue.date,
                    surChangement = { vm.saisirCorrection(dialogue.description, it) },
                    placeholder = "JJ/MM/AAAA ou AAAA-MM-JJ",
                    max = 10,
                    monoLigne = true,
                )
            },
        )
        is DialogueCommunautaire.Signaler -> FormulaireDialogue(
            titre = "Signaler ce contenu",
            explanation = "Le signalement est public et anonyme : seul le motif et la cible sont visibles.",
            envoi = envoi,
            erreur = dialogue.erreur,
            annuler = vm::fermerDialogue,
            envoyer = vm::envoyer,
            champs = {
                ChampTexte(
                    label = "Motif",
                    valeur = dialogue.raison,
                    surChangement = vm::saisirSignalement,
                    placeholder = "Ex. Contenu inapproprié ou faux",
                    max = LIMITE_TEXTE,
                )
            },
        )
        is DialogueCommunautaire.Supprimer -> AlertDialog(
            onDismissRequest = { if (!envoi) vm.fermerDialogue() },
            title = {
                Text(
                    text = "Supprimer définitivement ?",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "« ${dialogue.intitulé} » sera retiré de la communauté. " +
                            "L'action est définitive.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    dialogue.erreur?.let { ErrorInline(message = it) }
                }
            },
            confirmButton = {
                Button(
                    onClick = vm::confirmerSuppression,
                    enabled = !envoi,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistreTheme.colors.redPen,
                        contentColor = RegistreTheme.colors.page,
                    ),
                    shape = ControlShape,
                ) {
                    Text(if (envoi) "Suppression…" else "Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.fermerDialogue() }, enabled = !envoi) {
                    Text("Annuler")
                }
            },
        )
    }
}

@Composable
private fun SélecteurDateRemise(date: String, choisir: (String) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    val picker = rememberDatePickerState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Date de remise (facultative)", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { ouvert = true }) {
                Text(if (date.isBlank()) "Choisir une date" else date)
            }
            if (date.isNotBlank()) TextButton(onClick = { choisir("") }) { Text("Effacer") }
        }
    }
    if (ouvert) DatePickerDialog(
        onDismissRequest = { ouvert = false },
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { millis ->
                    choisir(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
                }
                ouvert = false
            }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = { ouvert = false }) { Text("Annuler") } },
    ) { DatePicker(state = picker) }
}

/** La notice légale (APP.md §2) : ni refermable au retour ni contournable —
 *  Accepter poursuit l'écriture en attente, Refuser révoque le compte. */
@Composable
private fun DialogueNotice(
    mentions: school.greenwood.plus.model.Mentions,
    envoi: Boolean,
    accepter: () -> Unit,
    refuser: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "Avant de participer",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Version du ${mentions.version}. La communauté est un espace " +
                        "entre familles, modéré par ses membres.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
                mentions.sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = section.titre,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight(600),
                            color = RegistreTheme.colors.ink,
                        )
                        Text(
                            text = section.texte,
                            style = MaterialTheme.typography.bodySmall,
                            color = RegistreTheme.colors.ink,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = accepter,
                enabled = !envoi,
                shape = ControlShape,
            ) {
                Text(if (envoi) "Validation…" else "Accepter")
            }
        },
        dismissButton = {
            TextButton(onClick = refuser, enabled = !envoi) {
                Text("Refuser")
            }
        },
    )
}

/** Temps entre l'écriture refusée et l'arrivée de la notice. */
@Composable
fun DialogueNoticeEnChargement() {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "Vérification de la notice…",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = RegistreTheme.colors.chalk,
                )
            }
        },
        confirmButton = {},
    )
}

/** Squelette commun des trois formulaires + du signalement. */
@Composable
private fun FormulaireDialogue(
    titre: String,
    explanation: String,
    envoi: Boolean,
    erreur: String?,
    annuler: () -> Unit,
    envoyer: () -> Unit,
    champs: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!envoi) annuler() },
        title = {
            Text(
                text = titre,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
                champs()
                erreur?.let { ErrorInline(message = it) }
            }
        },
        confirmButton = {
            Button(
                onClick = envoyer,
                enabled = !envoi,
                shape = ControlShape,
            ) {
                Text(if (envoi) "Envoi…" else "Envoyer")
            }
        },
        dismissButton = {
            TextButton(onClick = annuler, enabled = !envoi) {
                Text("Annuler")
            }
        },
    )
}

/** Champ de formulaire : compteur de caractères aligné sur la limite serveur
 *  (le dépôt tronque aussi — jamais d'envoi de plus de 10 Ko). */
@Composable
private fun ChampTexte(
    label: String,
    valeur: String,
    surChangement: (String) -> Unit,
    placeholder: String,
    max: Int,
    monoLigne: Boolean = false,
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = { neuf -> surChangement(if (neuf.length <= max) neuf else neuf.take(max)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = RegistreTheme.colors.chalk,
            )
        },
        singleLine = monoLigne,
        minLines = if (monoLigne) 1 else 3,
        supportingText = {
            Text("${valeur.length} / $max", style = MaterialTheme.typography.labelSmall)
        },
        shape = ControlShape,
    )
}
