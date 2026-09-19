package school.greenwood.plus.ui.screens.messages

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
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
import school.greenwood.plus.ui.theme.ControlShape
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
) {
    val context = LocalContext.current

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
                            tint = RegistreTheme.colors.redPen,
                        )
                    }
                }
            } else {
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
                    BasicTextField(
                        value = texte,
                        onValueChange = onTexte,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .padding(vertical = 8.dp),
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
                        maxLines = 6,
                    )
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
        if (limitePièces) {
            Text(
                text = "Pièces jointes : 1 Mo maximum.",
                style = MaterialTheme.typography.labelSmall,
                color = RegistreTheme.colors.chalk,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
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
                .background(RegistreTheme.colors.redPen),
        )
        Text(
            text = String.format(Locale.FRENCH, "%d:%02d", secondes / 60, secondes % 60),
            style = MaterialTheme.typography.labelSmall,
            color = RegistreTheme.colors.chalk,
        )
    }
}
