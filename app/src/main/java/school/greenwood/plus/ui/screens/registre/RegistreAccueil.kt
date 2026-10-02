package school.greenwood.plus.ui.screens.registre

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import school.greenwood.plus.data.repo.RegistreDuJour
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Eleve
import school.greenwood.plus.model.Post
import school.greenwood.plus.ui.components.GwsAvatar
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.GwsAccent
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.PiluleShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.tabulaire
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.frenchLongDay
import school.greenwood.plus.util.htmlToPlainSingleLine
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * Le haut de l'accueil (issue #101) : la barre d'identité, la carte focale
 * « Ce soir », la dernière actualité et les deux portes de l'école.
 *
 * Même grille que le reste de l'app — 8 dp, marges 20 dp, rayons 24/16/10 —
 * mais le relief passe à une ombre très basse et un liseré de 1 dp : la carte
 * focale se détache sans contour épais, et chaque commande garde une cible de
 * 48 dp. Le rouge stylo ne sert qu'au devoir réellement à faire.
 */

// — Seuils partagés par les cartes réactives (testés sans Compose).

/** Sous cette largeur disponible, « Ce soir » passe en colonne matière →
 *  titre → enseignant plutôt qu'en deux blocs compressés. */
internal fun ceSoirVertical(largeurDispoDp: Float): Boolean = largeurDispoDp < 344f

/** La vignette d'actualité passe au-dessus du texte sur écran étroit ou avec
 *  une police agrandie (130 % et plus). */
internal fun actualiteUneVerticale(largeurDispoDp: Float, échellePolice: Float): Boolean =
    largeurDispoDp < 344f || échellePolice > 1.3f

/** Pilule contextuelle : uniquement dérivée d'une vraie condition de jour,
 *  jamais un état inventé. */
internal fun piluleAccueil(date: LocalDate): String? =
    if (date.dayOfWeek == DayOfWeek.FRIDAY) "Bonne fin de semaine !" else null

/** Échéance complète de la carte « Ce soir », issue de l'horizon réel. */
internal fun échéanceCeSoir(horizon: LocalDate): String = "à rendre pour ${horizon.frenchLongDay()}"

/**
 * L'en-tête : la barre de commandes (menu, actualiser, élève consulté) puis la
 * date éditoriale sur sa propre ligne — jamais en concurrence avec le bloc
 * profil, sur 320 dp comme sur 430 dp.
 */
@Composable
internal fun EnTêteRegistre(
    date: LocalDate,
    eleve: Eleve?,
    rafraîchissement: Boolean,
    surOuvrirTiroir: () -> Unit,
    surOuvrirFeuille: () -> Unit,
    surActualiser: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = surOuvrirTiroir) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu",
                    tint = RegistreTheme.colors.ink,
                )
            }
            Spacer(Modifier.weight(1f))
            Actualiser(rafraîchissement = rafraîchissement, onClick = surActualiser)
            // Bascule d'enfant : fondu (docs/product/DESIGN.md §4). Le bloc
            // prend le reste de la ligne et tronque son texte plutôt que de
            // faire déborder la date, qui vit sur sa propre ligne.
            élèveBloc(
                eleve = eleve,
                modifier = Modifier.weight(1f),
                onClick = surOuvrirFeuille,
            )
        }

        // La date doit rester lisible en entier (« vendredi 2 octobre ») :
        // deux lignes plutôt qu'une coupure (issue #21).
        Text(
            text = date.frenchLongDay(),
            style = MaterialTheme.typography.displayLarge,
            color = RegistreTheme.colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        )
        // Le surligneur : le trait de l'onglet, sous le titre.
        Box(
            modifier = Modifier
                .padding(start = 6.dp, top = 6.dp)
                .size(width = 56.dp, height = 5.dp)
                .clip(AnnotationShape)
                .background(RegistreTheme.accent.conteneur),
        )
        piluleAccueil(date)?.let { message ->
            Box(
                modifier = Modifier
                    .padding(start = 6.dp, top = 10.dp)
                    .clip(PiluleShape)
                    .background(RegistreTheme.accent.conteneur)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = RegistreTheme.accent.surConteneur,
                )
            }
        }
    }
}

/** Le bouton d'actualisation : icône immobile au repos, en rotation lente
 *  pendant que le réseau rafraîchit en arrière-plan. */
@Composable
private fun Actualiser(rafraîchissement: Boolean, onClick: () -> Unit) {
    var angle = 0f
    if (rafraîchissement) {
        val rotation = rememberInfiniteTransition(label = "rafraîchissement")
        angle = rotation.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "angle",
        ).value
    }
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Actualiser",
            tint = if (rafraîchissement) RegistreTheme.accent.teinte else RegistreTheme.colors.chalk,
            modifier = Modifier
                .size(22.dp)
                .rotate(angle),
        )
    }
}

/** Avatar + prénom/classe + affordance discrète, cible tactile de 48 dp. */
@Composable
private fun élèveBloc(
    eleve: Eleve?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(ControlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GwsAvatar(
            initiales = eleve?.initiales ?: "·",
            imageUrl = eleve?.image,
            size = 32,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eleve?.prenom ?: eleve?.nomComplet ?: "Enfant",
                style = MaterialTheme.typography.labelLarge,
                color = RegistreTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            eleve?.niveau?.let { niveau ->
                Text(
                    text = niveau,
                    style = MaterialTheme.typography.labelSmall,
                    color = RegistreTheme.colors.chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = RegistreTheme.colors.chalk,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * La carte focale (docs/product/DESIGN.md §2) : fond vert menthe de l'accent
 * du Registre, liseré de 1 dp et une ombre très basse — le gros contour vert
 * a disparu, le contraste reste. La carte entière ouvre les devoirs.
 */
@Composable
internal fun CarteCeSoir(registre: RegistreDuJour, ouvrirDevoirs: () -> Unit) {
    val accent = RegistreTheme.accent
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = ouvrirDevoirs),
        shape = PageShape,
        color = accent.conteneur,
        border = BorderStroke(1.dp, accent.teinte.copy(alpha = 0.30f)),
        shadowElevation = 1.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val vertical = ceSoirVertical(maxWidth.value)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Ce soir",
                            style = MaterialTheme.typography.headlineSmall,
                            color = accent.surConteneur,
                        )
                        Text(
                            text = échéanceCeSoir(registre.horizonCeSoir),
                            style = MaterialTheme.typography.labelMedium.tabulaire(),
                            color = RegistreTheme.colors.chalk,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Voir les devoirs",
                        tint = accent.teinte,
                        modifier = Modifier.size(22.dp),
                    )
                }

                if (registre.ceSoir.isEmpty()) {
                    // Le vide est une bonne nouvelle, pas un manque.
                    Text(
                        text = "Rien pour demain",
                        style = MaterialTheme.typography.titleLarge,
                        color = accent.surConteneur,
                    )
                    Text(
                        text = "La rentrée prochaine est tranquille.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                } else {
                    registre.ceSoir.forEachIndexed { index, devoir ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(accent.teinte.copy(alpha = 0.25f)),
                            )
                        }
                        LigneDevoir(devoir = devoir, vertical = vertical)
                    }
                }
            }
        }
    }
}

/**
 * Un devoir de la carte focale : matière, titre entier (jamais coupé), enseignant.
 * La composition passe en colonne sur petit écran pour que rien ne se comprime.
 */
@Composable
private fun LigneDevoir(devoir: Devoir, vertical: Boolean) {
    val matière = devoir.matiere.ifBlank { "Devoir" }
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Puce(label = matière)
            Text(
                text = devoir.title,
                style = MaterialTheme.typography.titleMedium,
                color = RegistreTheme.colors.ink,
            )
            devoir.enseignant?.takeIf { it.isNotBlank() }?.let { enseignant ->
                Text(
                    text = enseignant,
                    style = MaterialTheme.typography.labelMedium,
                    color = RegistreTheme.colors.chalk,
                )
            }
            StatutDevoir(devoir = devoir)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Puce(label = matière)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = devoir.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                )
                devoir.enseignant?.takeIf { it.isNotBlank() }?.let { enseignant ->
                    Text(
                        text = enseignant,
                        style = MaterialTheme.typography.labelMedium,
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
            StatutDevoir(devoir = devoir)
        }
    }
}

/**
 * L'état fonctionnel du devoir — jamais la priorité, jamais le retard (ces
 * concepts n'existent pas dans la donnée) : coche quand il est fait, point
 * rouge stylo uniquement tant qu'il reste à faire, libellé pour TalkBack.
 */
@Composable
private fun StatutDevoir(devoir: Devoir) {
    when {
        devoir.fait -> Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Fait",
            tint = RegistreTheme.colors.ink,
            modifier = Modifier.size(18.dp),
        )

        devoir.faitLocal -> Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Marqué fait pour moi",
            tint = RegistreTheme.accent.teinte,
            modifier = Modifier.size(18.dp),
        )

        else -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(RegistreTheme.colors.redPen)
                .semantics { contentDescription = "À faire" },
        )
    }
}

/**
 * La dernière actualité : en-tête de section avec « Voir tout », puis la carte
 * à la une — vraie image à gauche, titre et horodatages à droite, chevron
 * dégagé. Sur écran étroit ou en grosse police, la vignette passe au-dessus.
 */
@Composable
internal fun SectionActualitéUne(
    post: Post,
    ouvrirPost: (String) -> Unit,
    ouvrirActualités: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(
                text = "Dernière actualité",
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = ouvrirActualités,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Voir tout",
                    style = MaterialTheme.typography.labelMedium,
                    color = RegistreTheme.accent.teinte,
                )
            }
        }
        CarteActualitéUne(post = post, onClick = { ouvrirPost(post.id) })
    }
}

/** La carte à la une d'une actualité (issue #101 §3). */
@Composable
private fun CarteActualitéUne(post: Post, onClick: () -> Unit) {
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        relief = 1.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val échelle = LocalDensity.current.fontScale
            val vertical = actualiteUneVerticale(maxWidth.value, échelle)
            if (vertical) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Vignette(
                        url = post.image,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                    TexteActualité(post = post, compact = false)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Vignette(
                        url = post.image,
                        modifier = Modifier
                            .width(96.dp)
                            .height(72.dp),
                    )
                    TexteActualité(
                        post = post,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = RegistreTheme.colors.chalk,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TexteActualité(post: Post, compact: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            post.categorie?.takeIf { it.isNotBlank() }?.let { categorie ->
                Puce(label = categorie, accent = RegistreTheme.accent)
            }
        }
        Text(
            text = post.title,
            style = MaterialTheme.typography.titleMedium,
            color = RegistreTheme.colors.ink,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        post.date?.let { date ->
            Text(
                text = date.frenchFull(),
                style = MaterialTheme.typography.labelMedium.tabulaire(),
                color = RegistreTheme.colors.chalk,
            )
        }
        post.intro?.takeIf { it.isNotBlank() }?.let { intro ->
            Text(
                text = intro.htmlToPlainSingleLine(),
                style = MaterialTheme.typography.labelSmall.tabulaire(),
                color = RegistreTheme.colors.chalk,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** La vignette d'une actualité : image à ratio fixe, repli discret pendant le
 *  chargement, en cas d'absence ou d'image cassée. */
@Composable
private fun Vignette(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(ControlShape)
            .background(RegistreTheme.colors.sage),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Image,
            contentDescription = null,
            tint = RegistreTheme.colors.chalk,
            modifier = Modifier.size(24.dp),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * Les deux portes de l'école, regroupées sous un même libellé : deux cartes
 * blanches flottantes, structure identique, hauteur dictée par le contenu.
 */
@Composable
internal fun SectionAccèsRapides(ouvrirEmploi: () -> Unit, ouvrirRepas: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = "Accès rapides")
        CarteAccès(
            intitulé = "Emploi du temps",
            sousTitre = "La semaine de l'école, jour par jour",
            icone = Icons.Rounded.CalendarMonth,
            accent = RegistreTheme.colors.accents["cours"],
            onClick = ouvrirEmploi,
        )
        CarteAccès(
            intitulé = "Repas invité",
            sousTitre = "Réserver le repas d'un jour de la cantine",
            icone = Icons.Rounded.Restaurant,
            accent = RegistreTheme.colors.accents["plus"],
            onClick = ouvrirRepas,
        )
    }
}

@Composable
private fun CarteAccès(
    intitulé: String,
    sousTitre: String,
    icone: ImageVector,
    accent: GwsAccent?,
    onClick: () -> Unit,
) {
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        relief = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent?.conteneur ?: RegistreTheme.colors.sage),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icone,
                    contentDescription = null,
                    tint = accent?.teinte ?: RegistreTheme.colors.ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = intitulé,
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                )
                Text(
                    text = sousTitre,
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = RegistreTheme.colors.chalk,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
