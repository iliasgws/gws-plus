package school.greenwood.plus.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Newspaper
import school.greenwood.plus.ui.screens.actualites.ActualitesScreen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import school.greenwood.plus.AppContainer
import school.greenwood.plus.ui.components.BarreOnglets
import school.greenwood.plus.ui.screens.LoginScreen
import school.greenwood.plus.ui.screens.OnboardingScreen
import school.greenwood.plus.ui.screens.boutique.BoutiqueHistoriqueScreen
import school.greenwood.plus.ui.screens.boutique.BoutiqueItemScreen
import school.greenwood.plus.ui.screens.boutique.BoutiqueScreen
import school.greenwood.plus.ui.screens.boutique.RepasInviteScreen
import school.greenwood.plus.ui.screens.devoirs.DevoirsScreen
import school.greenwood.plus.ui.screens.demandes.DemandesScreen
import school.greenwood.plus.ui.screens.documents.DocumentsScreen
import school.greenwood.plus.ui.screens.documents.QuizScreen
import school.greenwood.plus.ui.screens.documents.DialogueQuitterQuiz
import school.greenwood.plus.ui.screens.messages.ConversationScreen
import school.greenwood.plus.ui.screens.messages.MessagesScreen
import school.greenwood.plus.ui.screens.messages.NouveauMessageScreen
import school.greenwood.plus.ui.screens.cours.CoursScreen
import school.greenwood.plus.ui.screens.parametres.ParametresScreen
import school.greenwood.plus.ui.screens.plus.PlusScreen
import school.greenwood.plus.ui.screens.registre.PostDetailScreen
import school.greenwood.plus.ui.screens.registre.RegistreScreen
import school.greenwood.plus.ui.theme.FonduCouleur
import school.greenwood.plus.ui.theme.GwsAccent
import school.greenwood.plus.ui.theme.LocalGwsAccent
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Navigation racine. Trois états pilotés par la session (pas un NavController
 * racine) : onboarding → connexion → registre. Quand le serveur tue la session
 * (« disconnect »), l'état retombe sur la connexion, où que l'on soit.
 *
 * Onglets (docs/product/DESIGN.md §3) : chaque onglet garde sa pile via
 * saveState/restoreState, retour depuis un onglet racine → Registre,
 * retour depuis le Registre → quitter l'app.
 */

val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("AppContainer absent")
}

data class Onglet(
    val route: String,
    val label: String,
    val icone: ImageVector,
)

val Onglets = listOf(
    Onglet("registre", "Registre", Icons.Rounded.Home),
    Onglet("cours", "Cours", Icons.Rounded.CalendarMonth),
    Onglet("devoirs", "Devoirs", Icons.AutoMirrored.Rounded.MenuBook),
    Onglet("documents", "Documents", Icons.Rounded.Folder),
    Onglet("messages", "Messages", Icons.Rounded.QuestionAnswer),
    Onglet("plus", "Plus", Icons.Rounded.MoreHoriz),
)

/** Les routes racines des onglets (destination de départ incluse). */
private val RoutesOnglets = Onglets.map { it.route }.toSet()

/** La route de départ de la coquille, utilisée comme repère de toutes les piles. */
private const val RouteDépart = "registre"

private enum class ÉcranRacine { Chargement, Onboarding, Connexion, Registre }

@Composable
fun AppNav(container: AppContainer) {
    val session by container.session.state.collectAsStateWithLifecycle(initialValue = null)
    val onboardingVu by container.session.onboardingVu.collectAsStateWithLifecycle(initialValue = null)

    val écran: ÉcranRacine = when {
        onboardingVu == null -> ÉcranRacine.Chargement
        session?.isAuthentifie == true -> ÉcranRacine.Registre
        onboardingVu == false -> ÉcranRacine.Onboarding
        else -> ÉcranRacine.Connexion
    }

    val portée = rememberCoroutineScope()
    Crossfade(targetState = écran, animationSpec = tween(250), label = "racine") { é ->
        when (é) {
            ÉcranRacine.Chargement -> Box(Modifier.fillMaxSize().background(RegistreTheme.colors.paper))
            ÉcranRacine.Onboarding -> OnboardingScreen(
                onFini = { portée.launch { container.session.marquerOnboardingVu() } },
            )
            ÉcranRacine.Connexion -> LoginScreen(
                onConnecté = { /* l'état de session bascule vers le registre */ },
                container = container,
            )
            ÉcranRacine.Registre -> Shell(container = container)
        }
    }
}

/**
 * L'accent de l'endroit où l'on se trouve : une seule table fait correspondre
 * route et famille de couleur. Les onglets ont leur famille ; les écrans de
 * détail héritent de celle de leur onglet (post → actualités, quiz →
 * documents, conversation / nouveau-message / demandes → messages).
 */
private fun accentDe(route: String?, couleurs: school.greenwood.plus.ui.theme.GwsColors): GwsAccent =
    when (route) {
        "registre" -> couleurs.accents.getValue("registre")
        "actualites", "post" -> couleurs.accents.getValue("actualites")
        "cours" -> couleurs.accents.getValue("cours")
        "devoirs" -> couleurs.accents.getValue("devoirs")
        "documents" -> couleurs.accents.getValue("documents")
        "messages", "demandes" -> couleurs.accents.getValue("messages")
        "parametres" -> couleurs.accents.getValue("registre")
        "plus", "boutique", "boutique-historique", "repas-invite" -> couleurs.accents.getValue("plus")
        else -> when {
            route?.startsWith("post/") == true -> couleurs.accents.getValue("actualites")
            route?.startsWith("quiz/") == true -> couleurs.accents.getValue("documents")
            route?.startsWith("conversation/") == true -> couleurs.accents.getValue("messages")
            route == "nouveau-message" -> couleurs.accents.getValue("messages")
            route == "demandes" -> couleurs.accents.getValue("messages")
            route?.startsWith("boutique/") == true -> couleurs.accents.getValue("plus")
            else -> couleurs.accents.getValue("registre")
        }
    }

/**
 * L'onglet actif se lit sur la pile de navigation, pas sur la route exacte
 * (issue #34) : le dernier écran racine d'onglet présent dans la pile est
 * l'onglet qui « possède » la sous-page affichée au-dessus. Un post ouvert
 * depuis le Registre garde le Registre actif ; ouvert depuis Actualités,
 * Actualités — le retour dépile et l'onglet se recalcule tout seul.
 */
private fun ongletActifDe(pile: List<NavBackStackEntry>): String? =
    pile.lastOrNull { entrée ->
        val route = entrée.destination?.route
        route != null && route in RoutesOnglets
    }?.destination?.route

/** La coquille : barre basse 6 onglets à accents + piles d'onglets. */
@Composable
fun Shell(container: AppContainer) {
    val navController = rememberNavController()
    val destinationCourante by navController.currentBackStackEntryAsState()
    val routeCourante = destinationCourante?.destination?.route

    // L'onglet actif suit la pile : une sous-page (post, quiz, conversation…)
    // laisse son onglet parent actif dans la barre basse (issue #34).
    val pile by navController.currentBackStack.collectAsStateWithLifecycle(initialValue = emptyList())
    val ongletActif = ongletActifDe(pile) ?: RouteDépart

    // Un quiz en cours protège sa sortie (issue #17) : changer d'onglet pendant
    // la partie demande confirmation — l'onglet demandé est mis en attente le
    // temps du dialogue, abandon confirmé → le quiz est dépilé sans sauvegarde
    // (le ViewModel meurt, son signal retombe) puis l'onglet est suivi.
    val quizEnJeu by container.quizEnJeu.collectAsStateWithLifecycle()
    var ongletEnAttente by remember { mutableStateOf<String?>(null) }

    // L'accent suit l'écran, en fondu : puces, surligneurs et états vides se
    // teintent de la matière de l'endroit où l'on se trouve.
    val couleurs = RegistreTheme.colors
    val cible = accentDe(routeCourante, couleurs)
    val accentAnimé = GwsAccent(
        teinte = animateColorAsState(cible.teinte, animationSpec = FonduCouleur, label = "accentTeinte").value,
        conteneur = animateColorAsState(cible.conteneur, animationSpec = FonduCouleur, label = "accentConteneur").value,
        surConteneur = animateColorAsState(cible.surConteneur, animationSpec = FonduCouleur, label = "accentSurConteneur").value,
    )

    // Session expirée en plein usage : l'état racine renvoie à la connexion ;
    // ici on dépille pour ne rien laisser sous un onglet fantôme.
    LaunchedEffect(Unit) {
        container.session.events.collect {
            navController.popBackStack(
                navController.graph.findStartDestination().id,
                inclusive = false,
            )
        }
    }

    CompositionLocalProvider(LocalGwsAccent provides accentAnimé) {
        Scaffold(
            containerColor = RegistreTheme.colors.paper,
            bottomBar = {
                BarreOnglets(
                    onglets = Onglets,
                    routeSélectionnée = ongletActif,
                    accents = couleurs.accents,
                    onOnglet = { route ->
                        if (quizEnJeu) ongletEnAttente = route
                        else navController.allerÀLOnglet(route)
                    },
                )
            },
        ) { padding ->
        NavHost(
            navController = navController,
            startDestination = RouteDépart,
            modifier = Modifier
                .fillMaxSize()
                .background(RegistreTheme.colors.paper),
            enterTransition = { fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 24 } },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(140)) + slideOutVertically(tween(200)) { it / 24 } },
        ) {
            composable("registre") {
                RegistreScreen(
                    container = container,
                    padding = padding,
                    ouvrirDemandes = { navController.allerDétail("demandes") },
                    ouvrirParamètres = { navController.allerDétail("parametres") },
                    ouvrirRepas = { navController.allerDétail("repas-invite") },
                    ouvrirPost = { id -> navController.allerDétail("post/$id") },
                    ouvrirEmploi = { navController.allerÀLOnglet("cours") },
                )
            }
            composable("actualites") {
                ActualitesScreen(
                    container = container,
                    padding = padding,
                    ouvrirPost = { id -> navController.allerDétail("post/$id") },
                )
            }
            composable("plus") {
                PlusScreen(
                    padding = padding,
                    ouvrirActualités = { navController.allerDétail("actualites") },
                    ouvrirBoutique = { navController.allerDétail("boutique") },
                )
            }
            composable("boutique") {
                BoutiqueScreen(
                    container = container,
                    padding = padding,
                    ouvrirProduit = { id -> navController.allerDétail("boutique/$id") },
                    ouvrirHistorique = { navController.allerDétail("boutique-historique") },
                )
            }
            composable(
                route = "boutique/{produitId}?commande={commandeId}",
                arguments = listOf(
                    navArgument("produitId") { type = NavType.StringType },
                    navArgument("commandeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entrée ->
                BoutiqueItemScreen(
                    container = container,
                    padding = padding,
                    produitId = entrée.arguments?.getString("produitId") ?: "",
                    commandeId = entrée.arguments?.getString("commandeId"),
                    retour = { navController.popBackStack() },
                )
            }
            composable("boutique-historique") {
                BoutiqueHistoriqueScreen(
                    container = container,
                    padding = padding,
                    ouvrirModifier = { produitId, commandeId ->
                        navController.allerDétail("boutique/$produitId?commande=$commandeId")
                    },
                    retour = { navController.popBackStack() },
                )
            }
            composable("repas-invite") {
                RepasInviteScreen(
                    container = container,
                    padding = padding,
                    retour = { navController.popBackStack() },
                )
            }
            composable("cours") {
                CoursScreen(container = container, padding = padding)
            }
            composable("devoirs") {
                DevoirsScreen(container = container, padding = padding)
            }
            composable("documents") {
                DocumentsScreen(
                    container = container,
                    padding = padding,
                    onOuvrirQuiz = { id -> navController.allerDétail("quiz/$id") },
                )
            }
            composable("quiz/{quizId}") { entrée ->
                QuizScreen(
                    container = container,
                    padding = padding,
                    quizId = entrée.arguments?.getString("quizId") ?: "",
                    retour = { navController.popBackStack() },
                )
            }
            composable("messages") {
                MessagesScreen(
                    container = container,
                    padding = padding,
                    onOuvrirConversation = { id -> navController.allerDétail("conversation/$id") },
                    onNouveauMessage = { navController.allerDétail("nouveau-message") },
                )
            }
            composable("conversation/{conversationId}") { entrée ->
                ConversationScreen(
                    container = container,
                    padding = padding,
                    conversationId = entrée.arguments?.getString("conversationId") ?: "",
                    retour = { navController.popBackStack() },
                )
            }
            composable("nouveau-message") {
                NouveauMessageScreen(
                    container = container,
                    padding = padding,
                    retour = { navController.popBackStack() },
                    onEnvoyé = { navController.popBackStack() },
                )
            }
            composable("demandes") {
                DemandesScreen(
                    container = container,
                    padding = padding,
                    retour = { navController.popBackStack() },
                )
            }
            composable("post/{postId}") { entrée ->
                PostDetailScreen(
                    container = container,
                    padding = padding,
                    postId = entrée.arguments?.getString("postId") ?: "",
                    retour = { navController.popBackStack() },
                )
            }
            composable("parametres") {
                ParametresScreen(
                    container = container,
                    padding = padding,
                    retour = { navController.popBackStack() },
                )
            }
        }
        }

        // Sortie d'un quiz en jeu confirmée : on dépille le quiz sans
        // sauvegarde puis l'onglet demandé est suivi (le modèle de piles
        // d'onglets est inchangé — allerÀLOnglet reste le seul chemin).
        ongletEnAttente?.let { cible ->
            DialogueQuitterQuiz(
                onContinuer = { ongletEnAttente = null },
                onQuitter = {
                    ongletEnAttente = null
                    navController.popBackStack("quiz/{quizId}", inclusive = true, saveState = false)
                    navController.allerÀLOnglet(cible)
                },
            )
        }
    }
}

/**
 * Le geste d'onglet du docs/product/DESIGN.md §3 : on ne dépille rien, chaque onglet
 * garde sa pile (saveState/restoreState), jamais de racine poussée sur une
 * pile de détail.
 */
private fun NavHostController.allerÀLOnglet(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.allerDétail(route: String) {
    navigate(route) { launchSingleTop = true }
}
