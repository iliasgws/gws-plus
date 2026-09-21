package school.greenwood.plus.ui.screens.registre

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import school.greenwood.plus.AppContainer
import school.greenwood.plus.data.repo.EntreeRegistre
import school.greenwood.plus.data.repo.RegistreDuJour
import school.greenwood.plus.model.Absence
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Eleve
import school.greenwood.plus.model.Post
import school.greenwood.plus.ui.RegistreViewModel
import school.greenwood.plus.ui.components.BandeauErreur
import school.greenwood.plus.ui.components.CarteActualité
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
import school.greenwood.plus.util.frenchLongDay
import school.greenwood.plus.util.frenchNumeric
import school.greenwood.plus.util.frenchTime
import school.greenwood.plus.util.htmlToPlainSingleLine
import java.time.LocalDate

/*
 * Le registre du jour (docs/product/DESIGN.md §2) : un flux chronologique à lire de haut
 * en bas, avec une seule chose en avant — la carte « Ce soir ». Le reste est
 * plus discret. L'encre écrit ; le stylo rouge signale l'action requise.
 */
@Composable
fun RegistreScreen(
    container: AppContainer,
    padding: PaddingValues,
    ouvrirDemandes: () -> Unit,
    ouvrirParamètres: () -> Unit,
    ouvrirPost: (String) -> Unit,
    ouvrirEmploi: () -> Unit,
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

    var feuilleOuverte by remember { mutableStateOf(false) }

    // Le menu du haut : les gestes « hors flux du jour » (demandes,
    // paramètres) vivent dans le tiroir, la liste du registre ne porte plus
    // que du contenu (docs/product/DESIGN.md §4).
    val portée = rememberCoroutineScope()
    val tiroir = rememberDrawerState(DrawerValue.Closed)
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
        when {
        état.registre == null && état.erreur != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RegistreTheme.colors.paper)
                    .padding(padding),
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
                    .background(RegistreTheme.colors.paper)
                    .padding(padding),
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(RegistreTheme.colors.paper),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "entete") {
                    EnTête(
                        date = LocalDate.now(),
                        eleve = état.eleve,
                        surOuvrirTiroir = {
                            portée.launch { tiroir.open() }
                        },
                        surOuvrirFeuille = { feuilleOuverte = true },
                        surActualiser = vm::charger,
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

                item(key = "ce-soir") { CarteCeSoir(registre) }

                if (derniereActu != null) {
                    item(key = "derniere-actu") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SectionLabel(text = "Dernière actualité")
                            CarteActualité(
                                post = derniereActu,
                                onClick = { ouvrirPost(derniereActu.id) },
                            )
                        }
                    }
                }

                item(key = "lien-emploi") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel(text = "Emploi du temps")
                        CarteLienEmploi(ouvrirEmploi)
                    }
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
                    AnimatedVisibility(
                        visible = cascade.value,
                        enter = fadeIn(tween(240, delayMillis = délai)) +
                            slideInVertically(tween(280, delayMillis = délai)) { it / 6 },
                    ) {
                        CarteEntrée(entrée, ouvrirPost)
                    }
                }
            }
        }
    }
    }

    if (feuilleOuverte) {
        FeuilleEleves(
            eleves = état.eleves,
            sélection = état.eleve,
            surChoix = vm::choisirEleve,
            surFermer = { feuilleOuverte = false },
        )
    }
    }
}

/** En-tête : le menu, la date en Bricolage, l'enfant consulté à droite, actualiser. */
@Composable
private fun EnTête(
    date: LocalDate,
    eleve: Eleve?,
    surOuvrirTiroir: () -> Unit,
    surOuvrirFeuille: () -> Unit,
    surActualiser: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = surOuvrirTiroir) {
            Icon(
                imageVector = Icons.Rounded.Menu,
                contentDescription = "Menu",
                tint = RegistreTheme.colors.ink,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            // La date doit rester lisible en entier (« jeudi 18 septembre ») :
            // elle passe sur deux lignes plutôt que de se couper (issue #21) —
            // la coupure n'arrive qu'en toute dernière extrémité.
            Text(
                text = date.frenchLongDay(),
                style = MaterialTheme.typography.displayLarge,
                color = RegistreTheme.colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Le surligneur : le trait de l'onglet, sous le titre.
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(width = 56.dp, height = 5.dp)
                    .clip(AnnotationShape)
                    .background(RegistreTheme.accent.conteneur),
            )
        }
        IconButton(onClick = surActualiser) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Actualiser",
                tint = RegistreTheme.colors.chalk,
            )
        }
        // Bascule d'enfant : fondu (docs/product/DESIGN.md §4).
        AnimatedContent(
            targetState = eleve,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "élève consulté",
        ) { enfant ->
            Row(
                modifier = Modifier
                    .clip(ControlShape)
                    .clickable { surOuvrirFeuille() }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GwsAvatar(
                    initiales = enfant?.initiales ?: "·",
                    imageUrl = enfant?.image,
                    size = 32,
                )
                Column {
                    Text(
                        text = enfant?.prenom ?: enfant?.nomComplet ?: "Enfant",
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.ink,
                        maxLines = 1,
                    )
                    enfant?.niveau?.let { niveau ->
                        Text(
                            text = niveau,
                            style = MaterialTheme.typography.labelSmall,
                            color = RegistreTheme.colors.chalk,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Lien discret vers l'onglet Emploi du temps, depuis l'accueil — habillé
 *  de l'accent bleu de l'onglet Cours. */
@Composable
private fun CarteLienEmploi(ouvrirEmploi: () -> Unit) {
    val accentCours = RegistreTheme.colors.accents["cours"]
    GwsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = ouvrirEmploi),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(ControlShape)
                    .background(accentCours?.conteneur ?: RegistreTheme.colors.sage),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = accentCours?.teinte ?: RegistreTheme.colors.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Emploi du temps",
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                )
                Text(
                    text = "La semaine de l'école, jour par jour",
                    style = MaterialTheme.typography.labelSmall,
                    color = RegistreTheme.colors.chalk,
                )
            }
        }
    }
}

/**
 * La carte focale (docs/product/DESIGN.md §2) : fond de l'accent vert du
 * Registre, liseré assorti. Le rouge n'y apparaît que sur les devoirs pas
 * encore faits — l'action requise.
 */
@Composable
private fun CarteCeSoir(registre: RegistreDuJour) {
    val accent = RegistreTheme.accent
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PageShape,
        color = accent.conteneur,
        border = BorderStroke(1.5.dp, accent.teinte),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = "Ce soir",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent.surConteneur,
                )
                val demain = registre.date.plusDays(1)
                val sousTitre = if (registre.horizonCeSoir == demain) {
                    "à rendre pour demain"
                } else {
                    "à rendre pour ${registre.horizonCeSoir.frenchLongDay()}"
                }
                Text(
                    text = sousTitre,
                    style = MaterialTheme.typography.labelMedium,
                    color = RegistreTheme.colors.chalk,
                )
            }

            if (registre.ceSoir.isEmpty()) {
                // Le vide est une bonne nouvelle, pas un manque.
                Text(
                    text = "Rien pour demain",
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent.surConteneur,
                )
                Text(
                    text = "La rentrée prochaine est tranquille.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
            } else {
                registre.ceSoir.forEach { devoir ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Puce(label = devoir.matiere.ifBlank { "Devoir" })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = devoir.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = RegistreTheme.colors.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            devoir.enseignant?.let { enseignant ->
                                Text(
                                    text = enseignant,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RegistreTheme.colors.chalk,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (devoir.fait) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Fait",
                                tint = accent.teinte,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(RegistreTheme.colors.redPen),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteEntrée(entrée: EntreeRegistre, ouvrirPost: (String) -> Unit) {
    when (entrée) {
        is EntreeRegistre.Actualite -> CarteActualité(entrée.post, onClick = { ouvrirPost(entrée.post.id) })
        is EntreeRegistre.DevoirDonné -> CarteDevoirDonné(entrée.devoir)
        is EntreeRegistre.AbsenceNotée -> CarteAbsence(entrée.absence)
        is EntreeRegistre.MessageReçu -> CarteMessage(entrée.conversation)
    }
}

@Composable
private fun CarteDevoirDonné(devoir: Devoir) {
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
private fun CarteAbsence(absence: Absence) {
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
private fun CarteMessage(conversation: Conversation) {
    GwsCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Puce(label = "Message")
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = conversation.sujet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                conversation.messages.lastOrNull()?.let { dernier ->
                    Text(
                        text = dernier.texte.htmlToPlainSingleLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                conversation.dernierDate?.let { date ->
                    Text(
                        text = date.frenchFull(),
                        style = MaterialTheme.typography.bodySmall,
                        color = RegistreTheme.colors.chalk,
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

/** Bascule d'enfant : la liste des élèves du compte parent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleEleves(
    eleves: List<Eleve>,
    sélection: Eleve?,
    surChoix: (Eleve) -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Changer d'enfant",
                style = MaterialTheme.typography.titleMedium,
                color = RegistreTheme.colors.ink,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            eleves.forEach { eleve ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
        }
    }
}
