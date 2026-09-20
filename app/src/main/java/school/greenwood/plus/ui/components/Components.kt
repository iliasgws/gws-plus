package school.greenwood.plus.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Briques communes du registre. Discrètes : le rouge stylo n'apparaît que sur
 * l'action requise, le sage porte les annotations, les pages portent le contenu.
 */

/** Surface « page » — la carte de contenu standard, rayon 20 dp. */
@Composable
fun GwsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = PageShape,
        color = RegistreTheme.colors.page,
        content = { content() },
    )
}

/** Puce d'annotation (matière, statut, compteur) — rayon 6 dp, fond sage. */
@Composable
fun Puce(
    label: String,
    modifier: Modifier = Modifier,
    tintRed: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(AnnotationShape)
            .background(if (tintRed) RegistreTheme.colors.redPen else RegistreTheme.colors.sage)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (tintRed) RegistreTheme.colors.page else RegistreTheme.colors.ink,
        )
    }
}

/** Libellé de section — 13 sp, encre craie, discret. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight(600),
        color = RegistreTheme.colors.chalk,
    )
}

/** État vide : une invitation, pas un manque (docs/product/DESIGN.md §2). */
@Composable
fun EmptyState(
    titre: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = titre,
            style = MaterialTheme.typography.titleMedium,
            color = RegistreTheme.colors.ink,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.chalk,
        )
    }
}

/** Erreur en ligne : pictogramme + texte, teinte stylo rouge, jamais de fond rouge. */
@Composable
fun ErrorInline(
    message: String,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icone != null) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = RegistreTheme.colors.redPen,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.redPen,
        )
    }
}

/** Bandeau d'erreur non bloquant (issue #21) : quand un contenu connu reste
 *  affiché malgré un échec réseau, l'erreur se pose en annotation discrète
 *  au-dessus de lui — le stylo rouge n'écrit que le message, jamais un mur
 *  d'alarme — et un geste « Réessayer » relance le chargement forcé. */
@Composable
fun BandeauErreur(
    message: String,
    réessayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AnnotationShape,
        color = RegistreTheme.colors.sage,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ErrorInline(
                message = message,
                icone = Icons.Rounded.ErrorOutline,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = réessayer) {
                Text(
                    text = "Réessayer",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Avatar rond de l'élève : image signée si présente, sinon initiales sur sage. */
@Composable
fun GwsAvatar(
    initiales: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Int = 36,
) {
    if (imageUrl != null) {
        coil3.compose.AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(RegistreTheme.colors.sage),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initiales,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight(600),
                color = RegistreTheme.colors.ink,
            )
        }
    }
}

/*
 * Squelettes (issue #21) : des blocs pulsés à la forme du contenu à venir, pour
 * qu'un premier chargement occupe déjà la page. Pure décoration — aucune
 * sémantique, les lecteurs d'écran ne les voient pas. La géométrie copie les
 * écrans réels (mêmes marges, mêmes écarts) pour que la substitution
 * squelette → contenu ne fasse pas de saut.
 */

/** Bloc pulsé — la brique de base des squelettes : un fond sage qui respire
 *  (alpha 0,35 → 1, aller-retour), découpé à la forme demandée. */
@Composable
fun BlocSquelette(
    modifier: Modifier = Modifier,
    forme: Shape = AnnotationShape,
) {
    val pulsation = rememberInfiniteTransition(label = "squelette")
    val alpha by pulsation.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulsation",
    )
    Box(
        modifier = modifier
            .clip(forme)
            .background(RegistreTheme.colors.sage.copy(alpha = alpha)),
    )
}

/** Squelette du registre : en-tête (date, avatar), carte focale « Ce soir »,
 *  label de section puis trois entrées du jour en pages blanches. */
@Composable
fun SqueletteRegistre() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // En-tête : la date en grosse écriture (souvent sur deux lignes sur
        // téléphone) et l'avatar de l'élève consulté.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BlocSquelette(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                forme = ControlShape,
            )
            BlocSquelette(
                modifier = Modifier.size(40.dp),
                forme = CircleShape,
            )
        }

        // La carte focale : fond sage, liseré encre, comme la vraie « Ce soir ».
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = PageShape,
            color = RegistreTheme.colors.sage,
            border = BorderStroke(1.5.dp, RegistreTheme.colors.ink),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BlocSquelette(modifier = Modifier.width(90.dp).height(20.dp))
                BlocSquelette(modifier = Modifier.width(150.dp).height(13.dp))
                BlocSquelette(modifier = Modifier.fillMaxWidth().height(16.dp))
                BlocSquelette(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
            }
        }

        // Label de section « Aujourd'hui ».
        BlocSquelette(modifier = Modifier.width(90.dp).height(13.dp))

        // Trois entrées du jour — pages blanches comme les cartes réelles.
        repeat(3) { index ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                shape = PageShape,
                color = RegistreTheme.colors.page,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BlocSquelette(
                            modifier = Modifier
                                .fillMaxWidth(if (index == 0) 0.85f else 0.7f)
                                .height(16.dp),
                        )
                        BlocSquelette(modifier = Modifier.fillMaxWidth(0.45f).height(12.dp))
                    }
                    if (index == 0) {
                        // Vignette d'image de la première actualité.
                        BlocSquelette(
                            modifier = Modifier.size(64.dp, 40.dp),
                            forme = ControlShape,
                        )
                    } else {
                        BlocSquelette(modifier = Modifier.width(44.dp).height(12.dp))
                    }
                }
            }
        }
    }
}

/** Squelette des devoirs : cartes de devoir sous le sélecteur de jour réel
 *  (toujours affiché au-dessus) — matière, titre, corps déroulé. */
@Composable
fun SqueletteDevoirs() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = PageShape,
                color = RegistreTheme.colors.page,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BlocSquelette(modifier = Modifier.width(64.dp).height(20.dp))
                        BlocSquelette(modifier = Modifier.width(48.dp).height(20.dp))
                    }
                    BlocSquelette(
                        modifier = Modifier
                            .fillMaxWidth(if (index == 0) 0.85f else 0.7f)
                            .height(18.dp),
                        forme = ControlShape,
                    )
                    BlocSquelette(modifier = Modifier.fillMaxWidth().height(14.dp))
                    BlocSquelette(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
                }
            }
        }
    }
}

/** Squelette des documents : la recherche et les filtres sont rendus en réel
 *  au-dessus ; ici, groupes par matière — monogramme sur annotation puis deux
 *  lignes de texte. */
@Composable
fun SqueletteDocuments() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlocSquelette(modifier = Modifier.width(90.dp).height(13.dp))
        repeat(3) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = PageShape,
                color = RegistreTheme.colors.page,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Monogramme du type : annotation 48 dp.
                    BlocSquelette(modifier = Modifier.size(48.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BlocSquelette(
                            modifier = Modifier
                                .fillMaxWidth(if (index == 0) 0.8f else 0.65f)
                                .height(15.dp),
                        )
                        BlocSquelette(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp))
                    }
                }
            }
        }
    }
}

/** Squelette des messages : quelques fils — point de direction, sujet,
 *  extrait du dernier message, date. */
@Composable
fun SqueletteMessages() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = PageShape,
                color = RegistreTheme.colors.page,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BlocSquelette(
                            modifier = Modifier.size(6.dp),
                            forme = CircleShape,
                        )
                        BlocSquelette(
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp),
                            forme = ControlShape,
                        )
                        BlocSquelette(modifier = Modifier.width(24.dp).height(18.dp))
                    }
                    BlocSquelette(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.9f else 0.75f)
                            .height(13.dp),
                    )
                    BlocSquelette(modifier = Modifier.width(120.dp).height(12.dp))
                }
            }
        }
    }
}

/** Squelette des demandes : date de création, titre, puce de statut. */
@Composable
fun SqueletteDemandes() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = PageShape,
                color = RegistreTheme.colors.page,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BlocSquelette(modifier = Modifier.width(90.dp).height(12.dp))
                    BlocSquelette(
                        modifier = Modifier
                            .fillMaxWidth(if (index == 0) 0.85f else 0.65f)
                            .height(18.dp),
                        forme = ControlShape,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BlocSquelette(modifier = Modifier.width(70.dp).height(22.dp))
                        BlocSquelette(modifier = Modifier.width(110.dp).height(12.dp))
                    }
                }
            }
        }
    }
}

/** Squelette d'un fil de conversation : séparateur de date, bulles en
 *  alternance — administration à gauche (sage plein), parent à droite (page
 *  bordée sage) — et le composeur posé en bas. */
@Composable
fun SqueletteConversation() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Séparateur de date en tête de fil.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BlocSquelette(modifier = Modifier.width(120.dp).height(13.dp))
        }
        BulleSquelette(admin = true, largeur = 240.dp, hauteur = 44.dp)
        BulleSquelette(
            admin = false,
            largeur = 180.dp,
            modifier = Modifier.align(Alignment.End),
        )
        BulleSquelette(admin = true, largeur = 200.dp, hauteur = 44.dp)
        BulleSquelette(
            admin = false,
            largeur = 220.dp,
            modifier = Modifier.align(Alignment.End),
        )
        // Le fil respire, le composeur reste collé au bas de l'écran.
        Spacer(Modifier.weight(1f))
        BlocSquelette(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            forme = ControlShape,
        )
    }
}

/** Une bulle simulée : sage plein pour l'administration, page bordée sage
 *  pour le parent — même vocabulaire que le fil réel. */
@Composable
private fun BulleSquelette(
    admin: Boolean,
    largeur: Dp,
    modifier: Modifier = Modifier,
    hauteur: Dp = 44.dp,
) {
    if (admin) {
        BlocSquelette(
            modifier = modifier
                .width(largeur)
                .height(hauteur),
            forme = ControlShape,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = ControlShape,
            color = RegistreTheme.colors.page,
            border = BorderStroke(1.dp, RegistreTheme.colors.sage),
        ) {
            BlocSquelette(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .width(largeur)
                    .height(16.dp),
            )
        }
    }
}

/** Squelette du quiz : titre, carte de question, trois réponses bordées. */
@Composable
fun SqueletteQuiz() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlocSquelette(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(24.dp),
            forme = ControlShape,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = PageShape,
            color = RegistreTheme.colors.page,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BlocSquelette(modifier = Modifier.fillMaxWidth().height(16.dp))
                BlocSquelette(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp))
            }
        }
        repeat(3) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ControlShape,
                color = RegistreTheme.colors.page,
                border = BorderStroke(1.dp, RegistreTheme.colors.sage),
            ) {
                BlocSquelette(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .height(16.dp),
                )
            }
        }
    }
}

/** Squelette du flux d'actualités : cartes d'actualités avec vignette. */
@Composable
fun SqueletteActualites() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = PageShape,
                color = RegistreTheme.colors.page,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BlocSquelette(modifier = Modifier.width(70.dp).height(20.dp))
                        BlocSquelette(
                            modifier = Modifier
                                .fillMaxWidth(if (index % 2 == 0) 0.85f else 0.7f)
                                .height(18.dp),
                            forme = ControlShape,
                        )
                        BlocSquelette(modifier = Modifier.width(120.dp).height(12.dp))
                    }
                    BlocSquelette(
                        modifier = Modifier.size(64.dp),
                        forme = ControlShape,
                    )
                }
            }
        }
    }
}

/**
 * Squelette du détail d'un post. Il vit dans la colonne défilante de
 * [PostDetailScreen] — un défilement imbriqué serait mesuré avec une hauteur
 * infinie et ferait planter l'app au moindre ouvert de post.
 */
@Composable
fun SquelettePostDetail() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlocSquelette(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            forme = PageShape,
        )
        BlocSquelette(modifier = Modifier.width(80.dp).height(20.dp))
        BlocSquelette(modifier = Modifier.fillMaxWidth(0.9f).height(24.dp), forme = ControlShape)
        BlocSquelette(modifier = Modifier.width(140.dp).height(14.dp))
        Spacer(Modifier.height(8.dp))
        repeat(3) {
            BlocSquelette(modifier = Modifier.fillMaxWidth().height(16.dp))
        }
    }
}

/** Squelette de l'emploi du temps : navigation de semaine, puces de jours, créneaux. */
@Composable
fun SqueletteCours() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlocSquelette(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            forme = ControlShape,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(6) {
                BlocSquelette(
                    modifier = Modifier
                        .width(56.dp)
                        .height(44.dp),
                    forme = ControlShape,
                )
            }
        }
        repeat(4) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = PageShape,
                color = RegistreTheme.colors.page,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BlocSquelette(
                        modifier = Modifier
                            .width(56.dp)
                            .height(36.dp),
                        forme = ControlShape,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BlocSquelette(
                            modifier = Modifier
                                .fillMaxWidth(if (index % 2 == 0) 0.6f else 0.45f)
                                .height(16.dp),
                            forme = ControlShape,
                        )
                        BlocSquelette(modifier = Modifier.width(90.dp).height(12.dp))
                    }
                }
            }
        }
    }
}
