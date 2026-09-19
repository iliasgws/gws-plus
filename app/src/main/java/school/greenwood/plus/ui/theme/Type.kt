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
 * Typographie (docs/product/DESIGN.md §2) : Fraunces pour l'affichage, Public Sans pour le
 * texte et l'interface — chiffres tabulaires pour les dates, tranches et
 * comptes. Hiérarchie par écarts francs : 28 / 17 / 15 / 13, jamais deux
 * niveaux adjacents à 1 sp d'écart.
 *
 * Fraunces est un *candidat*, pas un choix verrouillé : le verdict se prend
 * sur un vrai écran. Bricolage Grotesque est embarqué à côté ; le premier
 * essai sur l'appareil se fait en basculant DisplayFontFamily d'une ligne.
 */

private fun variableFont(resId: Int, weight: FontWeight, opsz: Boolean) = Font(
    resId = resId,
    weight = weight,
    variationSettings = if (opsz) {
        FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.opticalSizing(40.sp))
    } else {
        FontVariation.Settings(FontVariation.weight(weight.weight))
    },
)

private fun frauncesFont(weight: FontWeight) = variableFont(R.font.fraunces, weight, opsz = true)

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

/** Le serif à l'encre du registre — candidat d'affichage (verdict sur l'écran). */
val FrauncesFamily = FontFamily(UsedWeights.map { frauncesFont(it) })

/** Le substitut prévu pour l'essai on-device : basculer cette ligne. */
val BricolageGrotesqueFamily = FontFamily(UsedWeights.map { bricolageFont(it) })

/** Famille verrouillée d'office pour le texte et l'interface. */
val PublicSansFamily = FontFamily(UsedWeights.map { publicSansFont(it) })

/** L'affichage du registre. Fraunces par défaut ; essai croisé avec Bricolage. */
val DisplayFontFamily = FrauncesFamily

private fun display(weight: FontWeight, size: Int) = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.2).sp,
)

private fun interfaceStyle(weight: FontWeight, size: Int, lineHeight: Int) = TextStyle(
    fontFamily = PublicSansFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

val GwsTypography = Typography(
    // Titre d'écran — Fraunces 28/600
    displayLarge = display(FontWeight(600), 28),
    // Date du registre, gros compteurs — Fraunces
    headlineMedium = display(FontWeight(600), 24),
    headlineSmall = display(FontWeight(600), 20),
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

/** Variante à chiffres tabulaires — dates, tranches horaires, comptes de devoirs. */
val tabularDigits = TextStyle(
    fontFamily = PublicSansFamily,
    fontFeatureSettings = "tnum",
)
