package school.greenwood.plus.ui.screens.registre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import school.greenwood.plus.data.repo.EntreeRegistre
import school.greenwood.plus.model.Post
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.tabulaire
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.frenchNumeric
import school.greenwood.plus.util.htmlToPlainMultiline

/*
 * Aperçu contextuel (issue #107) : appui long sur une carte du Registre →
 * la feuille montre ce que la carte contient vraiment, sans naviguer. La
 * moitié pure (`apercuDe`) ne touche jamais Android (testable en JVM), ne
 * fabrique rien : seuls des champs réels remontent, HTML intact. Le corps
 * n'est aplati qu'à l'affichage, dans `FeuilleApercu`.
 */

/**
 * Ce que la feuille d'aperçu affiche : étiquette de type, titre, date déjà
 * formatée en français, corps brut (éventuellement HTML), vignette et lignes
 * secondaires réelles. Jamais d'information inventée — un champ absent est
 * null, jamais deviné.
 */
data class Apercu(
    /** Étiquette de type déjà en français : « Actualité », « Devoir »… */
    val type: String,
    val titre: String,
    /** Date française déjà formatée (`frenchFull` / `frenchNumeric`), null si inconnue. */
    val date: String?,
    /** Texte brut, parfois en HTML — aplati à l'affichage, jamais ici. */
    val corps: String?,
    /** URL d'image, ou null. */
    val vignette: String?,
    /** Lignes secondaires réelles (matière, enseignant, état…). */
    val détails: List<DétailApercu>,
)

/** Une ligne secondaire d'aperçu : libellé discret, valeur en encre. */
data class DétailApercu(val libellé: String, val valeur: String)

/**
 * L'aperçu d'une entrée du registre — les quatre types produisent toujours
 * un aperçu complet, sans jamais naviguer ni inventer.
 */
fun apercuDe(entrée: EntreeRegistre): Apercu = when (entrée) {
    is EntreeRegistre.Actualite -> apercuDe(entrée.post)

    is EntreeRegistre.DevoirDonné -> {
        val devoir = entrée.devoir
        Apercu(
            type = "Devoir",
            titre = devoir.title,
            date = devoir.publication?.frenchFull(),
            corps = devoir.description,
            vignette = null,
            détails = buildList {
                devoir.matiere.takeIf { it.isNotBlank() }?.let { add(DétailApercu("Matière", it)) }
                devoir.enseignant?.takeIf { it.isNotBlank() }?.let { add(DétailApercu("Enseignant(e)", it)) }
                devoir.categorie?.takeIf { it.isNotBlank() }?.let { add(DétailApercu("Catégorie", it)) }
                devoir.dateRemise?.let { add(DétailApercu("Échéance", it.frenchNumeric())) }
                add(
                    DétailApercu(
                        "État",
                        when {
                            devoir.fait -> "Travail fait (connu de l'école)"
                            devoir.faitLocal -> "Marqué fait pour moi"
                            else -> "À faire"
                        },
                    ),
                )
                if (devoir.attachments.isNotEmpty()) {
                    add(
                        DétailApercu(
                            "Pièces jointes",
                            devoir.attachments.joinToString(", ") { it.name },
                        ),
                    )
                }
            },
        )
    }

    is EntreeRegistre.AbsenceNotée -> {
        val absence = entrée.absence
        Apercu(
            type = "Absence",
            titre = absence.motif ?: "Absence notée",
            date = buildList {
                absence.du?.let { add(it.frenchNumeric()) }
                absence.au?.takeIf { it != absence.du }?.let { add(it.frenchNumeric()) }
            }.joinToString(" → ").takeIf { it.isNotBlank() },
            corps = null,
            vignette = null,
            détails = listOf(
                DétailApercu("État", if (absence.justifiee) "Justifiée" else "Non justifiée"),
            ),
        )
    }

    is EntreeRegistre.MessageReçu -> {
        val conversation = entrée.conversation
        Apercu(
            type = "Message",
            titre = conversation.sujet,
            date = conversation.dernierDate?.frenchFull(),
            corps = conversation.messages.lastOrNull()?.texte,
            vignette = null,
            détails = emptyList(),
        )
    }
}

/** L'aperçu d'une actualité seule (dernière actualité de l'accueil). */
fun apercuDe(post: Post): Apercu = Apercu(
    type = "Actualité",
    titre = post.title,
    date = post.date?.frenchFull(),
    corps = post.intro?.takeIf { it.isNotBlank() } ?: post.description,
    vignette = post.image,
    détails = buildList {
        post.categorie?.takeIf { it.isNotBlank() }?.let { add(DétailApercu("Catégorie", it)) }
        post.auteur?.takeIf { it.isNotBlank() }?.let { add(DétailApercu("Auteur", it)) }
    },
)

/** L'action éventuelle posée au bas de la feuille. */
data class ActionComplète(val libellé: String, val ouvrir: () -> Unit)

/** Ce que la feuille doit montrer : le contenu, plus une action éventuelle. */
data class ApercuOuverte(val contenu: Apercu, val action: ActionComplète? = null)

/**
 * La feuille d'aperçu (issue #107) : appui long sur une carte du Registre →
 * le contenu réel de la carte sans quitter l'écran. Le dehors ferme la
 * feuille (geste retour Android compris) sans jamais naviguer ; une action
 * éventuelle ferme d'abord la feuille, puis ouvre sa destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleApercu(demande: ApercuOuverte, surFermer: () -> Unit) {
    val feuille = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = surFermer,
        sheetState = feuille,
        containerColor = RegistreTheme.colors.page,
        shape = PageShape,
    ) {
        val contenu = demande.contenu
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Puce(label = contenu.type, accent = RegistreTheme.accent)

            Text(
                text = contenu.titre,
                style = MaterialTheme.typography.headlineSmall,
                color = RegistreTheme.colors.ink,
            )

            contenu.date?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelMedium.tabulaire(),
                    color = RegistreTheme.colors.chalk,
                )
            }

            contenu.détails.forEach { détail ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = détail.libellé,
                        style = MaterialTheme.typography.labelMedium,
                        color = RegistreTheme.colors.chalk,
                    )
                    Text(
                        text = détail.valeur,
                        style = MaterialTheme.typography.labelMedium,
                        color = RegistreTheme.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            contenu.vignette?.let { url ->
                BoxVignette(url = url)
            }

            contenu.corps?.takeIf { it.isNotBlank() }?.let { corps ->
                Text(
                    text = corps.htmlToPlainMultiline(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.ink,
                )
            }

            demande.action?.let { action ->
                Button(
                    onClick = {
                        surFermer()
                        action.ouvrir()
                    },
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistreTheme.colors.ink,
                        contentColor = RegistreTheme.colors.page,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = action.libellé,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** La vignette de l'aperçu : image à ratio fixe sur fond sauge, pour qu'un
 *  chargement ou une image cassée laisse un repli neutre et non un trou. */
@Composable
private fun BoxVignette(url: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(ControlShape)
            .background(RegistreTheme.colors.sage),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
