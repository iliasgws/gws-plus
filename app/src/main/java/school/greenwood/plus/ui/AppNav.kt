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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import school.greenwood.plus.AppContainer
import school.greenwood.plus.ui.components.BarreOnglets
import school.greenwood.plus.ui.screens.LoginScreen
import school.greenwood.plus.ui.screens.OnboardingScreen
import school.greenwood.plus.ui.screens.devoirs.DevoirsScreen
import school.greenwood.plus.ui.screens.demandes.DemandesScreen
import school.greenwood.plus.ui.screens.documents.DocumentsScreen
import school.greenwood.plus.ui.screens.documents.QuizScreen
import school.greenwood.plus.ui.screens.messages.ConversationScreen
import school.greenwood.plus.ui.screens.messages.MessagesScreen
import school.greenwood.plus.ui.screens.messages.NouveauMessageScreen
import school.greenwood.plus.ui.screens.cours.CoursScreen
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
    Onglet("actualites", "Actualités", Icons.Rounded.Newspaper),
    Onglet("cours", "Cours", Icons.Rounded.CalendarMonth),
    Onglet("devoirs", "Devoirs", Icons.AutoMirrored.Rounded.MenuBook),
    Onglet("documents", "Documents", Icons.Rounded.Folder),
    Onglet("messages", "Messages", Icons.Rounded.QuestionAnswer),
)

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
        else -> when {
            route?.startsWith("post/") == true -> couleurs.accents.getValue("actualites")
            route?.startsWith("quiz/") == true -> couleurs.accents.getValue("documents")
            route?.startsWith("conversation/") == true -> couleurs.accents.getValue("messages")
            route == "nouveau-message" -> couleurs.accents.getValue("messages")
            route == "demandes" -> couleurs.accents.getValue("messages")
            else -> couleurs.accents.getValue("registre")
        }
    }

/** La coquille : barre basse 6 onglets à accents + piles d'onglets. */
@Composable
fun Shell(container: AppContainer) {
    val navController = rememberNavController()
    val destinationCourante by navController.currentBackStackEntryAsState()
    val routeCourante = destinationCourante?.destination?.route

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
                    routeSélectionnée = routeCourante,
                    accents = couleurs.accents,
                    onOnglet = { route -> navController.allerÀLOnglet(route) },
                )
            },
        ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "registre",
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
        }
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
