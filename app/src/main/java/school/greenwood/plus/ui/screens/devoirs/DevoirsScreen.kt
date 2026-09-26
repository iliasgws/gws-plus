package school.greenwood.plus.ui.screens.devoirs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.ui.DevoirsViewModel
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SqueletteDevoirs
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RessortVif
import school.greenwood.plus.ui.theme.tabulaire
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.frenchShort
import school.greenwood.plus.util.htmlToPlainSingleLine

/*
 * Issue #78 : le point de suivi des devoirs par jour. Vert (accent « registre »)
 * quand tout le travail du jour est marqué fait, rouge stylo dès qu'un devoir
 * reste à faire — pas d'état intermédiaire. Rien si le jour n'a pas de devoirs.
 */

/** Suivi d'un jour : [Vert] = tous les devoirs faits, [Rouge] = au moins un reste. */
private enum class ÉtatJour { Vert, Rouge }

/** Jours avec devoirs non faits qui ont défilé hors de la rangée d'onglets. */
private data class JoursHorsÉcran(val àGauche: Boolean, val àDroite: Boolean)

/*
 * L'onglet Devoirs (docs/product/DESIGN.md §4) : liste par jour, sélection côté client sur
 * `date_remise` — le paramètre `date` du serveur est ignoré. Une pièce jointe
 * se télécharge dans l'espace privé de l'app puis s'ouvre en natif.
 */

@Composable
fun DevoirsScreen(
    container: AppContainer,
    padding: PaddingValues,
    onOuvrirDevoir: (String) -> Unit = {},
) {
    val vm: DevoirsViewModel = viewModel { DevoirsViewModel(container) }
    val état by vm.état.collectAsStateWithLifecycle()
    val aujourdhui = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Devoirs",
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
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Par jour de rentrée",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
            )
        }

        // Sélecteur de jour : les échéances présentes, plus aujourd'hui et demain.
        val jours = remember(état.tous, aujourdhui) {
            (état.tous.mapNotNull { it.dateRemise } + aujourdhui + aujourdhui.plusDays(1))
                .distinct()
                .sorted()
        }
        // Issue #78 : état « fait » de chaque jour, pour le point sur l'onglet
        // et la pastille hors écran. Le marquage local « fait pour moi »
        // (issue #82) compte lui aussi — le suivi reflète ce que l'utilisateur
        // a réellement terminé.
        val étatsJours = remember(état.tous, jours) {
            jours.associateWith { jour ->
                val duJour = état.tous.filter { it.dateRemise == jour }
                when {
                    duJour.isEmpty() -> null
                    duJour.all { it.fait || it.faitLocal } -> ÉtatJour.Vert
                    else -> ÉtatJour.Rouge
                }
            }
        }
        val listeJours = rememberLazyListState()
        // Un jour rouge hors du viewport : de quel côté est-il ?
        val horsÉcran by remember(jours, étatsJours) {
            derivedStateOf {
                val visibles = listeJours.layoutInfo.visibleItemsInfo
                val premier = visibles.minOfOrNull { it.index }
                val dernier = visibles.maxOfOrNull { it.index }
                JoursHorsÉcran(
                    àGauche = premier != null && premier > 0 &&
                        (0 until premier).any { étatsJours[jours[it]] == ÉtatJour.Rouge },
                    àDroite = dernier != null && dernier < jours.lastIndex &&
                        ((dernier + 1)..jours.lastIndex).any { étatsJours[jours[it]] == ÉtatJour.Rouge },
                )
            }
        }
        Box {
            LazyRow(
                state = listeJours,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(jours) { jour ->
                    JourChip(
                        jour = jour,
                        état = étatsJours[jour],
                        sélectionné = jour == état.jourChoisi,
                        onClick = { vm.choisirJour(jour) },
                    )
                }
            }
            PastilleHorsÉcran(horsÉcran.àGauche, horsÉcran.àDroite)
        }

        when {
            état.chargement -> SqueletteDevoirs()
            état.erreur != null && état.tous.isEmpty() -> Column(
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
            else -> {
                // Bandeau discret au-dessus de la liste quand un échec réseau
                // laisse le contenu connu affiché (issue #21).
                val duJour = état.tous.filter { it.dateRemise == état.jourChoisi }
                Column(Modifier.fillMaxSize()) {
                    état.erreur?.let { message ->
                        BandeauErreur(
                            message = message,
                            réessayer = { vm.charger(force = true) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (duJour.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                titre = "Rien ce jour-là",
                                message = "Aucun devoir pour cette date.",
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(duJour, key = { it.id }) { devoir ->
                                CarteDevoir(
                                    devoir = devoir,
                                    aujourdhui = aujourdhui,
                                    onOuvrir = { onOuvrirDevoir(devoir.id) },
                                    onBasculerFaitLocal = { vm.basculerFaitLocal(devoir) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourChip(
    jour: LocalDate,
    état: ÉtatJour?,
    sélectionné: Boolean,
    onClick: () -> Unit,
) {
    val accent = RegistreTheme.accent
    val échelle by animateFloatAsState(
        targetValue = if (sélectionné) 1.04f else 1f,
        animationSpec = RessortVif,
        label = "jourÉchelle",
    )
    Surface(
        shape = ControlShape,
        color = if (sélectionné) accent.conteneur else RegistreTheme.colors.page,
        border = if (sélectionné) null else BorderStroke(1.dp, RegistreTheme.colors.sage),
        modifier = Modifier
            .graphicsLayer {
                scaleX = échelle
                scaleY = échelle
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            état?.let { PointSuivi(it) }
            Text(
                text = if (jour == LocalDate.now()) "Aujourd'hui" else jour.frenchShort(),
                style = MaterialTheme.typography.labelMedium.tabulaire(),
                color = if (sélectionné) accent.surConteneur else RegistreTheme.colors.chalk,
            )
        }
    }
}

/** Le point de suivi (issue #78) : vert si tout le travail du jour est fait,
 *  rouge stylo dès qu'un devoir reste à faire. */
@Composable
private fun PointSuivi(état: ÉtatJour) {
    val couleur = when (état) {
        ÉtatJour.Vert -> RegistreTheme.colors.accents.getValue("registre").teinte
        ÉtatJour.Rouge -> RegistreTheme.colors.redPen
    }
    val description = when (état) {
        ÉtatJour.Vert -> "Tous les devoirs de ce jour sont faits"
        ÉtatJour.Rouge -> "Il reste des devoirs à faire ce jour-là"
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(couleur)
            .semantics { contentDescription = description },
    )
}

/** Issue #78 : quand un jour avec devoirs non faits a défilé hors de la rangée
 *  d'onglets, une petite pastille rouge au bord indique de quel côté il se
 *  trouve. */
@Composable
private fun BoxScope.PastilleHorsÉcran(àGauche: Boolean, àDroite: Boolean) {
    AnimatedVisibility(
        visible = àGauche,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterStart),
    ) {
        BadgeDirection(
            icône = Icons.Rounded.ChevronLeft,
            description = "Des devoirs non faits se trouvent plus tôt dans la liste des jours",
            modifier = Modifier.padding(start = 2.dp),
        )
    }
    AnimatedVisibility(
        visible = àDroite,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
        BadgeDirection(
            icône = Icons.Rounded.ChevronRight,
            description = "Des devoirs non faits se trouvent plus loin dans la liste des jours",
            modifier = Modifier.padding(end = 2.dp),
        )
    }
}

/** La pastille elle-même : rond rouge stylo, chevron vers le jour concerné. */
@Composable
private fun BadgeDirection(
    icône: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(RegistreTheme.colors.redPen)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icône,
            contentDescription = null,
            tint = RegistreTheme.colors.page,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CarteDevoir(
    devoir: Devoir,
    aujourdhui: LocalDate,
    onOuvrir: () -> Unit,
    onBasculerFaitLocal: () -> Unit,
) {
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOuvrir),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (devoir.matiere.isNotBlank()) Puce(devoir.matiere)
                devoir.categorie?.takeIf { it.isNotBlank() }?.let { Puce(it) }
                Spacer(Modifier.weight(1f))
                // Bascule « fait pour moi » (issue #82) : local, réversible
                // d'un clic, invisible pour l'école. Orange quand le travail
                // est fait pour soi mais pas encore pour l'école ; vert
                // Greenwood quand l'école le sait aussi.
                IconButton(
                    onClick = onBasculerFaitLocal,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (devoir.faitLocal) Icons.Rounded.CheckCircle
                        else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (devoir.faitLocal)
                            "Retirer le marquage « fait pour moi »"
                        else "Marquer fait pour moi (local, invisible à l'école)",
                        tint = when {
                            devoir.faitLocal && devoir.fait ->
                                RegistreTheme.colors.accents.getValue("registre").teinte
                            devoir.faitLocal -> RegistreTheme.colors.signetVif
                            else -> RegistreTheme.colors.chalk
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = devoir.title,
                style = MaterialTheme.typography.titleMedium,
                color = RegistreTheme.colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            devoir.enseignant?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
            // Le rouge ne marque que l'action requise (docs/product/DESIGN.md §2).
            // Le marquage local « fait pour moi » (issue #82) éteint le rouge :
            // le travail est fait au regard de l'utilisateur, sans toucher au
            // suivi officiel.
            when {
                devoir.fait -> Puce("Travail fait")
                devoir.faitLocal -> Puce("Fait pour moi")
                devoir.dateRemise != null && !devoir.dateRemise.isAfter(aujourdhui) ->
                    Puce("À faire", tintRed = true)
            }

            // Aperçu d'une ligne du corps (HTML aplati) — le détail complet
            // s'ouvre dans le panneau dédié (issue #60).
            devoir.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it.htmlToPlainSingleLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (devoir.attachments.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Attachment,
                        contentDescription = null,
                        tint = RegistreTheme.colors.chalk,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${devoir.attachments.size} pièce(s) jointe(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
        }
    }
}
