package school.greenwood.plus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Use the space actually allocated to this pane, including enlarged text. */
@Composable
internal fun DispositionAdaptative(content: @Composable (largeurLisible: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        content(maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f))
    }
}

/** Keep one vertical viewport and stable card identities while columns reflow. */
internal fun <T> LazyListScope.cartesEnColonnes(
    éléments: List<T>,
    colonnes: Int,
    clé: (T) -> String,
    carte: @Composable (T) -> Unit,
) {
    items(éléments.chunked(colonnes), key = { ligne -> clé(ligne.first()) }) { ligne ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ligne.forEach { élément ->
                key(clé(élément)) {
                    Box(Modifier.weight(1f)) { carte(élément) }
                }
            }
            repeat(colonnes - ligne.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
