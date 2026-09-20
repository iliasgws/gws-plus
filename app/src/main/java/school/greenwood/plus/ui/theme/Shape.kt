package school.greenwood.plus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Forme (docs/product/DESIGN.md §2) : le langage des capsules. Chaque contrôle
 * (bouton, champ, puce, composeur, barre) est une capsule pleine ; le contenu
 * (cartes, bulles, feuilles) garde un rayon continu qui lui dit son rang.
 * Pages (cartes de contenu) : 24 dp. Feuilles modales : 28 dp. Bulles et
 * bandeaux : 18 dp. Contrôles : capsule (50 %).
 */

val PageShape = RoundedCornerShape(24.dp)
val SheetShape = RoundedCornerShape(28.dp)
val BubbleShape = RoundedCornerShape(18.dp)
val ControlShape = RoundedCornerShape(percent = 50)

val GwsShapes = Shapes(
    small = ControlShape,
    medium = ControlShape,
    large = PageShape,
)
