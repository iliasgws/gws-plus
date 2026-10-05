package school.greenwood.plus.ui.components

import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import coil3.compose.AsyncImage
import school.greenwood.plus.logic.GalerieImages

/**
 * L'image affichée dans le visualiseur (issue #109) : la liste complète à
 * défiler, l'index de l'image touchée et le titre éventuel.
 */
data class VisualisationImage(
    val images: List<String>,
    val index: Int,
    val titre: String? = null,
)

/**
 * L'image tenue en main par l'aperçu rapide (issue #109) : une URL, un titre,
 * et l'envie d'agrandir.
 */
data class AperçuImage(
    val url: String,
    val titre: String? = null,
)

/**
 * Les deux surfaces sont posées sur du noir : les icônes des barres système
 * passent en blanc, quel que soit le thème (clair ou sombre) et la version
 * d'Android — sous Android 11 on retombe sur le contrôleur d'androidx.
 */
private fun forcerIcônesClaires(vue: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Efface les bits « barre claire » : les icônes passent en blanc.
        vue.windowInsetsController?.setSystemBarsAppearance(
            0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    } else {
        @Suppress("DEPRECATION")
        ViewCompat.getWindowInsetsController(vue)?.let { contrôleur ->
            contrôleur.isAppearanceLightStatusBars = false
            contrôleur.isAppearanceLightNavigationBars = false
        }
    }
}

/*
 * Le visualiseur d'images (issue #109) — deux surfaces plein écran, chacune
 * sa propre boîte de dialogue, appelées conditionnellement par l'écran :
 *
 * - « VisualiseurImages » : le pager noir, bord à bord, avec pincement et
 *   glissement faits main (le balayage du pager ne reprend qu'à l'échelle 1,
 *   un seul doigt posé), la barre de fermeture en haut, la pastille de
 *   position et les états de chargement/échec. Le retour système referme tout
 *   — pas de `BackHandler`, la navigation arrière reste celle du système.
 * - « AperçuImageRapide » : l'aperçu d'appui long, sans zoom, avec « Fermer »
 *   et « Agrandir » (qui ouvre le visualiseur sur la même image).
 *
 * Le fond est noir en permanence et les icônes des barres sont forcées en
 * blanc, thème clair ou thème sombre.
 */

/**
 * Le visualiseur plein écran d'une galerie d'images (issue #109).
 *
 * @param images la liste déjà nettoyée par `GalerieImages.liste`, jamais vide ;
 * @param indexInitial l'index de l'image touchée, ramené dans les bornes ;
 * @param fermer referme la boîte de dialogue (bouton, ailleurs, retour système) ;
 * @param titre le titre du contenu, ajouté aux descriptions accessibles.
 */
@Composable
fun VisualiseurImages(
    images: List<String>,
    indexInitial: Int,
    fermer: () -> Unit,
    titre: String? = null,
) {
    if (images.isEmpty()) return

    Dialog(
        onDismissRequest = fermer,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Icônes des barres en blanc sur le fond noir, dans les deux thèmes.
        val vue = LocalView.current
        SideEffect {
            forcerIcônesClaires(vue)
        }

        // État par page, tenu hors du pager : il survit aux changements de
        // page tant que la boîte de dialogue est ouverte.
        val zooms = remember { mutableStateMapOf<Int, Float>() }
        val décalages = remember { mutableStateMapOf<Int, Offset>() }
        val origines = remember { mutableStateMapOf<Int, IntSize>() }
        // Vrai tant que deux doigts sont posés : le pager ne doit pas
        // s'emparer du pincement en vol.
        val gesteMultiple: MutableState<Boolean> = remember { mutableStateOf(false) }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val pagerState = rememberPagerState(
                initialPage = indexInitial.coerceIn(0, images.lastIndex),
            ) { images.size }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !gesteMultiple.value &&
                    (zooms[pagerState.currentPage] ?: 1f) <= 1f,
            ) { page ->
                var chargement by remember { mutableStateOf(true) }
                var erreur by remember { mutableStateOf(false) }
                val zoom = zooms.getOrPut(page) { 1f }
                val décalage = décalages.getOrPut(page) { Offset.Zero }

                Box(
                    Modifier
                        .fillMaxSize()
                        // Pincement et glissement écrits à la main : le
                        // detectTransformGestures avale les glissements et
                        // tuerait le balayage du pager.
                        .pointerInput(page) {
                            awaitEachGesture {
                                while (true) {
                                    val évènement = awaitPointerEvent()
                                    val pressés = évènement.changes.count { it.pressed }
                                    if (pressés == 0) {
                                        gesteMultiple.value = false
                                        break
                                    }
                                    val cadre = IntSize(size.width, size.height)
                                    val affiché = GalerieImages.tailleAffichée(
                                        image = origines[page] ?: IntSize.Zero,
                                        cadre = cadre,
                                    )
                                    val échelle = zooms[page] ?: 1f
                                    if (pressés >= 2) {
                                        val pincé = GalerieImages.zoomBorné(
                                            échelle * évènement.calculateZoom(),
                                        )
                                        val tiré = GalerieImages.décalageBorné(
                                            décalage = (décalages[page] ?: Offset.Zero) +
                                                évènement.calculatePan(),
                                            zoom = pincé,
                                            affiché = affiché,
                                            cadre = cadre,
                                        )
                                        zooms[page] = pincé
                                        décalages[page] = tiré
                                        gesteMultiple.value = true
                                        évènement.changes.forEach { changement ->
                                            if (changement.positionChanged()) {
                                                changement.consume()
                                            }
                                        }
                                    } else if (échelle > 1f) {
                                        // Un seul doigt sur une image agrandie :
                                        // on déplace, on ne laisse pas défiler.
                                        décalages[page] = GalerieImages.décalageBorné(
                                            décalage = (décalages[page] ?: Offset.Zero) +
                                                évènement.calculatePan(),
                                            zoom = échelle,
                                            affiché = affiché,
                                            cadre = cadre,
                                        )
                                        évènement.changes.forEach { changement ->
                                            if (changement.positionChanged()) {
                                                changement.consume()
                                            }
                                        }
                                    }
                                    // Image à sa taille d'origine, un seul
                                    // doigt : rien n'est consommé, le pager
                                    // fait défiler la galerie.
                                }
                            }
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Zoom autour du centre par défaut ; le décalage
                                // n'est pas amplifié par l'échelle, on l'applique
                                // donc tel quel.
                                scaleX = zoom
                                scaleY = zoom
                                translationX = décalage.x
                                translationY = décalage.y
                            },
                    ) {
                        AsyncImage(
                            model = images[page],
                            contentDescription = GalerieImages.descriptionImage(
                                index = page + 1,
                                total = images.size,
                                titre = titre,
                            ),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                            onLoading = { chargement = true },
                            onSuccess = { état ->
                                chargement = false
                                origines[page] = IntSize(
                                    état.result.image.width,
                                    état.result.image.height,
                                )
                            },
                            onError = {
                                chargement = false
                                erreur = true
                            },
                        )
                    }

                    if (erreur) {
                        Icon(
                            imageVector = Icons.Rounded.BrokenImage,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center),
                        )
                    } else if (chargement) {
                        GwsLoadingIndicator(
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            // La barre est dessinée après le pager : elle reste au-dessus.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = fermer) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Fermer",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.weight(1f))
                GalerieImages.libelléPosition(
                    index = pagerState.currentPage + 1,
                    total = images.size,
                )?.let { libellé ->
                    Text(
                        text = libellé,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .semantics {
                                contentDescription = GalerieImages.descriptionImage(
                                    index = pagerState.currentPage + 1,
                                    total = images.size,
                                    titre = titre,
                                )
                            },
                    )
                }
            }
        }
    }
}

/**
 * L'aperçu rapide d'une image, à l'appui long (issue #109) : un aperçu plein
 * écran sans zoom, refermable d'un doigt n'importe où, avec « Fermer » et
 * « Agrandir » — ce dernier ouvre le visualiseur sur la même image.
 *
 * @param url l'image à montrer ;
 * @param titre le titre du contenu, ajouté à la description accessible ;
 * @param agrandir ouvre le visualiseur plein écran ;
 * @param fermer referme l'aperçu.
 */
@Composable
fun AperçuImageRapide(
    url: String,
    titre: String? = null,
    agrandir: () -> Unit,
    fermer: () -> Unit,
) {
    Dialog(
        onDismissRequest = fermer,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Icônes des barres en blanc sur le fond noir, dans les deux thèmes.
        val vue = LocalView.current
        SideEffect {
            forcerIcônesClaires(vue)
        }

        var chargement by remember { mutableStateOf(true) }
        var erreur by remember { mutableStateOf(false) }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Appui n'importe où = fermer : les boutons gagnent, ils sont
                // des enfants et passent avant dans la passe principale.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { fermer() },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = GalerieImages.descriptionImage(
                            index = 1,
                            total = 1,
                            titre = titre,
                        ),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        onLoading = { chargement = true },
                        onSuccess = { chargement = false },
                        onError = {
                            chargement = false
                            erreur = true
                        },
                    )
                    if (erreur) {
                        Icon(
                            imageVector = Icons.Rounded.BrokenImage,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center),
                        )
                    } else if (chargement) {
                        GwsLoadingIndicator(
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        12.dp,
                        Alignment.CenterHorizontally,
                    ),
                ) {
                    TextButton(onClick = fermer) {
                        Text("Fermer", color = Color.White)
                    }
                    Button(
                        onClick = agrandir,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Text("Agrandir")
                    }
                }
            }
        }
    }
}
