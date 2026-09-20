package school.greenwood.plus.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Jetons étendus (docs/product/DESIGN.md §5) : les six rôles du registre exposés
 * au-delà du MaterialTheme, pour que « redPen » reste sémantique dans tout le
 * code UI, plus la palette de verre pour les surfaces translucides.
 */

/** Matériau de verre : fills translucides, liseré lumineux, teintes de l'aurore.
 *  `card` pose une feuille de verre calme sur l'aurore ; `bar` est la teinte de
 *  la barre flottante réellement floutée (API 31+) ; `barStrong` est le
 *  remplaçant opaque quand le floutage n'existe pas (API < 31) et le matériau
 *  des surfaces qui ne peuvent pas échantillonner (feuilles modales, composeur).
 *  `stroke` est le liseré lumineux au bord de chaque feuille. Les trois teintes
 *  `aura` sont les blobs du fond — le verre a besoin d'une aurore pour vivre. */
@Immutable
data class GlassPalette(
    val card: Color,
    val bar: Color,
    val barStrong: Color,
    val stroke: Color,
    val auraA: Color,
    val auraB: Color,
    val auraC: Color,
)

@Immutable
data class GwsColors(
    val ink: Color,
    val paper: Color,
    val page: Color,
    val sage: Color,
    val redPen: Color,
    val chalk: Color,
    val glass: GlassPalette,
)

fun lightGwsColors() = GwsColors(
    ink = InkLight,
    paper = PaperLight,
    page = PageLight,
    sage = SageLight,
    redPen = RedPenLight,
    chalk = ChalkLight,
    glass = GlassPalette(
        // Feuille de verre : blanc à 72 % sur l'aurore — l'encre reste lisible.
        card = Color(0xB8FFFFFF),
        // Teinte de la barre floutée : plus fine que la carte, l'aurore passe.
        bar = Color(0x99FFFFFF),
        // Quasi opaque : API < 31, feuilles modales, composeur.
        barStrong = Color(0xF2FFFFFF),
        // Liseré lumineux au bord des feuilles.
        stroke = Color(0x59FFFFFF),
        // Aurore : vert sauge en haut à gauche, crème chaud en bas à droite,
        // bleu-vert pâle à mi-hauteur à droite.
        auraA = Color(0x99D9E6D6),
        auraB = Color(0x8CF1E9D8),
        auraC = Color(0x80DCEBEE),
    ),
)

fun darkGwsColors() = GwsColors(
    ink = InkDark,
    paper = PaperDark,
    page = PageDark,
    sage = SageDark,
    redPen = RedPenDark,
    chalk = ChalkDark,
    glass = GlassPalette(
        card = Color(0xB31B241E),
        bar = Color(0x8C1B241E),
        barStrong = Color(0xF21B241E),
        stroke = Color(0x29FFFFFF),
        auraA = Color(0x8C1F3529),
        auraB = Color(0x592A2318),
        auraC = Color(0x5216303A),
    ),
)

val LocalGwsColors = staticCompositionLocalOf { lightGwsColors() }

/** Accesseur des jetons du registre : `RegistreTheme.colors.redPen` etc. */
object RegistreTheme {
    val colors: GwsColors
        @Composable
        get() = LocalGwsColors.current
}
