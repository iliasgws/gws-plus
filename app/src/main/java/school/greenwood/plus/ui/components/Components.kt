package school.greenwood.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Briques communes du registre. Discrètes : le rouge stylo n'apparaît que sur
 * l'action requise, le sage porte les annotations, les pages portent le contenu.
 */

/** Surface « page » — la carte de contenu standard, rayon 20 dp. */
@Composable
fun GwsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = PageShape,
        color = RegistreTheme.colors.page,
        content = { content() },
    )
}

/** Puce d'annotation (matière, statut, compteur) — rayon 6 dp, fond sage. */
@Composable
fun Puce(
    label: String,
    modifier: Modifier = Modifier,
    tintRed: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(AnnotationShape)
            .background(if (tintRed) RegistreTheme.colors.redPen else RegistreTheme.colors.sage)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (tintRed) RegistreTheme.colors.page else RegistreTheme.colors.ink,
        )
    }
}

/** Libellé de section — 13 sp, encre craie, discret. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight(600),
        color = RegistreTheme.colors.chalk,
    )
}

/** État vide : une invitation, pas un manque (docs/product/DESIGN.md §2). */
@Composable
fun EmptyState(
    titre: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = titre,
            style = MaterialTheme.typography.titleMedium,
            color = RegistreTheme.colors.ink,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.chalk,
        )
    }
}

/** Erreur en ligne : pictogramme + texte, teinte stylo rouge, jamais de fond rouge. */
@Composable
fun ErrorInline(
    message: String,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icone != null) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = RegistreTheme.colors.redPen,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.redPen,
        )
    }
}

/** Avatar rond de l'élève : image signée si présente, sinon initiales sur sage. */
@Composable
fun GwsAvatar(
    initiales: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Int = 36,
) {
    if (imageUrl != null) {
        coil3.compose.AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(RegistreTheme.colors.sage),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initiales,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight(600),
                color = RegistreTheme.colors.ink,
            )
        }
    }
}
