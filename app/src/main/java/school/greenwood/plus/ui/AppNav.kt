package school.greenwood.plus.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import school.greenwood.plus.AppContainer
import school.greenwood.plus.ui.screens.LoginScreen
import school.greenwood.plus.ui.screens.OnboardingScreen
import school.greenwood.plus.ui.screens.devoirs.DevoirsScreen
import school.greenwood.plus.ui.screens.demandes.DemandesScreen
import school.greenwood.plus.ui.screens.documents.DocumentsScreen
import school.greenwood.plus.ui.screens.messages.ConversationScreen
import school.greenwood.plus.ui.screens.messages.MessagesScreen
import school.greenwood.plus.ui.screens.messages.NouveauMessageScreen
import school.greenwood.plus.ui.screens.registre.PostDetailScreen
import school.greenwood.plus.ui.screens.registre.RegistreScreen
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Navigation racine. Trois états pilotés par la session (pas un NavController
 * racine) : onboarding → connexion → registre. Quand le serveur tue la session
 * (« disconnect »), l'état retombe sur la connexion, où que l'on soit.
 *
 * Onglets (DESIGN.md §3) : chaque onglet garde sa pile via
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

/** La coquille : barre basse 4 onglets + piles d'onglets. */
@Composable
fun Shell(container: AppContainer) {
    val navController = rememberNavController()
    val destinationCourante by navController.currentBackStackEntryAsState()
    val routeCourante = destinationCourante?.destination?.route

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

    Scaffold(
        containerColor = RegistreTheme.colors.paper,
        bottomBar = {
            NavigationBar(
                containerColor = RegistreTheme.colors.page,
                tonalElevation = 0.dp,
            ) {
                Onglets.forEach { onglet ->
                    val sélectionné = routeCourante == onglet.route
                    NavigationBarItem(
                        selected = sélectionné,
                        onClick = { navController.allerÀLOnglet(onglet.route) },
                        icon = {
                            Icon(
                                imageVector = onglet.icone,
                                contentDescription = onglet.label,
                            )
                        },
                        label = { Text(onglet.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RegistreTheme.colors.ink,
                            selectedTextColor = RegistreTheme.colors.ink,
                            unselectedIconColor = RegistreTheme.colors.chalk,
                            unselectedTextColor = RegistreTheme.colors.chalk,
                            indicatorColor = RegistreTheme.colors.sage,
                        ),
                    )
                }
            }
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
                )
            }
            composable("devoirs") {
                DevoirsScreen(container = container, padding = padding)
            }
            composable("documents") {
                DocumentsScreen(container = container, padding = padding)
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

/**
 * Le geste d'onglet du DESIGN.md §3 : on ne dépille rien, chaque onglet
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
