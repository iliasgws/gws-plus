package school.greenwood.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Premier lancement (docs/product/DESIGN.md §4) : trois pages sobres, pas de carrousel
 * infini. Pas d'illustration — l'app se décrit en trois phrases.
 */
@Composable
fun OnboardingScreen(onFini: () -> Unit) {
    val pages = listOf(
        "Le registre du jour" to
            "Actualités, devoirs, absences, messages : tout ce qui s'est passé " +
            "à l'école, lu de haut en bas comme un cahier.",
        "Ce soir" to
            "Ce qu'il faut préparer pour la prochaine rentrée, en un coup " +
            "d'œil. S'il n'y a rien, la carte reste tranquille.",
        "Le rouge, c'est l'action" to
            "Le stylo rouge signale uniquement ce qui attend une réponse : " +
            "un devoir non fait, une demande à relancer. Le reste, c'est de l'encre.",
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        TextButton(
            onClick = onFini,
                        modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Text(
                text = "Passer",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = pages[page].first,
                        style = MaterialTheme.typography.displayLarge,
                        color = RegistreTheme.colors.ink,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = pages[page].second,
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { index ->
                    val actif = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (actif) RegistreTheme.colors.ink
                                else RegistreTheme.colors.sage,
                            ),
                    )
                }
            }
            if (pagerState.currentPage == pages.lastIndex) {
                Button(
                    onClick = onFini,
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistreTheme.colors.ink,
                        contentColor = RegistreTheme.colors.page,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 60.dp),
                ) {
                    Text(
                        text = "Commencer",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                TextButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                                    ) {
                    Text(
                        text = "Suivant",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
