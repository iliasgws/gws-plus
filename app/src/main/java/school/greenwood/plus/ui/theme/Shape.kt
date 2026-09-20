package school.greenwood.plus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Forme « École vivante » (docs/product/DESIGN.md §2) : le rayon encode le
 * rang, plus généreux qu'avant — l'école vit. Pages (cartes de contenu) :
 * 24 dp. Champs et boutons : 16 dp. Annotations (puces, statuts) : 10 dp.
 * La pilule est réservée à la capsule de la barre basse.
 */

val PageShape = RoundedCornerShape(24.dp)
val ControlShape = RoundedCornerShape(16.dp)
val AnnotationShape = RoundedCornerShape(10.dp)

/** Capsule pleine — réservée à la sélection de la barre basse. */
val PiluleShape = RoundedCornerShape(50)

val GwsShapes = Shapes(
    extraSmall = AnnotationShape,
    small = AnnotationShape,
    medium = ControlShape,
    large = PageShape,
    extraLarge = RoundedCornerShape(28.dp),
)
