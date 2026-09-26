package school.greenwood.plus.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.api.BotiHttp
import school.greenwood.plus.data.ai.ActionIA
import school.greenwood.plus.data.ai.ComposeurIA
import school.greenwood.plus.data.ai.RéglagesIA
import school.greenwood.plus.data.ai.TonIA
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.data.repo.AuthRepository
import school.greenwood.plus.data.repo.BoutiqueRepository
import school.greenwood.plus.data.repo.CatalogueBoutique
import school.greenwood.plus.data.repo.CoursRepository
import school.greenwood.plus.data.repo.DemandesRepository
import school.greenwood.plus.data.repo.DevoirsRepository
import school.greenwood.plus.data.repo.DocumentsRepository
import school.greenwood.plus.data.repo.EntreeRegistre
import school.greenwood.plus.data.repo.MessagesRepository
import school.greenwood.plus.data.repo.NouveautesRepository
import school.greenwood.plus.data.repo.RegistreDuJour
import school.greenwood.plus.data.repo.RegistreRepository
import school.greenwood.plus.data.repo.SensSemaine
import school.greenwood.plus.data.session.SessionState
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.CommandeBoutique
import school.greenwood.plus.model.Demande
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.DevoirDétail
import school.greenwood.plus.model.FicheBibliotheque
import school.greenwood.plus.model.FicheBibliothequeDetail
import school.greenwood.plus.model.Post
import school.greenwood.plus.model.PostDetail
import school.greenwood.plus.model.ProduitDétail
import school.greenwood.plus.model.QuizReponse
import school.greenwood.plus.model.RéponseJouée
import school.greenwood.plus.model.Ressource
import school.greenwood.plus.model.SemaineCours
import school.greenwood.plus.model.ThemeMessage
import school.greenwood.plus.model.UniteBibliotheque
import java.io.File
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import javax.swing.JFileChooser
import okhttp3.Request
import org.jsoup.Jsoup

private class DesktopContainer {
    private val préférences = PreferenceDataStoreFactory.create(produceFile = {
        val racine = File(System.getenv("XDG_CONFIG_HOME") ?: File(System.getProperty("user.home"), ".config").path, "gws-plus")
        racine.mkdirs()
        Files.setPosixFilePermissions(racine.toPath(), setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
        ))
        File(racine, "session.preferences_pb")
    })
    val session = SessionStore(préférences)
    private val client = BotiClient(BotiHttp.api(), session)
    private val caches = CachesSession(session)
    val auth = AuthRepository(client, session, caches)
    val registre = RegistreRepository(client, session, caches)
    val cours = CoursRepository(client, caches)
    val devoirs = DevoirsRepository(client, caches, session)
    val documents = DocumentsRepository(client, session, caches)
    val messages = MessagesRepository(client, session, caches)
    val demandes = DemandesRepository(client, caches)
    val actualités = NouveautesRepository(client, session, caches)
    val boutique = BoutiqueRepository(client, session)
}

fun main() = application {
    val container = remember { DesktopContainer() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Greenwood School +",
        state = WindowState(size = DpSize(1100.dp, 750.dp)),
    ) {
        val sombre = isSystemInDarkTheme()
        val palette = if (sombre) darkColorScheme(
            primary = Color(0xFFE9F0E6), onPrimary = Color(0xFF141B15), background = Color(0xFF141B15),
            onBackground = Color(0xFFE9F0E6), surface = Color(0xFF1D271E), onSurface = Color(0xFFE9F0E6),
            surfaceVariant = Color(0xFF262F24), onSurfaceVariant = Color(0xFF9AAB97),
            surfaceContainerLowest = Color(0xFF171E18), surfaceContainerLow = Color(0xFF1A231B),
            surfaceContainer = Color(0xFF212B21), surfaceContainerHigh = Color(0xFF252F25),
            surfaceContainerHighest = Color(0xFF2A3429), outline = Color(0xFF6F7B6D),
            outlineVariant = Color(0xFF39443A), error = Color(0xFFF0917F),
        ) else lightColorScheme(
            primary = Color(0xFF1F3324), onPrimary = Color.White, background = Color(0xFFF7F1E5),
            onBackground = Color(0xFF1F3324), surface = Color.White, onSurface = Color(0xFF1F3324),
            surfaceVariant = Color(0xFFEBE6D7), onSurfaceVariant = Color(0xFF5E6B5C),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBF7EE),
            surfaceContainer = Color(0xFFF5EEDF), surfaceContainerHigh = Color(0xFFEFE7D6),
            surfaceContainerHighest = Color(0xFFE9E0CD), outline = Color(0xFF949B8C),
            outlineVariant = Color(0xFFDDD5C2), error = Color(0xFFB3382A),
        )
        val formes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp))
        MaterialTheme(colorScheme = palette, shapes = formes) {
            Surface(Modifier.fillMaxSize(), color = palette.background, contentColor = palette.onBackground) {
                DesktopApp(container)
            }
        }
    }
}

@Composable
private fun DesktopApp(container: DesktopContainer) {
    var racine by remember { mutableStateOf<SessionState?>(null) }
    var chargement by remember { mutableStateOf(true) }
    LaunchedEffect(container) {
        launch { container.session.events.collect { container.session.effacer() } }
        container.session.state.collect {
            racine = it
            chargement = false
        }
    }
    when {
        chargement -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        racine == null -> Connexion(container)
        else -> Bureau(container, racine!!)
    }
}

@Composable
private fun Connexion(container: DesktopContainer) {
    var téléphone by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var information by remember { mutableStateOf<String?>(null) }
    var chargement by remember { mutableStateOf(false) }
    val portée = rememberCoroutineScope()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Greenwood School +", style = MaterialTheme.typography.headlineMedium)
            Text("Connexion à votre espace parent")
            OutlinedTextField(téléphone, { téléphone = it }, label = { Text("Téléphone") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(motDePasse, { motDePasse = it }, label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            information?.let { Text(it) }
            Button(
                onClick = {
                    chargement = true
                    erreur = null
                    portée.launch {
                        runCatching { withContext(Dispatchers.IO) { container.auth.connexion(téléphone, motDePasse, true) } }
                            .onFailure { erreur = it.message ?: "Connexion impossible" }
                        chargement = false
                    }
                },
                enabled = !chargement && téléphone.isNotBlank() && motDePasse.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (chargement) "Connexion…" else "Se connecter") }
            TextButton(onClick = {
                chargement = true
                portée.launch {
                    runCatching { withContext(Dispatchers.IO) { container.auth.motDePasseOublié(téléphone) } }
                        .onSuccess { information = "Demande de réinitialisation envoyée" }
                        .onFailure { erreur = it.message ?: "Demande impossible" }
                    chargement = false
                }
            }, enabled = téléphone.isNotBlank() && !chargement) { Text("Mot de passe oublié ?") }
        }
    }
}

private enum class Onglet(val titre: String) { Registre("Registre"), Cours("Cours"), Devoirs("Devoirs"), Documents("Documents"), Messages("Messages"), Plus("Plus") }

private sealed interface Détail {
    data class DevoirChoisi(val id: String) : Détail
    data class ConversationChoisie(val id: String) : Détail
    data class FicheChoisie(val id: String) : Détail
    data class PostChoisi(val id: String) : Détail
    data class QuizChoisi(val id: String) : Détail
    data class DemandeChoisie(val demande: Demande) : Détail
    data class ProduitChoisi(val id: String) : Détail
    data class CommandeChoisie(val commande: CommandeBoutique) : Détail
}

@Composable
private fun Bureau(container: DesktopContainer, session: SessionState) {
    var onglet by remember { mutableStateOf(Onglet.Registre) }
    var détail by remember { mutableStateOf<Détail?>(null) }
    var quizEnJeu by remember { mutableStateOf(false) }
    var actualisation by remember { mutableIntStateOf(0) }
    var chargement by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var registre by remember { mutableStateOf<RegistreDuJour?>(null) }
    var cours by remember { mutableStateOf<SemaineCours?>(null) }
    var devoirs by remember { mutableStateOf<List<Devoir>>(emptyList()) }
    var ressources by remember { mutableStateOf<List<Ressource>>(emptyList()) }
    var unités by remember { mutableStateOf<List<UniteBibliotheque>>(emptyList()) }
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var thèmes by remember { mutableStateOf<List<ThemeMessage>>(emptyList()) }
    var demandes by remember { mutableStateOf<List<Demande>>(emptyList()) }
    var actualités by remember { mutableStateOf<List<Post>>(emptyList()) }
    var suiteActualités by remember { mutableStateOf(true) }
    var chargementSuite by remember { mutableStateOf(false) }
    var catalogue by remember { mutableStateOf<CatalogueBoutique?>(null) }
    var commandes by remember { mutableStateOf<List<CommandeBoutique>>(emptyList()) }
    var nouveauMessage by remember { mutableStateOf(false) }
    var paramètres by remember { mutableStateOf(false) }
    val portée = rememberCoroutineScope()

    LaunchedEffect(onglet, actualisation, session.userId, session.eleveId) {
        chargement = true
        erreur = null
        runCatching {
            when (onglet) {
                Onglet.Registre -> registre = withContext(Dispatchers.IO) { container.registre.charger() }
                Onglet.Cours -> cours = withContext(Dispatchers.IO) { container.cours.semaine() }
                Onglet.Devoirs -> devoirs = withContext(Dispatchers.IO) { container.devoirs.liste() }
                Onglet.Documents -> {
                    unités = withContext(Dispatchers.IO) { container.documents.unitesBibliotheque() }
                    ressources = withContext(Dispatchers.IO) { container.documents.ressources() }
                }
                Onglet.Messages -> {
                    val page = withContext(Dispatchers.IO) { container.messages.conversations() }
                    conversations = page.conversations
                    thèmes = page.themes
                }
                Onglet.Plus -> {
                    demandes = withContext(Dispatchers.IO) { container.demandes.liste() }
                    actualités = withContext(Dispatchers.IO) { container.actualités.liste() }
                    suiteActualités = actualités.size == 10
                    catalogue = withContext(Dispatchers.IO) { container.boutique.catalogue() }
                    commandes = withContext(Dispatchers.IO) { container.boutique.historique() }
                }
            }
        }.onFailure { erreur = it.message ?: "Chargement impossible" }
        chargement = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 1050.dp
        Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(190.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Greenwood School +", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Onglet.entries.forEach { entrée ->
                if (entrée == onglet) Button(onClick = { détail = null }, enabled = !quizEnJeu, modifier = Modifier.fillMaxWidth()) { Text(entrée.titre) }
                else TextButton(onClick = { onglet = entrée; détail = null }, enabled = !quizEnJeu, modifier = Modifier.fillMaxWidth()) { Text(entrée.titre) }
            }
            if (session.eleves.size > 1) {
                Text("Élève", style = MaterialTheme.typography.titleSmall)
                session.eleves.forEachIndexed { index, élève ->
                    TextButton(onClick = { portée.launch { container.session.sélectionnerÉlève(index) } }, enabled = !quizEnJeu) {
                        Text(if (élève.id == session.eleveId) "✓ ${élève.nomComplet}" else élève.nomComplet)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(session.parent?.nomComplet ?: "Espace parent", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { paramètres = true }, enabled = !quizEnJeu) { Text("Paramètres") }
            TextButton(onClick = { portée.launch { withContext(Dispatchers.IO) { container.auth.déconnexion() } } }, enabled = !quizEnJeu) { Text("Se déconnecter") }
        }
        VerticalDivider(Modifier.fillMaxHeight(), thickness = 1.dp)
        Column(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(onglet.titre, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (onglet == Onglet.Messages) TextButton(onClick = { nouveauMessage = true }) { Text("Nouveau message") }
                OutlinedButton(onClick = { actualisation++ }, enabled = !chargement) { Text("Actualiser") }
            }
            erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (chargement) CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            when (onglet) {
                Onglet.Registre -> RegistreContenu(registre) { if (!quizEnJeu) détail = it }
                Onglet.Cours -> CoursContenu(cours, onSemaine = { sens ->
                    portée.launch {
                        runCatching { withContext(Dispatchers.IO) { container.cours.semaine(sens, if (sens == SensSemaine.Précédente) cours?.semainePrécédente else cours?.semaineSuivante) } }
                            .onSuccess { cours = it }.onFailure { erreur = it.message }
                    }
                })
                Onglet.Devoirs -> Lignes(devoirs, { it.title }, { "${it.matiere} · ${it.dateRemise ?: "Sans échéance"}${if (it.fait) " · Fait" else ""}" }) { if (!quizEnJeu) détail = Détail.DevoirChoisi(it.id) }
                Onglet.Documents -> DocumentsContenu(container, unités, ressources) { if (!quizEnJeu) détail = it }
                Onglet.Messages -> Lignes(conversations, { it.sujet }, { "${it.messages.size} message(s)" }) { if (!quizEnJeu) détail = Détail.ConversationChoisie(it.id) }
                Onglet.Plus -> PlusContenu(demandes, actualités, suiteActualités, chargementSuite, onSuite = {
                    chargementSuite = true
                    portée.launch {
                        runCatching { withContext(Dispatchers.IO) { container.actualités.liste(actualités.size + 1) } }
                            .onSuccess { page ->
                                actualités = (actualités + page).distinctBy { it.id }
                                suiteActualités = page.size == 10
                            }.onFailure { erreur = it.message }
                        chargementSuite = false
                    }
                }, catalogue, commandes) { if (!quizEnJeu) détail = it }
            }
        }
        if (!compact) détail?.let { choisi ->
            VerticalDivider(Modifier.fillMaxHeight(), thickness = 1.dp)
            Box(Modifier.width(390.dp).fillMaxHeight().padding(20.dp)) {
                DétailContenu(container, choisi, quizEnJeu = quizEnJeu, onQuizEnJeu = { quizEnJeu = it }, onFermer = { détail = null }, onActualiser = { actualisation++ })
            }
        }
        }
        if (compact) détail?.let { choisi ->
            Dialog(onDismissRequest = { if (!quizEnJeu) détail = null }) {
                Surface(Modifier.width(600.dp).height(650.dp), shape = MaterialTheme.shapes.large) {
                    Box(Modifier.padding(20.dp)) {
                        DétailContenu(container, choisi, quizEnJeu = quizEnJeu, onQuizEnJeu = { quizEnJeu = it }, onFermer = { détail = null }, onActualiser = { actualisation++ })
                    }
                }
            }
        }
    }
    if (nouveauMessage) NouveauMessage(container, thèmes, onFermer = { nouveauMessage = false }, onEnvoyé = { nouveauMessage = false; actualisation++ })
    if (paramètres) ParamètresDesktop(container) { paramètres = false }
}

@Composable
private fun RegistreContenu(jour: RegistreDuJour?, ouvrir: (Détail) -> Unit) {
    if (jour == null) return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Ce soir · ${jour.horizonCeSoir}", style = MaterialTheme.typography.titleLarge) }
        items(jour.ceSoir) { devoir -> Ligne(devoir.title, devoir.matiere) { ouvrir(Détail.DevoirChoisi(devoir.id)) } }
        item { Text("Aujourd’hui", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
        items(jour.entrees) { entrée ->
            when (entrée) {
                is EntreeRegistre.Actualite -> Ligne(entrée.post.title, "Actualité") { ouvrir(Détail.PostChoisi(entrée.post.id)) }
                is EntreeRegistre.DevoirDonné -> Ligne(entrée.devoir.title, "Devoir") { ouvrir(Détail.DevoirChoisi(entrée.devoir.id)) }
                is EntreeRegistre.MessageReçu -> Ligne(entrée.conversation.sujet, "Message") { ouvrir(Détail.ConversationChoisie(entrée.conversation.id)) }
                is EntreeRegistre.AbsenceNotée -> Ligne(entrée.absence.motif ?: "Absence", "Absence") {}
            }
        }
    }
}

@Composable
private fun CoursContenu(semaine: SemaineCours?, onSemaine: (SensSemaine) -> Unit) {
    if (semaine == null) { Text("Aucun cours disponible"); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onSemaine(SensSemaine.Précédente) }, enabled = semaine.semainePrécédente != null) { Text("←") }
            Text(semaine.label ?: "Semaine du ${semaine.lundi}", modifier = Modifier.padding(horizontal = 12.dp))
            OutlinedButton(onClick = { onSemaine(SensSemaine.Suivante) }, enabled = semaine.semaineSuivante != null) { Text("→") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(semaine.journées) { journée ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(journée.label ?: journée.date?.toString() ?: "Jour ${journée.jour}", fontWeight = FontWeight.Bold)
                        if (journée.créneaux.isEmpty()) Text(semaine.aucunCours ?: "Aucun cours")
                        journée.créneaux.forEach { créneau -> Text("${créneau.début.orEmpty()}–${créneau.fin.orEmpty()}  ${créneau.matière.orEmpty()}  ${créneau.salle.orEmpty()}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentsContenu(container: DesktopContainer, unités: List<UniteBibliotheque>, ressources: List<Ressource>, ouvrir: (Détail) -> Unit) {
    var fiches by remember { mutableStateOf<List<FicheBibliotheque>>(emptyList()) }
    var matière by remember { mutableStateOf<String?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val portée = rememberCoroutineScope()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Bibliothèque", style = MaterialTheme.typography.titleLarge) }
        items(unités) { unité -> Ligne(unité.label, "${unité.nombreRessources ?: 0} ressource(s)") {
            matière = unité.label
            portée.launch {
                runCatching { withContext(Dispatchers.IO) { container.documents.fichesDeUnite(unité.id, unité.label) } }
                    .onSuccess { fiches = it }.onFailure { erreur = it.message }
            }
        } }
        if (matière != null) item { Text(matière!!, style = MaterialTheme.typography.titleMedium) }
        items(fiches) { fiche -> Ligne(fiche.titre, fiche.categorie.orEmpty()) { ouvrir(Détail.FicheChoisie(fiche.id)) } }
        item { Text("Exercices", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
        items(ressources) { ressource -> Ligne(ressource.label, "${ressource.matiere} · ${ressource.type.orEmpty()}") {
            if (ressource.type.equals("quiz", ignoreCase = true)) ouvrir(Détail.QuizChoisi(ressource.id))
        } }
        erreur?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun PlusContenu(demandes: List<Demande>, actualités: List<Post>, suiteActualités: Boolean, chargementSuite: Boolean, onSuite: () -> Unit, catalogue: CatalogueBoutique?, commandes: List<CommandeBoutique>, ouvrir: (Détail) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Mes demandes", style = MaterialTheme.typography.titleLarge) }
        items(demandes) { demande -> Ligne(demande.titre, demande.statut.orEmpty()) { ouvrir(Détail.DemandeChoisie(demande)) } }
        item { Text("Actualités", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
        items(actualités) { post -> Ligne(post.title, post.date?.toLocalDate()?.toString().orEmpty()) { ouvrir(Détail.PostChoisi(post.id)) } }
        if (suiteActualités) item { OutlinedButton(onClick = onSuite, enabled = !chargementSuite) { Text(if (chargementSuite) "Chargement…" else "Voir plus d’actualités") } }
        item { Text("Boutique", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
        items(catalogue?.produits.orEmpty()) { produit -> Ligne(produit.label, produit.prix.orEmpty()) { ouvrir(Détail.ProduitChoisi(produit.id)) } }
        item { Text("Mes commandes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
        items(commandes) { commande -> Ligne(commande.articles.joinToString { it.label }.ifBlank { "Commande ${commande.id}" }, commande.étatLabel.orEmpty()) { ouvrir(Détail.CommandeChoisie(commande)) } }
        item { TextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI("https://github.com/iliasgws/gws-plus/releases")) } }) { Text("Voir les mises à jour") } }
    }
}

@Composable
private fun <T> Lignes(éléments: List<T>, titre: (T) -> String, sousTitre: (T) -> String, ouvrir: (T) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(éléments) { élément -> Ligne(titre(élément), sousTitre(élément)) { ouvrir(élément) } }
    }
}

@Composable
private fun Ligne(titre: String, sousTitre: String, ouvrir: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = ouvrir)) {
        Column(Modifier.padding(16.dp)) {
            Text(titre, fontWeight = FontWeight.SemiBold)
            if (sousTitre.isNotBlank()) Text(sousTitre, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DétailContenu(container: DesktopContainer, détail: Détail, quizEnJeu: Boolean, onQuizEnJeu: (Boolean) -> Unit, onFermer: () -> Unit, onActualiser: () -> Unit) {
    var contenu by remember(détail) { mutableStateOf<Any?>(null) }
    var erreur by remember(détail) { mutableStateOf<String?>(null) }
    var traitement by remember { mutableStateOf(false) }
    var confirmerFait by remember { mutableStateOf(false) }
    var confirmerCommande by remember { mutableStateOf(false) }
    var commandeÀSupprimer by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var confirmerQuitterQuiz by remember { mutableStateOf(false) }
    var réponse by remember(détail) { mutableStateOf("") }
    var piècesRéponse by remember(détail) { mutableStateOf<List<File>>(emptyList()) }
    var nouveauCommentaire by remember(détail) { mutableStateOf("") }
    var réponseÀ by remember(détail) { mutableStateOf<String?>(null) }
    val écritureActive by container.session.ecritureNouveautesActivée.collectAsState(initial = false)
    val portée = rememberCoroutineScope()
    LaunchedEffect(détail) {
        runCatching {
            withContext(Dispatchers.IO) {
                when (détail) {
                    is Détail.DevoirChoisi -> container.devoirs.détail(détail.id)
                    is Détail.ConversationChoisie -> container.messages.conversation(détail.id)
                    is Détail.FicheChoisie -> container.documents.détailFiche(détail.id)
                    is Détail.PostChoisi -> container.actualités.détail(détail.id)
                    is Détail.QuizChoisi -> container.documents.quiz(détail.id)
                    is Détail.DemandeChoisie -> détail.demande
                    is Détail.ProduitChoisi -> container.boutique.détail(détail.id)
                    is Détail.CommandeChoisie -> détail.commande
                }
            }
        }.onSuccess {
            if (it == null) erreur = "Contenu indisponible" else contenu = it
        }.onFailure { erreur = it.message }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Détail", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (quizEnJeu) confirmerQuitterQuiz = true else onFermer() }) { Text("Fermer") }
        }
        erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (contenu == null && erreur == null) CircularProgressIndicator()
        when (val valeur = contenu) {
            is DevoirDétail -> {
                Text(valeur.devoir.title, style = MaterialTheme.typography.titleMedium)
                Text("${valeur.devoir.matiere} · ${valeur.devoir.dateRemise ?: "Sans échéance"}")
                valeur.devoir.description?.let { TexteDéfilant(it) }
                (valeur.devoir.attachments + valeur.copiesEnvoyées).forEach { pièce ->
                    TextButton(onClick = { ouvrirFichier(portée, pièce.url, pièce.name) { erreur = it } }) { Text("Télécharger · ${pièce.name}") }
                }
                if (valeur.peutMarquerFait && !valeur.devoir.fait) Button(onClick = { confirmerFait = true }) { Text("Marquer comme fait") }
                if (valeur.peutAjouterFichiers) Button(onClick = {
                    val fichier = choisirFichier() ?: return@Button
                    traitement = true
                    portée.launch {
                        runCatching { withContext(Dispatchers.IO) { container.devoirs.soumettre(valeur.devoir.id, listOf(fichier)) } }
                            .onSuccess { onActualiser(); onFermer() }.onFailure { erreur = it.message }
                        traitement = false
                    }
                }, enabled = !traitement) { Text("Envoyer une copie") }
                if (confirmerFait) AlertDialog(
                    onDismissRequest = { confirmerFait = false },
                    title = { Text("Marquer ce devoir comme fait ?") },
                    text = { Text("Cette action est définitive.") },
                    confirmButton = { TextButton(onClick = {
                        confirmerFait = false
                        traitement = true
                        portée.launch {
                            runCatching { withContext(Dispatchers.IO) { container.devoirs.soumettre(valeur.devoir.id) } }
                                .onSuccess { onActualiser(); onFermer() }.onFailure { erreur = it.message }
                            traitement = false
                        }
                    }) { Text("Confirmer") } },
                    dismissButton = { TextButton(onClick = { confirmerFait = false }) { Text("Annuler") } },
                )
            }
            is Conversation -> {
                Text(valeur.sujet, style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(valeur.messages) { message ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(if (message.deLAdmin) "École" else "Vous", fontWeight = FontWeight.Bold)
                                Text(message.texte)
                                message.attachments.forEach { pièce -> TextButton(onClick = { ouvrirFichier(portée, pièce.url, pièce.name) { erreur = it } }) { Text(pièce.name) } }
                                message.audio?.let { audio -> TextButton(onClick = { ouvrirFichier(portée, audio.url, audio.name) { erreur = it } }) { Text("Écouter le message vocal") } }
                            }
                        }
                    }
                }
                OutlinedTextField(réponse, { réponse = it }, label = { Text("Répondre") }, modifier = Modifier.fillMaxWidth())
                PanneauIADesktop(container, réponse) { réponse = it }
                OutlinedButton(onClick = { choisirFichier()?.let { piècesRéponse = piècesRéponse + it } }) { Text("Joindre un fichier") }
                piècesRéponse.forEach { Text(it.name, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = {
                    traitement = true
                    portée.launch {
                        runCatching { withContext(Dispatchers.IO) { container.messages.envoyerRéponse(valeur, réponse, piècesRéponse) } }
                            .onSuccess { réponse = ""; onActualiser(); onFermer() }.onFailure { erreur = it.message }
                        traitement = false
                    }
                }, enabled = réponse.isNotBlank() && !traitement) { Text("Envoyer") }
            }
            is FicheBibliothequeDetail -> {
                Text(valeur.matiere ?: "Document", style = MaterialTheme.typography.titleMedium)
                valeur.description?.let { TexteDéfilant(it) }
                valeur.fichiers.forEach { pièce -> TextButton(onClick = { ouvrirFichier(portée, pièce.url, pièce.name) { erreur = it } }) { Text("Télécharger · ${pièce.name}") } }
            }
            is PostDetail -> {
                Text(valeur.title, style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    valeur.descriptionHtml?.let { html -> item { Text(Jsoup.parse(html).body().wholeText().trim()) } }
                    if (valeur.images.isNotEmpty()) item { Text("Images", fontWeight = FontWeight.Bold) }
                    items(valeur.images.size) { numéro ->
                        TextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(valeur.images[numéro])) } }) { Text("Ouvrir l’image ${numéro + 1}") }
                    }
                    if (valeur.questions.isNotEmpty()) item { Text("Questionnaire", fontWeight = FontWeight.Bold) }
                    items(valeur.questions) { question ->
                        Column {
                            Text(question.label)
                            question.réponses.forEach { choix ->
                                OutlinedButton(onClick = {
                                    traitement = true
                                    portée.launch {
                                        runCatching { withContext(Dispatchers.IO) {
                                            container.actualités.répondreQuestionQuiz(valeur.id, question.alias!!, choix)
                                            container.actualités.détail(valeur.id)
                                        } }.onSuccess { contenu = it }.onFailure { erreur = it.message }
                                        traitement = false
                                    }
                                }, enabled = écritureActive && question.alias != null && question.réponseChoisie == null && !traitement) { Text(choix) }
                            }
                            question.réponseChoisie?.let { Text("Réponse : $it") }
                        }
                    }
                    if (valeur.commentaires.isNotEmpty()) item { Text("Commentaires", fontWeight = FontWeight.Bold) }
                    items(valeur.commentaires) { commentaire ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(commentaire.auteur, fontWeight = FontWeight.Bold)
                                Text(commentaire.texte)
                                commentaire.sousCommentaires.forEach { réponse ->
                                    Text("${réponse.auteur} · ${réponse.texte}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (valeur.peutRépondre && écritureActive) TextButton(onClick = { réponseÀ = commentaire.auteur }) { Text("Répondre") }
                            }
                        }
                    }
                }
                valeur.files.forEach { pièce -> TextButton(onClick = { ouvrirFichier(portée, pièce.url, pièce.name) { erreur = it } }) { Text("Télécharger · ${pièce.name}") } }
                if (valeur.peutNouveauCommentaire && écritureActive) {
                    OutlinedTextField(nouveauCommentaire, { nouveauCommentaire = it }, label = { Text(réponseÀ?.let { "Répondre à $it" } ?: "Commentaire") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        traitement = true
                        portée.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                container.actualités.commenter(valeur.id, nouveauCommentaire, réponseÀ)
                                container.actualités.détail(valeur.id)
                            } }.onSuccess { nouveau ->
                                    contenu = nouveau
                                    nouveauCommentaire = ""
                                    réponseÀ = null
                                }.onFailure { erreur = it.message }
                            traitement = false
                        }
                    }, enabled = nouveauCommentaire.isNotBlank() && !traitement) { Text("Publier") }
                }
            }
            is Demande -> {
                Text(valeur.titre, style = MaterialTheme.typography.titleMedium)
                valeur.statut?.let { Text(it) }
                valeur.reponses.forEach { réponse ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            réponse.label?.let { Text(it, fontWeight = FontWeight.Bold) }
                            Text(réponse.reponse)
                        }
                    }
                }
            }
            is ProduitDétail -> {
                val commandeId = (détail as? Détail.CommandeChoisie)?.commande?.id
                var variante by remember(valeur.id) {
                    mutableStateOf(valeur.variantes.firstOrNull { it.label == valeur.prérempli?.taille } ?: valeur.variantes.firstOrNull())
                }
                var variantesOuvertes by remember { mutableStateOf(false) }
                var quantité by remember(valeur.id) { mutableStateOf((valeur.prérempli?.quantité ?: 1).toString()) }
                var commentaire by remember(valeur.id) { mutableStateOf(valeur.prérempli?.commentaire.orEmpty()) }
                Text(valeur.label, style = MaterialTheme.typography.titleMedium)
                valeur.description?.let { TexteDéfilant(it) }
                Text(valeur.prixRaw ?: "")
                if (valeur.variantes.isNotEmpty()) Box {
                    OutlinedButton(onClick = { variantesOuvertes = true }) { Text(variante?.label ?: "Choisir une variante") }
                    DropdownMenu(expanded = variantesOuvertes, onDismissRequest = { variantesOuvertes = false }) {
                        valeur.variantes.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { variante = option; variantesOuvertes = false }) }
                    }
                }
                OutlinedTextField(quantité, { quantité = it.filter(Char::isDigit) }, label = { Text("Quantité") })
                OutlinedTextField(commentaire, { commentaire = it }, label = { Text("Commentaire") })
                if (valeur.peutCommander) Button(onClick = { confirmerCommande = true }, enabled = (quantité.toIntOrNull() ?: 0) > 0) { Text(if (commandeId == null) "Commander" else "Enregistrer les modifications") }
                if (confirmerCommande) AlertDialog(
                    onDismissRequest = { confirmerCommande = false },
                    title = { Text(if (commandeId == null) "Passer cette commande ?" else "Modifier cette commande ?") },
                    text = { Text("La commande sera enregistrée immédiatement par l’école.") },
                    confirmButton = { TextButton(onClick = {
                        confirmerCommande = false
                        traitement = true
                        portée.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                container.boutique.commander(valeur.id, variante?.id, variante?.label, quantité.toInt(), commentaire, variante?.montant ?: valeur.prixRaw, commandeId)
                            } }.onSuccess {
                                if (it.succès) { onActualiser(); onFermer() } else erreur = it.message ?: "Commande impossible"
                            }.onFailure { erreur = it.message }
                            traitement = false
                        }
                    }, enabled = !traitement) { Text("Confirmer") } },
                    dismissButton = { TextButton(onClick = { confirmerCommande = false }) { Text("Annuler") } },
                )
            }
            is CommandeBoutique -> {
                Text("Commande ${valeur.id}", style = MaterialTheme.typography.titleMedium)
                valeur.étatLabel?.let { Text(it) }
                valeur.date?.let { Text(it) }
                valeur.articles.forEach { article ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(article.label, fontWeight = FontWeight.Bold)
                            Text("${article.quantité ?: 1} · ${article.prix.orEmpty()}")
                            if (article.modifiable && article.produitId != null) TextButton(onClick = {
                                traitement = true
                                portée.launch {
                                    runCatching { withContext(Dispatchers.IO) { container.boutique.détail(article.produitId, valeur.id) } }
                                        .onSuccess { contenu = it }.onFailure { erreur = it.message }
                                    traitement = false
                                }
                            }, enabled = !traitement) { Text("Modifier") }
                            if (article.supprimable) TextButton(onClick = { commandeÀSupprimer = valeur.id to article.id }) { Text("Supprimer l’article") }
                        }
                    }
                }
                if (valeur.supprimable) TextButton(onClick = { commandeÀSupprimer = valeur.id to null }) { Text("Supprimer la commande") }
                commandeÀSupprimer?.let { cible -> AlertDialog(
                    onDismissRequest = { commandeÀSupprimer = null },
                    title = { Text(if (cible.second == null) "Supprimer cette commande ?" else "Supprimer cet article ?") },
                    text = { Text("Cette action est définitive.") },
                    confirmButton = { TextButton(onClick = {
                        commandeÀSupprimer = null
                        traitement = true
                        portée.launch {
                            runCatching { withContext(Dispatchers.IO) { container.boutique.supprimer(cible.first, cible.second) } }
                                .onSuccess { if (it) { onActualiser(); onFermer() } else erreur = "Suppression impossible" }
                                .onFailure { erreur = it.message }
                            traitement = false
                        }
                    }, enabled = !traitement) { Text("Supprimer") } },
                    dismissButton = { TextButton(onClick = { commandeÀSupprimer = null }) { Text("Annuler") } },
                ) }
            }
            is school.greenwood.plus.data.repo.QuizChargé -> QuizContenu(container, valeur, onJeuChange = onQuizEnJeu) { erreur = it }
        }
        if (confirmerQuitterQuiz) AlertDialog(
            onDismissRequest = { confirmerQuitterQuiz = false },
            title = { Text("Quitter le quiz ?") },
            text = { Text("Vos réponses en cours seront perdues.") },
            confirmButton = { TextButton(onClick = { confirmerQuitterQuiz = false; onQuizEnJeu(false); onFermer() }) { Text("Quitter") } },
            dismissButton = { TextButton(onClick = { confirmerQuitterQuiz = false }) { Text("Continuer") } },
        )
    }
}

@Composable
private fun TexteDéfilant(texte: String) {
    LazyColumn(Modifier.fillMaxWidth().height(230.dp)) {
        item { Text(Jsoup.parse(texte).body().wholeText().trim()) }
    }
}

@Composable
private fun QuizContenu(container: DesktopContainer, chargé: school.greenwood.plus.data.repo.QuizChargé, onJeuChange: (Boolean) -> Unit, erreur: (String?) -> Unit) {
    var commencé by remember(chargé.détail.id) { mutableStateOf(false) }
    var index by remember(chargé.détail.id) { mutableIntStateOf(0) }
    val jouées = remember(chargé.détail.id) { mutableMapOf<Int, RéponseJouée>() }
    var score by remember(chargé.détail.id) { mutableStateOf<String?>(null) }
    var envoi by remember { mutableStateOf(false) }
    val portée = rememberCoroutineScope()
    val questions = chargé.détail.questions
    if (!commencé) {
        Text(chargé.détail.label, style = MaterialTheme.typography.titleMedium)
        Text("${questions.size} question(s) · ${chargé.détail.minutes.orEmpty()}")
        Button(onClick = { commencé = true; onJeuChange(true) }, enabled = chargé.détail.peutJouer && questions.isNotEmpty()) { Text("Commencer") }
        return
    }
    if (index >= questions.size) {
        LaunchedEffect(chargé.détail.id) { onJeuChange(false) }
        Text(score?.let { "Score : $it" } ?: "Quiz terminé")
        if (score == null && !envoi) Button(onClick = {
            envoi = true
            portée.launch {
                runCatching { withContext(Dispatchers.IO) { container.documents.envoyerRésultatQuiz(chargé.détail, chargé.questionsBrutes, jouées) } }
                    .onSuccess { score = it.score ?: "Résultat envoyé" }
                    .onFailure { erreur(it.message) }
                envoi = false
            }
        }) { Text("Envoyer le résultat") }
        return
    }
    val questionIndex = index
    val question = questions[questionIndex]
    var secondesRestantes by remember(chargé.détail.id, index) { mutableStateOf(question.tempsReponse) }
    LaunchedEffect(chargé.détail.id, index) {
        val durée = question.tempsReponse ?: return@LaunchedEffect
        while ((secondesRestantes ?: 0) > 0) {
            delay(1000)
            secondesRestantes = (secondesRestantes ?: 0) - 1
        }
        if (index == questionIndex) {
            jouées[questionIndex] = RéponseJouée("", durée)
            index = questionIndex + 1
        }
    }
    Text(chargé.détail.label, style = MaterialTheme.typography.titleMedium)
    Text("Question ${index + 1}/${questions.size} · ${question.texte}")
    secondesRestantes?.let { Text("Temps restant : ${it}s") }
    question.reponses.forEach { option: QuizReponse ->
        OutlinedButton(onClick = {
            jouées[questionIndex] = RéponseJouée(option.texte, (question.tempsReponse ?: 0) - (secondesRestantes ?: 0))
            index = questionIndex + 1
        }, modifier = Modifier.fillMaxWidth()) { Text(option.texte) }
    }
}

@Composable
private fun NouveauMessage(container: DesktopContainer, thèmes: List<ThemeMessage>, onFermer: () -> Unit, onEnvoyé: () -> Unit) {
    var sujet by remember { mutableStateOf("") }
    var texte by remember { mutableStateOf("") }
    var thème by remember { mutableStateOf(thèmes.firstOrNull()) }
    var menu by remember { mutableStateOf(false) }
    var pièces by remember { mutableStateOf<List<File>>(emptyList()) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var envoi by remember { mutableStateOf(false) }
    val portée = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Nouveau message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sujet, { sujet = it }, label = { Text("Sujet") })
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(thème?.label ?: "Choisir une catégorie") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        thèmes.forEach { candidat -> DropdownMenuItem(text = { Text(candidat.label) }, onClick = { thème = candidat; menu = false }) }
                    }
                }
                OutlinedTextField(texte, { texte = it }, label = { Text("Message") }, minLines = 4)
                PanneauIADesktop(container, texte) { texte = it }
                OutlinedButton(onClick = {
                    choisirFichier()?.let { fichier ->
                        if (fichier.length() > 1_000_000) erreur = "Pièce jointe limitée à 1 Mo"
                        else { pièces = pièces + fichier; erreur = null }
                    }
                }) { Text("Ajouter une pièce jointe") }
                pièces.forEach { Text(it.name) }
                erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            envoi = true
            portée.launch {
                runCatching { withContext(Dispatchers.IO) { container.messages.envoyerNouveau(sujet, texte, thème!!.id, pièces) } }
                    .onSuccess { onEnvoyé() }.onFailure { erreur = it.message ?: "Envoi impossible" }
                envoi = false
            }
        }, enabled = !envoi && sujet.isNotBlank() && texte.isNotBlank() && thème != null) { Text("Envoyer") } },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

@Composable
private fun ParamètresDesktop(container: DesktopContainer, onFermer: () -> Unit) {
    val actuels by container.session.réglagesIA.collectAsState(initial = RéglagesIA())
    val écriture by container.session.ecritureNouveautesActivée.collectAsState(initial = false)
    var réglages by remember(actuels) { mutableStateOf(actuels) }
    var autoriserÉcriture by remember(écriture) { mutableStateOf(écriture) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val portée = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Paramètres · Assistant IA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoriserÉcriture, onCheckedChange = { autoriserÉcriture = it })
                    Text("Autoriser les commentaires et les questionnaires des actualités")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = réglages.actif, onCheckedChange = { réglages = réglages.copy(actif = it) })
                    Text("Activer l’assistant IA")
                }
                OutlinedTextField(réglages.base, { réglages = réglages.copy(base = it) }, label = { Text("Adresse du fournisseur") })
                OutlinedTextField(réglages.modèle, { réglages = réglages.copy(modèle = it) }, label = { Text("Modèle") })
                OutlinedTextField(réglages.clé, { réglages = réglages.copy(clé = it) }, label = { Text("Clé API") }, visualTransformation = PasswordVisualTransformation())
                Row {
                    TonIA.entries.forEach { ton -> TextButton(onClick = { réglages = réglages.copy(ton = ton) }) {
                        Text(if (réglages.ton == ton) "✓ ${ton.libellé}" else ton.libellé)
                    } }
                }
                erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            portée.launch {
                runCatching {
                    container.session.définirEcritureNouveautes(autoriserÉcriture)
                    container.session.définirRéglagesIA(réglages)
                }
                    .onSuccess { onFermer() }.onFailure { erreur = it.message }
            }
        }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

@Composable
private fun PanneauIADesktop(container: DesktopContainer, texte: String, onRésultat: (String) -> Unit) {
    val réglages by container.session.réglagesIA.collectAsState(initial = RéglagesIA())
    if (!réglages.prête) return
    var menu by remember { mutableStateOf(false) }
    var consigne by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var proposition by remember { mutableStateOf<String?>(null) }
    var travail by remember { mutableStateOf(false) }
    val portée = rememberCoroutineScope()
    Column {
        OutlinedTextField(consigne, { consigne = it }, label = { Text("Décrivez votre modification") }, modifier = Modifier.fillMaxWidth())
        Box {
            TextButton(onClick = { menu = true }, enabled = !travail && texte.isNotBlank()) { Text(if (travail) "Traitement…" else "✨ Améliorer le texte") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                ActionIA.entries.forEach { action -> DropdownMenuItem(text = { Text(action.libellé) }, onClick = {
                    menu = false
                    travail = true
                    portée.launch {
                        runCatching { ComposeurIA.transformer(réglages, action, consigne, texte) }
                            .onSuccess { proposition = it }.onFailure { erreur = it.message }
                        travail = false
                    }
                }) }
            }
        }
        proposition?.let { résultat ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(résultat)
                    Row {
                        TextButton(onClick = { onRésultat(résultat); proposition = null }) { Text("Remplacer") }
                        TextButton(onClick = { proposition = null }) { Text("Annuler") }
                    }
                }
            }
        }
        erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun choisirFichier(): File? = JFileChooser().let { sélecteur ->
    if (sélecteur.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) sélecteur.selectedFile else null
}

private fun ouvrirFichier(portée: kotlinx.coroutines.CoroutineScope, url: String, nom: String, erreur: (String?) -> Unit) {
    portée.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val dossier = File(System.getProperty("user.home"), "Downloads/gws-plus").apply { mkdirs() }
                val nomSain = nom.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._ -]"), "_")
                    .takeIf { it.isNotBlank() && !it.startsWith('.') } ?: "document"
                val cible = File(dossier, nomSain)
                val temporaire = File(dossier, "$nomSain.part")
                val requête = Request.Builder().url(url).build()
                try {
                    BotiHttp.client().newCall(requête).execute().use { réponse ->
                        if (!réponse.isSuccessful) error("Téléchargement impossible (${réponse.code})")
                        réponse.body.byteStream().use { entrée -> temporaire.outputStream().use { sortie -> entrée.copyTo(sortie) } }
                    }
                    Files.move(temporaire.toPath(), cible.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    temporaire.delete()
                }
                Desktop.getDesktop().open(cible)
            }
        }.onFailure { erreur(it.message ?: "Ouverture impossible") }
    }
}
