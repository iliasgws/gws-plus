package school.greenwood.plus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import school.greenwood.plus.model.Post
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.htmlToPlainSingleLine

private val CouleurSignet = Color(0xFFFC942D)

/**
 * Carte d'une actualité (partagée entre le flux Actualités et le Registre).
 *
 * Affiche la catégorie, l'icône de signet (#fc942d) si présent, le titre,
 * la date de publication, le badge de lecture (« Vu le … » issu de `intro`),
 * l'auteur si présent, et la vignette d'image.
 */
@Composable
fun CarteActualité(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GwsCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    post.categorie?.takeIf { it.isNotBlank() }?.let { categorie ->
                        Puce(label = categorie)
                    }
                    if (post.bookmark) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = "Signet",
                            tint = CouleurSignet,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                post.date?.let { date ->
                    Text(
                        text = date.frenchFull(),
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                post.intro?.takeIf { it.isNotBlank() }?.let { intro ->
                    Text(
                        text = intro.htmlToPlainSingleLine(),
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                post.auteur?.takeIf { it.isNotBlank() }?.let { auteur ->
                    Text(
                        text = "Par $auteur",
                        style = MaterialTheme.typography.labelSmall,
                        color = RegistreTheme.colors.chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            post.image?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(ControlShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
