package school.greenwood.plus.ui.screens.registre

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import school.greenwood.plus.AppContainer
import school.greenwood.plus.data.repo.EntreeRegistre
import school.greenwood.plus.data.repo.TéléchargementMaj
import school.greenwood.plus.model.Absence
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Eleve
import school.greenwood.plus.ui.RegistreViewModel
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.CarteActualité
import school.greenwood.plus.ui.components.CarteMiseÀJour
import school.greenwood.plus.ui.components.DialogueDéconnexion
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsAvatar
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.components.SqueletteRegistre
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.GwsAccent
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.tabulaire
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.frenchNumeric
import school.greenwood.plus.util.frenchTime
import school.greenwood.plus.util.htmlToPlainSingleLine
import java.time.LocalDate

/*
 * Le registre du jour (docs/product/DESIGN.md §2) : un flux chronologique à lire de haut
 * en bas, avec une seule chose en avant — la carte « Ce soir ». Le reste est
 * plus discret. L'encre écrit ; le stylo rouge signale l'action requise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistreScreen(
    container: AppContainer,
    padding: PaddingValues,
    ouvrirDemandes: () -> Unit,
    ouvrirParamètres: () -> Unit,
    ouvrirRepas: () -> Unit,
    ouvrirPost: (String) -> Unit,
    ouvrirEmploi: () -> Unit,
    ouvrirDevoirs: () -> Unit,
    ouvrirActualités: () -> Unit,
    ouvrirDevoir: (String) -> Unit,
    ouvrirConversation: (String) -> Unit,
) {
    // Une seule instance de VM partagée entre le registre et le détail d'un
    // post (portée activité) : le détail hérite du registre déjà chargé.
    val activité = LocalContext.current as? ComponentActivity
    val vm: RegistreViewModel = if (activité != null) {
        viewModel(viewModelStoreOwner = activité) { RegistreViewModel(container) }
    } else {
        viewModel { RegistreViewModel(container) }
    }
    val état by vm.état.collectAsStateWithLifecycle()

    // Mise à jour de l'app (issue #46) : contrôle à chaque ouverture
    // (échec silencieux) et carte en tête du flux quand une publication
    // plus récente existe.
    val majÉtat by container.misesÀJour.état.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.misesÀJour.vérifierAuBesoin() }

    var feuilleOuverte by remember { mutableStateOf(false) }
    var apercu by remember { mutableStateOf<ApercuOuverte?>(null) }
    // Déconnexion (issue #101) : la demande n'agit qu'après confirmation —
    // un appui accidentel sur la feuille ne quitte jamais la session.
    var déconnexionDemandée by remember { mutableStateOf(false) }

    // Le menu du haut : les gestes « hors flux du jour » (demandes,
    // paramètres) vivent dans le tiroir, la liste du registre ne porte plus
    // que du contenu (docs/product/DESIGN.md §4).
    val portée = rememberCoroutineScope()
    val tiroir = rememberDrawerState(DrawerValue.Closed)
    val liste = rememberLazyListState()
    val hauteurBannièrePx = with(LocalDensity.current) { 140.dp.roundToPx() }
    val bannièreVisible = liste.firstVisibleItemIndex == 0 &&
        liste.firstVisibleItemScrollOffset < hauteurBannièrePx
    val pageClaire = RegistreTheme.colors.page.luminance() > 0.5f
    DisposableEffect(activité, tiroir.currentValue, pageClaire, bannièreVisible) {
        val contrôleur = activité?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val apparencePrécédente = contrôleur?.isAppearanceLightStatusBars
        contrôleur?.isAppearanceLightStatusBars =
            (tiroir.currentValue == DrawerValue.Open || !bannièreVisible) && pageClaire
        onDispose {
            if (apparencePrécédente != null) {
                contrôleur.isAppearanceLightStatusBars = apparencePrécédente
            }
        }
    }
    val paddingContenu = PaddingValues(top = 140.dp, bottom = padding.calculateBottomPadding())
    val actionDepuisTiroir: (() -> Unit) -> Unit = { action ->
        portée.launch { tiroir.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = tiroir,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = RegistreTheme.colors.page) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "Menu",
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                LigneTiroir(
                    label = "Mes demandes",
                    sousTitre = "Suivre et relancer",
                    icone = Icons.Rounded.Description,
                    accent = RegistreTheme.colors.accents["messages"],
                    onClick = { actionDepuisTiroir(ouvrirDemandes) },
                )
                LigneTiroir(
                    label = "Paramètres",
                    sousTitre = "Actualisation au retour",
                    icone = Icons.Rounded.Settings,
                    accent = RegistreTheme.colors.accents["registre"],
                    onClick = { actionDepuisTiroir(ouvrirParamètres) },
                )
            }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(RegistreTheme.colors.paper)) {
        if (bannièreVisible) {
            BanniereRegistre(
                hauteurBarreÉtat = padding.calculateTopPadding(),
                modifier = Modifier.offset { IntOffset(0, -liste.firstVisibleItemScrollOffset) },
            )
        }
        // L'actualisation se fait au geste (issue #104) : plus de bouton dans
        // l'en-tête. Le geste reprend exactement les deux effets de l'ancien
        // bouton — le registre, puis le contrôle silencieux des mises à jour
        // (échec muet, jamais de blocage).
        PullToRefreshBox(
            isRefreshing = état.rafraîchissement,
            onRefresh = {
                vm.rafraîchir()
                portée.launch { container.misesÀJour.vérifier(manuel = false) }
            },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
        when {
        état.registre == null && état.erreur != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingContenu),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ErrorInline(message = état.erreur ?: "")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.charger(force = true) },
                        shape = ControlShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RegistreTheme.colors.ink,
                            contentColor = RegistreTheme.colors.page,
                        ),
                    ) {
                        Text("Réessayer", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        état.registre == null -> {
            // Premier chargement : le registre se dessine déjà, en blocs pulsés.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingContenu),
            ) {
                SqueletteRegistre()
            }
        }

        else -> {
            val registre = état.registre
            if (registre != null) {
            // La cascade ne joue qu'à la première ouverture de l'accueil
            // (docs/product/DESIGN.md §2) — survive aux changements d'onglet.
            val cascade = rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(40)
                cascade.value = true
            }

            val idPostsAujourdhui = remember(registre.entrees) {
                registre.entrees.filterIsInstance<EntreeRegistre.Actualite>().map { it.post.id }.toSet()
            }
            val derniereActu = état.derniereActualite?.takeIf { it.id !in idPostsAujourdhui }

            LazyColumn(
                state = liste,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 140.dp + 12.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "entete") {
                    EnTêteRegistre(
                        date = LocalDate.now(),
                        eleve = état.eleve,
                        surOuvrirTiroir = {
                            portée.launch { tiroir.open() }
                        },
                        surOuvrirFeuille = { feuilleOuverte = true },
                    )
                }

                // Erreur non bloquante (issue #21) : le contenu connu reste
                // affiché, l'échec se pose en annotation au-dessus de lui.
                état.erreur?.let { message ->
                    item(key = "erreur") {
                        BandeauErreur(
                            message = message,
                            réessayer = { vm.charger(force = true) },
                        )
                    }
                }

                // Mise à jour de l'app (issue #46) — visible tant qu'une
                // publication plus récente attend, ou qu'un téléchargement
                // est en cours.
                if (majÉtat.disponible != null || majÉtat.téléchargement != TéléchargementMaj.Inactif) {
                    item(key = "mise-a-jour") {
                        CarteMiseÀJour(
                            état = majÉtat,
                            peutInstaller = container.misesÀJour.peutInstaller(),
                            surMettreÀJour = {
                                portée.launch { container.misesÀJour.mettreÀJour() }
                            },
                            surInstaller = { container.misesÀJour.relancerInstallation() },
                        )
                    }
                }

                item(key = "ce-soir") {
                    CarteCeSoir(registre = registre, ouvrirDevoirs = ouvrirDevoirs)
                }

                if (derniereActu != null) {
                    item(key = "derniere-actu") {
                        SectionActualitéUne(
                            post = derniereActu,
                            ouvrirPost = ouvrirPost,
                            ouvrirActualités = ouvrirActualités,
                            surApercu = {
                                apercu = ApercuOuverte(
                                    contenu = apercuDe(derniereActu),
                                    action = ActionComplète("Lire l'actualité") {
                                        ouvrirPost(derniereActu.id)
                                    },
                                )
                            },
                        )
                    }
                }

                item(key = "acces-rapides") {
                    SectionAccèsRapides(ouvrirEmploi = ouvrirEmploi, ouvrirRepas = ouvrirRepas)
                }

                item(key = "label-jour") { SectionLabel(text = "Aujourd'hui") }

                if (registre.entrees.isEmpty() && registre.ceSoir.isEmpty()) {
                    item(key = "vide") {
                        EmptyState(
                            titre = "Un jour tranquille",
                            message = "Aucune nouvelle de l'école aujourd'hui.",
                        )
                    }
                }

                itemsIndexed(registre.entrees, key = { _, entrée -> entrée.id }) { index, entrée ->
                    val délai = (index * 40).coerceAtMost(400)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = cascade.value,
                        enter = fadeIn(tween(240, delayMillis = délai)) +
                            slideInVertically(tween(280, delayMillis = délai)) { it / 6 },
                    ) {
                        CarteEntrée(
                            entrée = entrée,
                            ouvrirPost = ouvrirPost,
                            ouvrirDevoir = ouvrirDevoir,
                            ouvrirConversation = ouvrirConversation,
                            afficherApercu = { apercu = it },
                        )
                    }
                }
            }
        }
    }
    }
        }

        }

    if (feuilleOuverte) {
        FeuilleCompte(
            eleves = état.eleves,
            sélection = état.eleve,
            déconnexionEnCours = état.déconnexionEnCours,
            surChoix = vm::choisirEleve,
            surOuvrirParamètres = {
                feuilleOuverte = false
                ouvrirParamètres()
            },
            surDemanderDéconnexion = { déconnexionDemandée = true },
            surFermer = { feuilleOuverte = false },
        )
    }

    apercu?.let { demande ->
        FeuilleApercu(
            demande = demande,
            surFermer = { apercu = null },
        )
    }

    if (déconnexionDemandée) {
        DialogueDéconnexion(
            enCours = état.déconnexionEnCours,
            onConfirmer = { vm.déconnexion() },
            onAnnuler = { déconnexionDemandée = false },
        )
    }
    }
}

@Composable
private fun CarteEntrée(
    entrée: EntreeRegistre,
    ouvrirPost: (String) -> Unit,
    ouvrirDevoir: (String) -> Unit,
    ouvrirConversation: (String) -> Unit,
    afficherApercu: (ApercuOuverte) -> Unit,
) {
    when (entrée) {
        is EntreeRegistre.Actualite -> CarteActualité(
            entrée.post,
            onClick = { ouvrirPost(entrée.post.id) },
            onLongClick = {
                afficherApercu(
                    ApercuOuverte(
                        contenu = apercuDe(entrée.post),
                        action = ActionComplète("Lire l'actualité") { ouvrirPost(entrée.post.id) },
                    ),
                )
            },
        )
        is EntreeRegistre.DevoirDonné -> CarteDevoirDonné(
            entrée.devoir,
            onLongPress = {
                afficherApercu(
                    ApercuOuverte(
                        contenu = apercuDe(entrée),
                        action = ActionComplète("Ouvrir le devoir") { ouvrirDevoir(entrée.devoir.id) },
                    ),
                )
            },
        )
        is EntreeRegistre.AbsenceNotée -> CarteAbsence(
            entrée.absence,
            onLongPress = { afficherApercu(ApercuOuverte(apercuDe(entrée))) },
        )
        is EntreeRegistre.MessageReçu -> CarteMessage(
            entrée.conversation,
            onLongPress = {
                afficherApercu(
                    ApercuOuverte(
                        contenu = apercuDe(entrée),
                        action = ActionComplète("Ouvrir la conversation") {
                            ouvrirConversation(entrée.conversation.id)
                        },
                    ),
                )
            },
        )
    }
}

@Composable
private fun Modifier.appuiLong(onLongPress: (() -> Unit)?): Modifier {
    if (onLongPress == null) return this
    val haptique = LocalHapticFeedback.current
    val action by rememberUpdatedState(onLongPress)
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = {
                    haptique.performHapticFeedback(HapticFeedbackType.LongPress)
                    action()
                },
            )
        }
        .semantics(mergeDescendants = true) {
            onLongClick("Afficher l'aperçu rapide") {
                haptique.performHapticFeedback(HapticFeedbackType.LongPress)
                action()
                true
            }
        }
}

@Composable
private fun CarteDevoirDonné(devoir: Devoir, onLongPress: (() -> Unit)? = null) {
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .appuiLong(onLongPress),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Puce(label = "Devoir")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = devoir.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = devoir.matiere,
                    style = MaterialTheme.typography.labelSmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
            devoir.publication?.let { publication ->
                Text(
                    text = publication.frenchTime(),
                    style = MaterialTheme.typography.bodySmall.tabulaire(),
                    color = RegistreTheme.colors.chalk,
                )
            }
        }
    }
}

@Composable
private fun CarteAbsence(absence: Absence, onLongPress: (() -> Unit)? = null) {
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .appuiLong(onLongPress),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Non justifiée = action requise : la puce passe au stylo rouge.
            Puce(label = "Absence", tintRed = !absence.justifiee)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = absence.motif ?: "Absence notée",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.ink,
                )
                val dates = buildList {
                    absence.du?.let { add(it.frenchNumeric()) }
                    absence.au?.takeIf { it != absence.du }?.let { add(it.frenchNumeric()) }
                }.joinToString(" → ")
                if (dates.isNotBlank()) {
                    Text(
                        text = dates,
                        style = MaterialTheme.typography.labelSmall.tabulaire(),
                        color = RegistreTheme.colors.chalk,
                    )
                }
            }
        }
    }
}

@Composable
private fun CarteMessage(conversation: Conversation, onLongPress: (() -> Unit)? = null) {
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .appuiLong(onLongPress),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Puce(label = "Message")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.sujet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                conversation.dernierDate?.let { date ->
                    Text(
                        text = "Reçu ${date.frenchFull()}",
                        style = MaterialTheme.typography.labelMedium.tabulaire(),
                        color = RegistreTheme.colors.ink,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                conversation.messages.lastOrNull()?.let { dernier ->
                    Text(
                        text = dernier.texte.htmlToPlainSingleLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Une ligne du menu du haut : icône sur l'accent de sa destination, sous-titre. */
@Composable
private fun LigneTiroir(
    label: String,
    sousTitre: String,
    icone: ImageVector,
    accent: GwsAccent?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(AnnotationShape)
                .background(accent?.conteneur ?: RegistreTheme.colors.sage),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = accent?.teinte ?: RegistreTheme.colors.ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = RegistreTheme.colors.ink,
            )
            Text(
                text = sousTitre,
                style = MaterialTheme.typography.labelSmall,
                color = RegistreTheme.colors.chalk,
            )
        }
    }
}

/**
 * « Mon compte » (issue #101) : l'élève consulté, le basculement d'enfant,
 * l'accès aux paramètres — puis, après un séparateur et toujours en bas,
 * la déconnexion. La feuille n'embarque aucune navigation par elle-même :
 * ouvrir les paramètres ferme la feuille avant de pousser l'écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleCompte(
    eleves: List<Eleve>,
    sélection: Eleve?,
    déconnexionEnCours: Boolean,
    surChoix: (Eleve) -> Unit,
    surOuvrirParamètres: () -> Unit,
    surDemanderDéconnexion: () -> Unit,
    surFermer: () -> Unit,
) {
    val feuille = rememberModalBottomSheetState()
    val portée = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = surFermer,
        sheetState = feuille,
        containerColor = RegistreTheme.colors.page,
        shape = PageShape,
    ) {
        // Défilable : sur petit écran ou à grosse police, « Se déconnecter »
        // reste atteignable sous le bas de la feuille (jamais masqué).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Mon compte",
                style = MaterialTheme.typography.titleMedium,
                color = RegistreTheme.colors.ink,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            // L'en-tête : l'élève courant, tel qu'il est déjà montré en haut
            // de l'écran (avatar, nom complet, classe) — jamais d'une autre
            // source que la session.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GwsAvatar(
                    initiales = sélection?.initiales ?: "·",
                    imageUrl = sélection?.image,
                    size = 44,
                )
                Column {
                    Text(
                        text = sélection?.nomComplet ?: "Enfant",
                        style = MaterialTheme.typography.bodyLarge,
                        color = RegistreTheme.colors.ink,
                    )
                    sélection?.niveau?.let { niveau ->
                        Text(
                            text = niveau,
                            style = MaterialTheme.typography.labelSmall,
                            color = RegistreTheme.colors.chalk,
                        )
                    }
                }
            }

            Text(
                text = "Changer d'enfant",
                style = MaterialTheme.typography.labelMedium,
                color = RegistreTheme.colors.chalk,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            )
            eleves.forEach { eleve ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(ControlShape)
                        .clickable {
                            surChoix(eleve)
                            portée.launch { feuille.hide() }.invokeOnCompletion { surFermer() }
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GwsAvatar(initiales = eleve.initiales, imageUrl = eleve.image, size = 36)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = eleve.nomComplet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RegistreTheme.colors.ink,
                        )
                        eleve.niveau?.let { niveau ->
                            Text(
                                text = niveau,
                                style = MaterialTheme.typography.labelSmall,
                                color = RegistreTheme.colors.chalk,
                            )
                        }
                    }
                    if (eleve.id == sélection?.id) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Enfant consulté",
                            tint = RegistreTheme.colors.ink,
                        )
                    }
                }
            }

            LigneCompte(
                label = "Paramètres",
                icone = Icons.Rounded.Settings,
                onClick = surOuvrirParamètres,
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = RegistreTheme.colors.sage,
            )

            // En bas, seul après le séparateur : désactivé pendant l'appel en
            // cours (aucun double envoi), encre et non rouge stylo.
            LigneCompte(
                label = if (déconnexionEnCours) "Déconnexion…" else "Se déconnecter",
                icone = Icons.Rounded.Logout,
                activé = !déconnexionEnCours,
                onClick = surDemanderDéconnexion,
            )
        }
    }
}

/** Une ligne de compte : icône + libellé, cible tactile d'au moins 48 dp. */
@Composable
private fun LigneCompte(
    label: String,
    icone: ImageVector,
    activé: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ControlShape)
            .clickable(enabled = activé, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = if (activé) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (activé) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
        )
    }
}
