package school.greenwood.plus.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

/*
 * Mouvement « École vivante » (docs/product/DESIGN.md §2) : le ressort est la
 * voix par défaut — la sélection rebondit, l'entrée glisse. Un seul fichier
 * pour que la sensation reste cohérente dans toute l'app ; la règle du §2
 * tient toujours : motion avec cause, jamais par tic.
 */

/** Sélection et presse : un rebond contrôlé. */
val RessortVif = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** Apparitions discrètes (capsule, échelle) : la même main, sans rebond. */
val RessortDoux = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** Changements de couleur (accent d'onglet, sélection de puce). */
val FonduCouleur = tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)
