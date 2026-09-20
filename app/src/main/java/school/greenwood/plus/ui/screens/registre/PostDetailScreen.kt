package school.greenwood.plus.ui.screens.registre

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Commentaire
import school.greenwood.plus.model.QuestionPost
import school.greenwood.plus.ui.PostDetailViewModel
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.GwsAvatar
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.components.SquelettePostDetail
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.Fichiers
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.frenchShort
import school.greenwood.plus.util.htmlToPlainMultiline

private val CouleurSignet = Color(0xFFFC942D)

/**
 * Détail d'une actualité (GET `post_view?post=<id>`).
 * Affiche l'annonce complète, sa galerie photo, ses pièces jointes téléchargeables,
 * ses commentaires et ses quiz interactifs (protégés par kill switch).
 */
@Composable
fun PostDetailScreen(
    container: AppContainer,
    padding: PaddingValues,
    postId: String,
    retour: () -> Unit,
) {
    val vm: PostDetailViewModel = viewModel(key = "post_detail_$postId") {
        PostDetailViewModel(container, postId)
    }
    val état by vm.état.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val téléchargements = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = retour) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Retour",
                    tint = RegistreTheme.colors.ink,
                )
            }
        }

        état.erreur?.let { err ->
            BandeauErreur(
                message = err,
                réessayer = vm::charger,
            )
        }

        if (état.chargement && état.detail == null) {
            SquelettePostDetail()
        } else {
            val detail = état.detail
            if (detail != null) {
                GwsCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // En-tête : catégorie, signet, auteur
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            detail.categorie?.takeIf { it.isNotBlank() }?.let { categorie ->
                                Puce(label = categorie)
                            }
                            if (detail.bookmark) {
                                Icon(
                                    imageVector = Icons.Rounded.Bookmark,
                                    contentDescription = "Signet",
                                    tint = CouleurSignet,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Text(
                            text = detail.title.ifBlank { "Actualité" },
                            style = MaterialTheme.typography.headlineMedium,
                            color = RegistreTheme.colors.ink,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            detail.date?.let { date ->
                                Text(
                                    text = date.frenchFull(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RegistreTheme.colors.chalk,
                                )
                            }
                            detail.auteur?.takeIf { it.isNotBlank() }?.let { auteur ->
                                Text(
                                    text = "Par $auteur",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RegistreTheme.colors.chalk,
                                )
                            }
                        }

                        // Image principale de couverture
                        detail.image?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(PageShape),
                                contentScale = ContentScale.Crop,
                            )
                        }

                        // Galerie d'images secondaires si présentes (> 1 image)
                        if (detail.images.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                detail.images.forEach { imgUrl ->
                                    AsyncImage(
                                        model = imgUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(ControlShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }

                        // Corps de texte riche (HTML aplati avec paragraphes lisibles)
                        val brut = detail.descriptionHtml
                        if (!brut.isNullOrBlank()) {
                            Text(
                                text = brut.htmlToPlainMultiline(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = RegistreTheme.colors.ink,
                            )
                        }

                        // Pièces jointes
                        if (detail.files.isNotEmpty()) {
                            SectionLabel(text = "Pièces jointes (${detail.files.size})")
                            detail.files.forEach { pièce ->
                                LignePièceJointePost(
                                    pièce = pièce,
                                    enCours = téléchargements[pièce.url] == true,
                                    onTélécharger = {
                                        téléchargements[pièce.url] = true
                                        vm.téléchargerPièce(pièce, context) { fichier ->
                                            téléchargements[pièce.url] = false
                                            if (fichier != null) {
                                                val intention = Fichiers.intentionOuvrir(context, fichier)
                                                if (intention != null) context.startActivity(intention)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Section Quiz (si questions présentes)
                if (detail.questions.isNotEmpty()) {
                    GwsCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            SectionLabel(text = "Questionnaire")
                            if (!état.ecritureActivee) {
                                Text(
                                    text = "Mode lecture seule — participation aux quiz désactivée.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RegistreTheme.colors.chalk,
                                )
                            }

                            detail.questions.forEach { question ->
                                CarteQuestionPost(
                                    question = question,
                                    interactive = état.ecritureActivee,
                                    onRépondre = { choix ->
                                        vm.répondreQuestionQuiz(question.alias, choix)
                                    },
                                )
                            }
                        }
                    }
                }

                // Section Commentaires (si autorisés par l'école)
                if (detail.peutCommenter) {
                    GwsCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SectionLabel(text = "Commentaires (${detail.commentaires.size})")

                            if (detail.commentaires.isEmpty()) {
                                Text(
                                    text = "Aucun commentaire pour le moment.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RegistreTheme.colors.chalk,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            } else {
                                detail.commentaires.forEach { com ->
                                    CarteCommentaire(
                                        commentaire = com,
                                        peutRépondre = detail.peutRépondre && état.ecritureActivee,
                                        onRépondre = { vm.définirRéponseÀ(com.auteur) },
                                    )
                                }
                            }

                            // Formulaire de saisie d'un nouveau commentaire
                            if (detail.peutNouveauCommentaire && état.ecritureActivee) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    état.replyToId?.let { cible ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = "Réponse à $cible",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = RegistreTheme.colors.chalk,
                                            )
                                            IconButton(
                                                onClick = { vm.définirRéponseÀ(null) },
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Annuler la réponse",
                                                    tint = RegistreTheme.colors.chalk,
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = état.texteCommentaire,
                                            onValueChange = vm::majTexteCommentaire,
                                            placeholder = { Text("Votre commentaire…") },
                                            modifier = Modifier.weight(1f),
                                            shape = ControlShape,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = RegistreTheme.colors.ink,
                                                unfocusedBorderColor = RegistreTheme.colors.sage,
                                            ),
                                            maxLines = 3,
                                        )

                                        IconButton(
                                            onClick = vm::envoyerCommentaire,
                                            enabled = état.texteCommentaire.isNotBlank() && !état.envoiCommentaire,
                                        ) {
                                            if (état.envoiCommentaire) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = RegistreTheme.colors.ink,
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                                    contentDescription = "Envoyer",
                                                    tint = if (état.texteCommentaire.isNotBlank()) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (detail.peutCommenter && !état.ecritureActivee) {
                                Text(
                                    text = "L'envoi de commentaires est désactivé (kill switch actif).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RegistreTheme.colors.chalk,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (état.alertMerci) {
        AlertDialog(
            onDismissRequest = vm::masquerAlerteMerci,
            title = { Text("Merci !", color = RegistreTheme.colors.ink) },
            text = {
                Text(
                    text = "Vos réponses ont été enregistrées avec succès.",
                    color = RegistreTheme.colors.ink,
                )
            },
            confirmButton = {
                TextButton(onClick = vm::masquerAlerteMerci) {
                    Text("Fermer", color = RegistreTheme.colors.ink)
                }
            },
        )
    }
}

@Composable
private fun LignePièceJointePost(
    pièce: Attachment,
    enCours: Boolean,
    onTélécharger: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Attachment,
            contentDescription = null,
            tint = RegistreTheme.colors.chalk,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = pièce.name,
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (enCours) {
            CircularProgressIndicator(
                color = RegistreTheme.colors.ink,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            IconButton(onClick = onTélécharger, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Télécharger ${pièce.name}",
                    tint = RegistreTheme.colors.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CarteCommentaire(
    commentaire: Commentaire,
    peutRépondre: Boolean,
    onRépondre: () -> Unit,
    profondeur: Int = 0,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (profondeur * 16).dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val initiales = commentaire.auteur.take(2).uppercase().ifBlank { "GW" }
            GwsAvatar(
                initiales = initiales,
                imageUrl = commentaire.image,
                size = 28,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commentaire.auteur.ifBlank { "Parent" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = RegistreTheme.colors.ink,
                )
                commentaire.date?.let { d ->
                    Text(
                        text = d.toLocalDate().frenchShort(),
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
            if (peutRépondre) {
                TextButton(onClick = onRépondre, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("Répondre", style = MaterialTheme.typography.labelSmall, color = RegistreTheme.colors.ink)
                }
            }
        }
        Text(
            text = commentaire.texte,
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.ink,
            modifier = Modifier.padding(start = 36.dp),
        )

        commentaire.sousCommentaires.forEach { sous ->
            CarteCommentaire(
                commentaire = sous,
                peutRépondre = false,
                onRépondre = {},
                profondeur = profondeur + 1,
            )
        }
    }
}

@Composable
private fun CarteQuestionPost(
    question: QuestionPost,
    interactive: Boolean,
    onRépondre: (String) -> Unit,
) {
    Surface(
        shape = ControlShape,
        color = RegistreTheme.colors.page,
        border = BorderStroke(1.dp, RegistreTheme.colors.sage),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = question.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = RegistreTheme.colors.ink,
            )

            question.réponses.forEach { rep ->
                val estChoisie = question.réponseChoisie == rep
                Surface(
                    shape = ControlShape,
                    color = if (estChoisie) RegistreTheme.colors.sage else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = interactive) { onRépondre(rep) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (estChoisie) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (estChoisie) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = rep,
                            style = MaterialTheme.typography.bodySmall,
                            color = RegistreTheme.colors.ink,
                        )
                    }
                }
            }
        }
    }
}
