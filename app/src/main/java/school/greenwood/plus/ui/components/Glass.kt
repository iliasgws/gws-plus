package school.greenwood.plus.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.Shadow
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Primitives « liquid glass » (docs/product/DESIGN.md §2). Toute la bibliothèque
 * backdrop (capture d'arrière-plan, floutage, échantillonnage) est confinée à ce
 * fichier : si son API bouge, seul Glass.kt suit. Trois niveaux de verre :
 * les surfaces calmes (cartes du flux, puces, squelettes) posent une feuille
 * translucide sur l'aurore — GlassSurface, sans floutage, gratuit ; les feuilles
 * réelles (FeuilleVerre, barre flottante) échantillonnent la scène derrière
 * elles — vibrance, flou, réfraction : la partie « liquide » du matériau, sur
 * les petits nœuds où elle se voit. Sous l'API 31 le floutage n'existe pas :
 * tout bascule sur des fills opaques équivalents, conçus pour rester lisibles.
 */

/** Où lire la scène capturée ? Fournie une seule fois à la racine (AppNav) ;
 *  les primitives profondes (FeuilleVerre dans les écrans) la lisent ici
 *  plutôt que de la faire remonter paramètre par paramètre.
 *
 *  Règle de survie : c'est la capture de l'AURORE SEULE. Une feuille de verre
 *  ne peut jamais échantillonner une capture qui la contient — la couche se
 *  ré-enregistrerait avec une référence à elle-même et le premier rendu du
 *  contenu crashe (vu en bêta 2 : squelette affiché, puis crash). Le verre
 *  dans les écrans réfracte donc l'aurore ; seule la barre basse, hors de la
 *  capture de scène, voit le contenu qui défile derrière elle. */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/** Un fond d'aurore : trois halos doux et immobiles sur la base du papier.
 *  Le verre a besoin d'un fond avec de la variation pour être visible —
 *  c'est cette couche qu'il échantillonne. Statique : jamais d'animation
 *  déclenchée par rien (docs/product/DESIGN.md §2). */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier) {
    val glass = RegistreTheme.colors.glass
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val plusGrandCôté = maxOf(w, h)

                // Vert sauge — coin haut gauche
                val centreA = Offset(w * 0.16f, h * 0.10f)
                // Crème chaud — coin bas droit
                val centreB = Offset(w * 0.96f, h * 0.94f)
                // Bleu-vert pâle — mi-hauteur, bord droit
                val centreC = Offset(w * 1.04f, h * 0.46f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.auraA, glass.auraA.copy(alpha = 0f)),
                        center = centreA,
                        radius = plusGrandCôté * 0.62f,
                    ),
                    center = centreA,
                    radius = plusGrandCôté * 0.62f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.auraB, glass.auraB.copy(alpha = 0f)),
                        center = centreB,
                        radius = plusGrandCôté * 0.70f,
                    ),
                    center = centreB,
                    radius = plusGrandCôté * 0.70f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.auraC, glass.auraC.copy(alpha = 0f)),
                        center = centreC,
                        radius = plusGrandCôté * 0.48f,
                    ),
                    center = centreC,
                    radius = plusGrandCôté * 0.48f,
                )
            },
    )
}

/** Feuille de verre calme : fill translucide + liseré lumineux, sans floutage —
 *  visuellement équivalent à un floutage de l'aurore statique, pour aucun coût.
 *  `strong` passe sur le fill quasi opaque (feuilles modales, fallback API < 31). */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PageShape,
    strong: Boolean = false,
    content: @Composable () -> Unit,
) {
    val glass = RegistreTheme.colors.glass
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (strong) glass.barStrong else glass.card,
        border = BorderStroke(1.dp, glass.stroke),
        content = content,
    )
}

/** Feuille de verre réelle : elle échantillonne la scène derrière elle —
 *  vibrance, flou, puis réfraction (`lens`, la partie « liquide » du
 *  matériau, API 33+ ; no-op silencieux en dessous). À réserver aux petits
 *  nœuds toujours visibles ou posés sur du contenu qui passe derrière :
 *  une carte du flux n'a rien derrière elle — la réfraction y serait
 *  invisible et ne coûterait que des passes de shader.
 *  Sans capture disponible ou sous l'API 31 : fill opaque équivalent
 *  (`teinte` en pleine opacité, sinon barStrong), liseré conservé. */
@Composable
fun FeuilleVerre(
    modifier: Modifier = Modifier,
    forme: Shape = PageShape,
    teinte: Color? = null,
    liseré: BorderStroke? = null,
    flou: Dp = 12.dp,
    réfraction: Dp = 16.dp,
    vibrant: Boolean = true,
    lumineux: Boolean = true,
    content: @Composable () -> Unit,
) {
    val glass = RegistreTheme.colors.glass
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null || !floutageDisponible) {
        Surface(
            modifier = modifier,
            shape = forme,
            color = teinte?.copy(alpha = 1f) ?: glass.barStrong,
            border = liseré ?: BorderStroke(1.dp, glass.stroke),
            content = content,
        )
    } else {
        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { forme },
                    effects = {
                        if (vibrant) vibrancy()
                        blur(flou.toPx())
                        lens(réfraction.toPx(), (réfraction * 1.5f).toPx())
                    },
                    highlight = { if (lumineux) Highlight.Default else null },
                    onDrawSurface = { teinte?.let { drawRect(it) } },
                )
                .then(
                    if (liseré != null) {
                        Modifier.border(liseré, forme)
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
        }
    }
}

/** Réglages de la barre flottante : hauteur, marge flottante, espace total à
 *  réserver sous un contenu qui défile sous la barre. */
object GlassDefaults {
    val BarHeight = 64.dp
    val BarMargin = 12.dp
    val BarTotal = BarHeight + BarMargin
}

/** Le floutage réel existe-t-il ici ? API 31+ : RenderEffect. */
private val floutageDisponible: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Barre basse flottante : une capsule de verre liquide qui échantillonne le
 *  contenu qui défile derrière elle — la pile liquide complète (vibrance,
 *  flou, réfraction) sur un petit nœud toujours visible, comme la barre
 *  d'iOS 26. La réfraction est no-op sous l'API 33, le floutage sous l'API
 *  31 — la capsule retombe alors sur un fill quasi opaque. */
@Composable
fun GlassBottomBar(
    onglets: List<VerreOnglet>,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val glass = RegistreTheme.colors.glass
    val capsule = ControlShape

    if (!floutageDisponible || backdrop == null) {
        // Repli : même silhouette, fill quasi opaque, aucun échantillonnage.
        Row(
            modifier = modifier
                .clip(capsule)
                .background(glass.barStrong)
                .border(1.dp, glass.stroke, capsule)
                .padding(4.dp),
        ) {
            onglets.forEach { onglet -> ÉlémentOnglet(onglet, Modifier.weight(1f)) }
        }
        return
    }

    Row(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { capsule },
                effects = {
                    vibrancy()
                    blur(18.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = { Highlight.Plain },
                shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.14f)) },
                onDrawSurface = { drawRect(glass.bar) },
            )
            .padding(4.dp),
    ) {
        onglets.forEach { onglet -> ÉlémentOnglet(onglet, Modifier.weight(1f)) }
    }
}

/** Un onglet de la barre de verre — l'icône, le libellé, l'état, le geste. */
@Immutable
data class VerreOnglet(
    val icône: ImageVector,
    val libellé: String,
    val sélectionné: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ÉlémentOnglet(
    onglet: VerreOnglet,
    modifier: Modifier = Modifier,
) {
    val colors = RegistreTheme.colors
    val teinte = if (onglet.sélectionné) colors.ink else colors.chalk
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(ControlShape)
            .then(
                if (onglet.sélectionné) {
                    Modifier.background(colors.sage.copy(alpha = 0.72f))
                } else {
                    Modifier
                },
            )
            .selectable(
                selected = onglet.sélectionné,
                role = Role.Tab,
                onClick = onglet.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = onglet.icône,
                contentDescription = onglet.libellé,
                tint = teinte,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = onglet.libellé,
                style = MaterialTheme.typography.labelMedium,
                color = teinte,
            )
        }
    }
}
