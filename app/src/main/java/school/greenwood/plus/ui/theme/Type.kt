package school.greenwood.plus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import school.greenwood.plus.R

/*
 * Typographie « École vivante » (docs/product/DESIGN.md §2) : Bricolage
 * Grotesque pour l'affichage — un grotesque de caractère, énergie cour
 * d'école — et Public Sans pour le texte et l'interface. Hiérarchie par
 * écarts francs : 30 / 17 / 15 / 13, jamais deux niveaux adjacents à 1 sp
 * d'écart. Tout chiffre qui s'aligne ou change passe par tabulaire().
 */

private fun variableFont(resId: Int, weight: FontWeight, opsz: Boolean) = Font(
    resId = resId,
    weight = weight,
    variationSettings = if (opsz) {
        FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.opticalSizing(64.sp))
    } else {
        FontVariation.Settings(FontVariation.weight(weight.weight))
    },
)

private fun bricolageFont(weight: FontWeight) =
    variableFont(R.font.bricolage_grotesque, weight, opsz = true)

private fun publicSansFont(weight: FontWeight) =
    variableFont(R.font.public_sans, weight, opsz = false)

// Les graisses réellement employées par la hiérarchie — chaque fonte variable
// est déclarée une fois par graisse utilisée.
private val UsedWeights = listOf(
    FontWeight(400),
    FontWeight(500),
    FontWeight(600),
    FontWeight(700),
)

/** L'affichage « École vivante » : le grotesque de caractère. */
val BricolageGrotesqueFamily = FontFamily(UsedWeights.map { bricolageFont(it) })

/** Famille verrouillée d'office pour le texte et l'interface. */
val PublicSansFamily = FontFamily(UsedWeights.map { publicSansFont(it) })

/** L'affichage de l'app. */
val DisplayFontFamily = BricolageGrotesqueFamily

private fun display(weight: FontWeight, size: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size + 4).sp,
    letterSpacing = tracking.sp,
)

private fun interfaceStyle(weight: FontWeight, size: Int, lineHeight: Int) = TextStyle(
    fontFamily = PublicSansFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

val GwsTypography = Typography(
    // Titre d'écran — Bricolage 30/700, resserré
    displayLarge = display(FontWeight(700), 30, tracking = -0.5f),
    // Date du registre, gros compteurs — Bricolage
    headlineMedium = display(FontWeight(700), 24, tracking = -0.3f),
    headlineSmall = display(FontWeight(700), 21),
    // Titres d'états vides, gros chiffres de score
    displaySmall = display(FontWeight(700), 18),
    // Titre de carte — Public Sans 17/600
    titleLarge = interfaceStyle(FontWeight(600), 18, 24),
    titleMedium = interfaceStyle(FontWeight(600), 17, 24),
    titleSmall = interfaceStyle(FontWeight(600), 14, 20),
    // Corps — 15/400, légèrement plus grand pour la lecture longue
    bodyLarge = interfaceStyle(FontWeight(400), 16, 24),
    bodyMedium = interfaceStyle(FontWeight(400), 15, 22),
    // Annotations — 13
    bodySmall = interfaceStyle(FontWeight(400), 13, 18),
    // Boutons et puces
    labelLarge = interfaceStyle(FontWeight(500), 15, 20),
    labelMedium = interfaceStyle(FontWeight(500), 13, 18),
    labelSmall = interfaceStyle(FontWeight(500), 11, 16),
)

/**
 * Variante à chiffres tabulaires — dates, tranches horaires, comptes :
 * les chiffres s'alignent en colonne et ne sautent pas quand ils changent.
 * À appliquer sur tout Text qui affiche un nombre.
 */
fun TextStyle.tabulaire(): TextStyle = copy(fontFeatureSettings = "tnum")
