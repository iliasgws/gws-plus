package school.greenwood.plus.ui.screens.registre

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import school.greenwood.plus.AppContainer
import school.greenwood.plus.data.repo.EntreeRegistre
import school.greenwood.plus.ui.RegistreViewModel
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.htmlToPlainMultiline

/*
 * Détail d'une actualité. Le corps HTML ne vient pas de la liste `nouveautes`
 * mais de admin_nouveautes, extrait en streaming par le dépôt (~19 Mo) —
 * d'où l'état « corps en cours de récupération ».
 */
@Composable
fun PostDetailScreen(
    container: AppContainer,
    padding: PaddingValues,
    postId: String,
    retour: () -> Unit,
) {
    // Même instance du VM que le registre (portée activité) : le post et son
    // contexte arrivent sans nouvel appel.
    val activité = LocalContext.current as? ComponentActivity
    val vm: RegistreViewModel = if (activité != null) {
        viewModel(viewModelStoreOwner = activité) { RegistreViewModel(container) }
    } else {
        viewModel { RegistreViewModel(container) }
    }
    val état by vm.état.collectAsStateWithLifecycle()

    var corps by remember { mutableStateOf<String?>(null) }
    var corpsChargé by remember { mutableStateOf(false) }
    LaunchedEffect(postId) {
        vm.corpsPost(postId) { résultat ->
            corps = résultat
            corpsChargé = true
        }
    }

    val post = état.registre?.entrees
        ?.filterIsInstance<EntreeRegistre.Actualite>()
        ?.firstOrNull { it.post.id == postId }
        ?.post

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row {
            IconButton(onClick = retour) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Retour",
                    tint = RegistreTheme.colors.ink,
                )
            }
        }

        GwsCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                post?.categorie?.takeIf { it.isNotBlank() }?.let { categorie ->
                    Puce(label = categorie)
                }
                Text(
                    text = post?.title ?: "Actualité",
                    style = MaterialTheme.typography.headlineMedium,
                    color = RegistreTheme.colors.ink,
                )
                post?.date?.let { date ->
                    Text(
                        text = date.frenchFull(),
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }
                post?.image?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(PageShape),
                        contentScale = ContentScale.Crop,
                    )
                }

                Spacer(Modifier.size(2.dp))

                val brut = post?.description ?: corps
                when {
                    brut != null -> Text(
                        text = brut.htmlToPlainMultiline(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RegistreTheme.colors.ink,
                    )
                    !corpsChargé -> Text(
                        text = "Corps en cours de récupération…",
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                    else -> Text(
                        text = "Le corps de cette annonce n'est pas disponible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                    )
                }

                post?.attachments?.forEach { pièce ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = RegistreTheme.colors.ink,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = pièce.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = RegistreTheme.colors.ink,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.size(16.dp))
    }
}
