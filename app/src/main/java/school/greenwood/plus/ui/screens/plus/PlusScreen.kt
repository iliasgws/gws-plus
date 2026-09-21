package school.greenwood.plus.ui.screens.plus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import school.greenwood.plus.ui.components.EntréeCascade
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * L'onglet « Plus » (docs/product/DESIGN.md §3) : les sections secondaires,
 * hors micro-moments du matin. Chaque carte porte l'accent de sa destination
 * — comme les couvertures de cahiers par matière — l'actualités en ambre, la
 * boutique en sarcelle. L'accent de l'écran lui-même reste sarcelle.
 */

@Composable
fun PlusScreen(
    padding: PaddingValues,
    ouvrirActualités: () -> Unit,
    ouvrirBoutique: () -> Unit,
) {
    // La cascade ne rejoue qu'à la première ouverture de l'écran.
    val cascade = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        cascade.value = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Plus",
                style = MaterialTheme.typography.displayLarge,
                color = RegistreTheme.colors.ink,
            )
            // Le surligneur : le trait de l'onglet, sous le titre.
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(width = 56.dp, height = 5.dp)
                    .clip(AnnotationShape)
                    .background(RegistreTheme.accent.conteneur),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Les autres sections de l'école",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel(text = "Sections")
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            EntréeCascade(déclenché = cascade.value, index = 0) {
                CarteSection(
                    titre = "Actualités",
                    sousTitre = "Les affiches de l'école, au jour le jour",
                    icone = Icons.Rounded.Newspaper,
                    accentClé = "actualites",
                    onClick = ouvrirActualités,
                )
            }
            Spacer(Modifier.height(12.dp))
            EntréeCascade(déclenché = cascade.value, index = 1) {
                CarteSection(
                    titre = "Boutique de l'école",
                    sousTitre = "Uniformes et services, commandés depuis l'app",
                    icone = Icons.Rounded.ShoppingBag,
                    accentClé = "plus",
                    onClick = ouvrirBoutique,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Une section du menu : la carte portant l'accent de sa destination. */
@Composable
private fun CarteSection(
    titre: String,
    sousTitre: String,
    icone: ImageVector,
    accentClé: String,
    onClick: () -> Unit,
) {
    val accent = RegistreTheme.colors.accents[accentClé]
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(AnnotationShape)
                    .background(accent?.conteneur ?: RegistreTheme.colors.sage),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icone,
                    contentDescription = null,
                    tint = accent?.teinte ?: RegistreTheme.colors.ink,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titre,
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                )
                Text(
                    text = sousTitre,
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
        }
    }
}
