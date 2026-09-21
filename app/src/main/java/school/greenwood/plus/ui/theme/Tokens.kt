package school.greenwood.plus.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Jetons étendus (docs/product/DESIGN.md §5) : les rôles d'encre et papier
 * exposés au-delà du MaterialTheme — « redPen » reste sémantique dans tout le
 * code UI — plus une famille d'accent par onglet (« École vivante »).
 */

@Immutable
data class GwsColors(
    val ink: Color,
    val paper: Color,
    val page: Color,
    val sage: Color,
    val redPen: Color,
    val chalk: Color,
    /** Orange vif du signet des actualités. */
    val signetVif: Color,
    /** Une famille d'accent par onglet, clé = route de l'onglet. */
    val accents: Map<String, GwsAccent>,
)

/** Une famille d'accent : teinte (texte/glyphe sur carte), conteneur (fond
 *  doux), surConteneur (texte sur conteneur). */
@Immutable
data class GwsAccent(
    val teinte: Color,
    val conteneur: Color,
    val surConteneur: Color,
)

fun lightGwsColors() = GwsColors(
    ink = InkLight,
    paper = PaperLight,
    page = PageLight,
    sage = SageLight,
    redPen = RedPenLight,
    chalk = ChalkLight,
    signetVif = SignetVifLight,
    accents = mapOf(
        "registre" to GwsAccent(VertAccentTeinteLight, VertAccentConteneurLight, VertAccentSurConteneurLight),
        "actualites" to GwsAccent(AmbreTeinteLight, AmbreConteneurLight, AmbreSurConteneurLight),
        "cours" to GwsAccent(BleuTeinteLight, BleuConteneurLight, BleuSurConteneurLight),
        "devoirs" to GwsAccent(VioletTeinteLight, VioletConteneurLight, VioletSurConteneurLight),
        "documents" to GwsAccent(OcreTeinteLight, OcreConteneurLight, OcreSurConteneurLight),
        "messages" to GwsAccent(CorailTeinteLight, CorailConteneurLight, CorailSurConteneurLight),
        "plus" to GwsAccent(SarcelleTeinteLight, SarcelleConteneurLight, SarcelleSurConteneurLight),
    ),
)

fun darkGwsColors() = GwsColors(
    ink = InkDark,
    paper = PaperDark,
    page = PageDark,
    sage = SageDark,
    redPen = RedPenDark,
    chalk = ChalkDark,
    signetVif = SignetVifDark,
    accents = mapOf(
        "registre" to GwsAccent(VertAccentTeinteDark, VertAccentConteneurDark, VertAccentSurConteneurDark),
        "actualites" to GwsAccent(AmbreTeinteDark, AmbreConteneurDark, AmbreSurConteneurDark),
        "cours" to GwsAccent(BleuTeinteDark, BleuConteneurDark, BleuSurConteneurDark),
        "devoirs" to GwsAccent(VioletTeinteDark, VioletConteneurDark, VioletSurConteneurDark),
        "documents" to GwsAccent(OcreTeinteDark, OcreConteneurDark, OcreSurConteneurDark),
        "messages" to GwsAccent(CorailTeinteDark, CorailConteneurDark, CorailSurConteneurDark),
        "plus" to GwsAccent(SarcelleTeinteDark, SarcelleConteneurDark, SarcelleSurConteneurDark),
    ),
)

val LocalGwsColors = staticCompositionLocalOf { lightGwsColors() }

/**
 * L'accent de l'endroit où l'on se trouve, fourni autour du Scaffold par
 * AppNav (`LocalGwsAccent provides …`, fondu animé au changement d'onglet).
 * Les écrans lisent `RegistreTheme.accent` sans paramètre de plus.
 */
val LocalGwsAccent = staticCompositionLocalOf {
    GwsAccent(VertAccentTeinteLight, VertAccentConteneurLight, VertAccentSurConteneurLight)
}

/** Accesseur des jetons : `RegistreTheme.colors.redPen`, `RegistreTheme.accent`… */
object RegistreTheme {
    val colors: GwsColors
        @Composable
        get() = LocalGwsColors.current

    val accent: GwsAccent
        @Composable
        get() = LocalGwsAccent.current
}
