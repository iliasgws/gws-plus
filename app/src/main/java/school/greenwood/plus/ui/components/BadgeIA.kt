package school.greenwood.plus.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Le badge « Généré par IA » (issue #58) — un composant unique réutilisable
 * partout où un contenu est produit par l'IA (sorties du composeur de #56,
 * future synthèse de conversation de #57). Deux variantes :
 * - l'icône seule, compacte (carré arrondi « AI » + étincelle en bas à droite,
 *   redessinée proprement d'après le SVG de référence — le tracé vectorisé
 *   d'origine comptait des milliers de segments) ;
 * - le badge complet : puce arrondie icône + « Généré par IA ».
 * Dans les deux cas le dessin est teinté par le thème, jamais noir en dur.
 */

/** L'icône « carré arrondi AI + étincelle », teintable. */
val IcôneGénéréIA: ImageVector by lazy {
    ImageVector.Builder(
        name = "GénéréParIA",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 512f,
        viewportHeight = 512f,
    ).apply {
        // Le carré arrondi, au trait (pas de fond : l'icône reste lisible
        // sur n'importe quelle surface du thème).
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 30f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // Rect arrondi 56..456, coins ~110.
            moveTo(166f, 56f)
            lineTo(346f, 56f)
            curveTo(409f, 56f, 456f, 103f, 456f, 166f)
            lineTo(456f, 346f)
            curveTo(456f, 409f, 409f, 456f, 346f, 456f)
            lineTo(166f, 456f)
            curveTo(103f, 456f, 56f, 409f, 56f, 346f)
            lineTo(56f, 166f)
            curveTo(56f, 103f, 103f, 56f, 166f, 56f)
            close()
        }
        // Le « A » — chevron monoline.
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 30f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(160f, 340f)
            lineTo(218f, 172f)
            lineTo(276f, 340f)
            moveTo(184f, 282f)
            lineTo(252f, 282f)
        }
        // Le « I » — fût vertical.
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 30f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(330f, 172f)
            lineTo(330f, 340f)
        }
        // L'étincelle à quatre branches, en bas à droite — pleine.
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(392f, 330f)
            curveTo(398f, 364f, 420f, 386f, 454f, 392f)
            curveTo(420f, 398f, 398f, 420f, 392f, 454f)
            curveTo(386f, 420f, 364f, 398f, 330f, 392f)
            curveTo(364f, 386f, 386f, 364f, 392f, 330f)
            close()
        }
    }.build()
}

/** L'icône seule, compacte — teintée par le thème (ou une couleur passée). */
@Composable
fun PuceIcôneIA(
    modifier: Modifier = Modifier,
    teinte: Color = RegistreTheme.colors.ink,
) {
    Icon(
        imageVector = IcôneGénéréIA,
        contentDescription = "Généré par IA",
        tint = teinte,
        modifier = modifier.size(16.dp),
    )
}

/** Le badge complet : puce arrondie, icône + libellé « Généré par IA ». */
@Composable
fun BadgeGénéréIA(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        PuceIcôneIA(modifier = modifier)
        return
    }
    Surface(
        shape = AnnotationShape,
        color = RegistreTheme.colors.sage,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            PuceIcôneIA()
            Text(
                text = "Généré par IA",
                style = MaterialTheme.typography.labelSmall,
                color = RegistreTheme.colors.chalk,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
    }
}
