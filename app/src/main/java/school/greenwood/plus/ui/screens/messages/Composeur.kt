package school.greenwood.plus.ui.screens.messages

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import school.greenwood.plus.data.ai.RéglagesIA
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.tabulaire
import school.greenwood.plus.ui.theme.RegistreTheme
import java.io.File
import java.util.Locale

/*
 * Le composeur (issue #10, seconde partie) — pièce jointe · saisie · micro ·
 * envoi, posé au bas de l'écran. L'état d'enregistrement vit dans le composeur
 * (pastille + chrono + annulation), pas dans une grande boîte de dialogue à
 * l'ancienne. L'envoi n'est actif que s'il y a quelque chose à envoyer.
 */

@Composable
fun Composeur(
    texte: String,
    onTexte: (String) -> Unit,
    pièces: List<File>,
    onAjouterPièces: (Context, List<android.net.Uri>) -> Unit,
    onRetirerPièce: (File) -> Unit,
    audio: File?,
    onRetirerAudio: () -> Unit,
    enregistre: Boolean,
    onDémarrerEnregistrement: (Context) -> Boolean,
    onArrêterEnregistrement: () -> Unit,
    onAnnulerEnregistrement: () -> Unit,
    envoiPossible: Boolean,
    onEnvoyer: () -> Unit,
    enCours: Boolean = false,
    limitePièces: Boolean = false,
    ia: RéglagesIA? = null,
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Panneau IA (issue #56) : posé au-dessus du champ, ouvert par le bouton
    // ✨ à côté du micro — visible seulement si l'IA est réglée et prête.
    var panneauIAOuvert by remember { mutableStateOf(false) }

    // L'éditeur plein écran (⤢) : le même brouillon, la place en plus —
    // une boîte de dialogue instantanée, sans animation.
    var éditeurOuvert by remember { mutableStateOf(false) }

    // Micro : permission runtime (RECORD_AUDIO), demandée au premier appui.
    val permissionMicro = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { accordée ->
        if (accordée) onDémarrerEnregistrement(context)
    }

    // Sélecteur de pièces : SAF, plusieurs fichiers.
    val sélecteurFichiers = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (!uris.isNullOrEmpty()) onAjouterPièces(context, uris)
    }

    Column(Modifier.fillMaxWidth()) {
        if (panneauIAOuvert && ia?.prête == true) {
            PanneauIA(
                réglages = ia,
                texte = texte,
                onRemplacer = onTexte,
                onFermer = { panneauIAOuvert = false },
            )
        }
        if (pièces.isNotEmpty() || audio != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                audio?.let {
                    PucePièce(
                        nom = "Message vocal",
                        icone = Icons.Rounded.Mic,
                        onRetirer = onRetirerAudio,
                    )
                }
                pièces.forEach { fichier ->
                    PucePièce(nom = fichier.name, onRetirer = { onRetirerPièce(fichier) })
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = ControlShape,
            color = RegistreTheme.colors.page,
            border = androidx.compose.foundation.BorderStroke(1.dp, RegistreTheme.colors.sage),
        ) {
            if (enregistre) {
                // État d'enregistrement intégré : pastille rouge, chrono,
                // annulation — l'envoi du vocal, c'est l'arrêt (bouton stop).
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PastilleEnregistrement()
                    IconButton(onClick = onAnnulerEnregistrement) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Annuler l'enregistrement",
                            tint = RegistreTheme.colors.chalk,
                        )
                    }
                    Text(
                        text = "Enregistrement…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RegistreTheme.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onArrêterEnregistrement) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Terminer le message vocal",
                            tint = RegistreTheme.accent.teinte,
                        )
                    }
                }
            } else {
                Column {
                    // Rangée 1 : le texte, aligné en haut à gauche, qui pousse
                    // de 1 à 6 lignes puis défile ; ⤢ reste en haut à droite.
                    Row(verticalAlignment = Alignment.Top) {
                        BasicTextField(
                            value = texte,
                            onValueChange = onTexte,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
                            textStyle = TextStyle(
                                color = RegistreTheme.colors.ink,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            ),
                            cursorBrush = SolidColor(RegistreTheme.colors.ink),
                            decorationBox = { champInterne ->
                                if (texte.isEmpty()) {
                                    Text(
                                        text = "Écris ton message…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RegistreTheme.colors.chalk,
                                        maxLines = 1,
                                    )
                                }
                                champInterne()
                            },
                            minLines = 1,
                            maxLines = 6,
                        )
                        IconButton(
                            onClick = { éditeurOuvert = true },
                            modifier = Modifier.size(34.dp).padding(top = 4.dp, end = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInFull,
                                contentDescription = "Agrandir le message",
                                tint = RegistreTheme.colors.chalk,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    // Rangée 2 : la barre d'outils épinglée en bas — pièce
                    // jointe à gauche, micro · IA · envoi à droite.
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        IconButton(onClick = { sélecteurFichiers.launch(arrayOf("*/*")) }) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = "Joindre un fichier",
                                tint = RegistreTheme.colors.chalk,
                            )
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        val microAccordé = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        IconButton(
                            onClick = {
                                if (microAccordé) {
                                    if (!onDémarrerEnregistrement(context)) {
                                        Toast.makeText(
                                            context,
                                            "Micro indisponible",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                } else {
                                    permissionMicro.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Message vocal",
                                tint = RegistreTheme.colors.chalk,
                            )
                        }
                        // Le bouton IA, juste à côté du micro (issue #56) —
                        // visible dès que l'IA est activée ; s'il manque un
                        // réglage (modèle, clé…), l'appui guide vers Paramètres.
                        if (ia?.actif == true) {
                            IconButton(
                                onClick = {
                                    if (ia.prête) {
                                        if (!panneauIAOuvert) {
                                            // Le panneau s'ouvre sans le clavier —
                                            // il ne revient que si on touche le
                                            // champ d'instruction.
                                            focusManager.clearFocus(force = true)
                                        }
                                        panneauIAOuvert = !panneauIAOuvert
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Termine le réglage de l'IA dans les Paramètres (modèle, clé…)",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                enabled = texte.isNotBlank(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Assistant IA",
                                    tint = if (panneauIAOuvert) RegistreTheme.accent.teinte else RegistreTheme.colors.chalk,
                                )
                            }
                        }
                        IconButton(
                            onClick = onEnvoyer,
                            enabled = envoiPossible && !enCours && (texte.isNotBlank() || pièces.isNotEmpty() || audio != null),
                        ) {
                            if (enCours) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = RegistreTheme.colors.ink,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Envoyer",
                                    tint = if (envoiPossible) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (limitePièces) {
            Text(
                text = "Pièces jointes : 1 Mo maximum.",
                style = MaterialTheme.typography.labelSmall,
                color = RegistreTheme.colors.chalk,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }

    if (éditeurOuvert) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { éditeurOuvert = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = RegistreTheme.colors.paper,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Message",
                            style = MaterialTheme.typography.displayLarge,
                            color = RegistreTheme.colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { éditeurOuvert = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Réduire le message",
                                tint = RegistreTheme.colors.ink,
                            )
                        }
                    }
                    BasicTextField(
                        value = texte,
                        onValueChange = onTexte,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        textStyle = TextStyle(
                            color = RegistreTheme.colors.ink,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        cursorBrush = SolidColor(RegistreTheme.colors.ink),
                        decorationBox = { champInterne ->
                            if (texte.isEmpty()) {
                                Text(
                                    text = "Écris ton message…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = RegistreTheme.colors.chalk,
                                )
                            }
                            champInterne()
                        },
                    )
                    // En plein écran aussi, les quatre contrôles restent :
                    // pièce jointe à gauche, micro · IA · envoi à droite,
                    // « Terminé » referme l'éditeur.
                    if (panneauIAOuvert && ia?.prête == true) {
                        PanneauIA(
                            réglages = ia,
                            texte = texte,
                            onRemplacer = onTexte,
                            onFermer = { panneauIAOuvert = false },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        IconButton(onClick = { sélecteurFichiers.launch(arrayOf("*/*")) }) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = "Joindre un fichier",
                                tint = RegistreTheme.colors.chalk,
                            )
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        val microAccordé = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        IconButton(
                            onClick = {
                                if (microAccordé) {
                                    if (!onDémarrerEnregistrement(context)) {
                                        Toast.makeText(
                                            context,
                                            "Micro indisponible",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                } else {
                                    permissionMicro.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Message vocal",
                                tint = RegistreTheme.colors.chalk,
                            )
                        }
                        if (ia?.actif == true) {
                            IconButton(
                                onClick = {
                                    if (ia.prête) {
                                        panneauIAOuvert = !panneauIAOuvert
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Termine le réglage de l'IA dans les Paramètres (modèle, clé…)",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                enabled = texte.isNotBlank(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Assistant IA",
                                    tint = if (panneauIAOuvert) RegistreTheme.accent.teinte else RegistreTheme.colors.chalk,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                éditeurOuvert = false
                                onEnvoyer()
                            },
                            enabled = envoiPossible && !enCours && (texte.isNotBlank() || pièces.isNotEmpty() || audio != null),
                        ) {
                            if (enCours) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = RegistreTheme.colors.ink,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Envoyer",
                                    tint = if (envoiPossible) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                                )
                            }
                        }
                    }
                    Surface(
                        shape = ControlShape,
                        color = RegistreTheme.colors.sage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(ControlShape)
                            .clickable { éditeurOuvert = false },
                    ) {
                        Text(
                            text = "Terminé",
                            style = MaterialTheme.typography.labelLarge,
                            color = RegistreTheme.colors.ink,
                            modifier = Modifier
                                .padding(vertical = 11.dp)
                                .align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }
    }
}

/** Nom + croix d'une pièce (ou d'un vocal) en attente d'envoi. */
@Composable
private fun PucePièce(
    nom: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onRetirer: () -> Unit,
) {
    Surface(
        shape = ControlShape,
        color = RegistreTheme.colors.sage,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icone?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = RegistreTheme.colors.ink,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = nom,
                style = MaterialTheme.typography.labelSmall,
                color = RegistreTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            IconButton(onClick = onRetirer, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Retirer $nom",
                    tint = RegistreTheme.colors.chalk,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Pastille rouge + secondes écoulées. */
@Composable
private fun PastilleEnregistrement() {
    var secondes by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            secondes++
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(RegistreTheme.accent.teinte),
        )
        Text(
            text = String.format(Locale.FRENCH, "%d:%02d", secondes / 60, secondes % 60),
            style = MaterialTheme.typography.labelSmall.tabulaire(),
            color = RegistreTheme.colors.chalk,
        )
    }
}
