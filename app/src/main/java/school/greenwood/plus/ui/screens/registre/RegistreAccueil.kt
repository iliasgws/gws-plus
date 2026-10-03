package school.greenwood.plus.ui.screens.registre

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * Le haut de l'accueil (issues #101 + revue de finition) : la barre
 * d'identité, la carte focale « Ce soir », la dernière actualité et les deux
 * portes de l'école.
 *
 * Même grille que le reste de l'app — 8 dp, marges 20 dp, rayons 24/16/10 —
 * mais le relief passe à une ombre très basse et un liseré de 1 dp : la carte
 * focale se détache sans contour épais, et chaque commande garde une cible de
 * 48 dp. Le rouge stylo ne sert qu'au devoir réellement à faire, toujours
 * étiqueté. La densité compte : le premier écran doit laisser entrevoir les
 * accès rapides sans sacrifier une seule information.
 */

// — Seuils partagés par les cartes réactives (testés sans Compose).

/** Sous cette largeur disponible, « Ce soir » passe en colonne matière →
 *  titre → enseignant plutôt qu'en deux blocs compressés. */
internal fun ceSoirVertical(largeurDispoDp: Float): Boolean = largeurDispoDp < 344f

/**
 * La vignette d'actualité ne passe au-dessus du texte que sur un écran
 * vraiment étroit (moins de 300 dp utiles, soit 320 dp de fenêtre) ou avec
 * une police agrandie : à 360 dp et plus, la carte reste horizontale pour que
 * titre, date et « Vu » restent visibles dès le premier écran.
 */
internal fun actualiteUneVerticale(largeurDispoDp: Float, échellePolice: Float): Boolean =
    largeurDispoDp < 300f || échellePolice > 1.3f

/** Pilule contextuelle : uniquement dérivée d'une vraie condition de jour,
 *  jamais un état inventé. */
internal fun piluleAccueil(date: LocalDate): String? =
    if (date.dayOfWeek == DayOfWeek.FRIDAY) "Bonne fin de semaine !" else null

/** Échéance complète de la carte « Ce soir », issue de l'horizon réel. */
internal fun échéanceCeSoir(horizon: LocalDate): String = "à rendre pour ${horizon.frenchLongDay()}"

/**
 * L'en-tête : la barre de commandes (menu, pilule profil) puis la date
 * éditoriale sur sa propre ligne — jamais en concurrence avec le bloc profil,
 * sur 320 dp comme sur 430 dp. Une ligne quand elle tient, deux quand la
 * police grandit : jamais coupée. Rien d'autre : l'actualisation se fait au
 * geste, en tirant vers le bas (issue #104).
 */
@Composable
internal fun EnTêteRegistre(
    date: LocalDate,
    eleve: Eleve?,
    surOuvrirTiroir: () -> Unit,
    surOuvrirFeuille: () -> Unit,
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
            // La pilule profil : discrète, jamais dominante, cible 48 dp.
            élèveBloc(
                eleve = eleve,
                modifier = Modifier.weight(1f),
                onClick = surOuvrirFeuille,
            )
        }

        // La date doit rester lisible en entier (« vendredi 2 octobre ») :
        // une ligne quand elle tient, deux sinon (issue #21), jamais coupée.
        Text(
            text = date.frenchLongDay(),
            style = MaterialTheme.typography.displayLarge,
            color = RegistreTheme.colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        )
        // Le surligneur disparaît quand la pilule du jour porte déjà la
        // couleur : deux decorations identiques se neutralisent.
        val pilule = piluleAccueil(date)
        if (pilule == null) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp, top = 6.dp)
                    .size(width = 56.dp, height = 5.dp)
                    .clip(AnnotationShape)
                    .background(RegistreTheme.accent.conteneur),
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp, top = 12.dp)
                    .clip(PiluleShape)
                    .background(RegistreTheme.accent.conteneur)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = pilule,
                    style = MaterialTheme.typography.labelMedium,
                    color = RegistreTheme.accent.surConteneur,
                )
            }
        }
    }
}

/** La pilule profil : avatar + prénom/classe + affordance, sur page blanche,
 *  cible tactile de 48 dp, sans dominer la ligne de commandes. */
@Composable
private fun élèveBloc(
    eleve: Eleve?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(PiluleShape)
            .background(RegistreTheme.colors.page)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
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
 * La carte focale (docs/product/DESIGN.md §2) : dégradé menthe/sauge très
 * discret, liseré de 1 dp peu contrasté et ombre à peine perceptible — le
 * gros contour vert a disparu, le contraste reste. La carte entière ouvre les
 * devoirs ; chaque devoir ferme sur son statut, étiqueté.
 */
@Composable
internal fun CarteCeSoir(registre: RegistreDuJour, ouvrirDevoirs: () -> Unit) {
    val accent = RegistreTheme.accent
    val dégradé = Brush.verticalGradient(
        listOf(
            accent.conteneur,
            lerp(accent.conteneur, RegistreTheme.colors.page, 0.25f),
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp, shape = PageShape, clip = false)
            .clip(PageShape)
            .background(dégradé)
            .border(width = 1.dp, color = accent.teinte.copy(alpha = 0.20f), shape = PageShape)
            .clickable(onClick = ouvrirDevoirs),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val vertical = ceSoirVertical(maxWidth.value)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                    .background(accent.teinte.copy(alpha = 0.22f)),
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
 * Un devoir de la carte focale : matière, titre entier (jamais coupé),
 * enseignant — puis le statut en bas. La composition passe en colonne sur
 * petit écran pour que rien ne se comprime.
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
            LigneStatut(devoir = devoir)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            }
            LigneStatut(devoir = devoir)
        }
    }
}

/**
 * Le statut d'un devoir, posé en bas de l'entrée et toujours étiqueté :
 * le rouge stylo n'apparaît que sur une action parentale réellement
 * requise, annoncée comme telle à TalkBack — jamais un point isolé.
 * Priorité, non-lu et retard restent hors jeu : ces concepts n'existent pas
 * dans la donnée.
 */
@Composable
private fun LigneStatut(devoir: Devoir) {
    when {
        devoir.fait -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = RegistreTheme.colors.ink,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Fait",
                style = MaterialTheme.typography.labelMedium,
                color = RegistreTheme.colors.ink,
            )
        }

        devoir.faitLocal -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = RegistreTheme.colors.signetVif,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Marqué fait pour moi",
                style = MaterialTheme.typography.labelMedium,
                color = RegistreTheme.colors.chalk,
            )
        }

        else -> Row(
            modifier = Modifier.semantics(mergeDescendants = true) {
                stateDescription = "Action requise"
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Puce(label = "À faire", tintRed = true)
        }
    }
}

/**
 * La dernière actualité : en-tête de section avec « Voir tout », puis la carte
 * à la une — vignette compacte à gauche (104 dp) et texte à droite, titre,
 * date et « Vu » lisibles sans chevauchement, chevron dégagé. La variante
 * verticale (seulement sous 300 dp utiles ou à grosse police) borne son
 * image à 152 dp pour ne jamais repousser les tâches hors du premier écran.
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

/** La carte à la une d'une actualité (issues #101 §3 + revue P0). */
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
                            .height(152.dp),
                    )
                    TexteActualité(post = post, lignesDeTitre = 4)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Vignette(
                        url = post.image,
                        modifier = Modifier
                            .width(104.dp)
                            .height(104.dp),
                    )
                    TexteActualité(
                        post = post,
                        lignesDeTitre = 4,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = RegistreTheme.colors.chalk,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TexteActualité(
    post: Post,
    lignesDeTitre: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        post.categorie?.takeIf { it.isNotBlank() }?.let { categorie ->
            Puce(label = categorie, accent = RegistreTheme.accent)
        }
        Text(
            text = post.title,
            style = MaterialTheme.typography.titleMedium,
            color = RegistreTheme.colors.ink,
            maxLines = lignesDeTitre,
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
                maxLines = 2,
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
 * blanches flottantes, structure identique, 12 dp entre elles, hauteur dictée
 * par le contenu (les sous-titres se replient, jamais tronqués).
 */
@Composable
internal fun SectionAccèsRapides(ouvrirEmploi: () -> Unit, ouvrirRepas: () -> Unit) {
    Column {
        SectionLabel(text = "Accès rapides")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
