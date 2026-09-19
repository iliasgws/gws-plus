package school.greenwood.plus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Forme (docs/product/DESIGN.md §2) : le rayon encode le rang, pas la décoration.
 * Pages (cartes de contenu) : 20 dp. Champs et boutons : 12 dp.
 * Annotations (puces, statuts, compteurs) : 6 dp.
 */

val PageShape = RoundedCornerShape(20.dp)
val ControlShape = RoundedCornerShape(12.dp)
val AnnotationShape = RoundedCornerShape(6.dp)

val GwsShapes = Shapes(
    small = AnnotationShape,
    medium = ControlShape,
    large = PageShape,
)
