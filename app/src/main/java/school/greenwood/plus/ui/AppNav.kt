package school.greenwood.plus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import school.greenwood.plus.AppContainer
import school.greenwood.plus.ui.components.AuroraBackdrop
import school.greenwood.plus.ui.components.GlassBottomBar
import school.greenwood.plus.ui.components.GlassDefaults
import school.greenwood.plus.ui.components.LocalGlassBackdrop
import school.greenwood.plus.ui.components.VerreOnglet
import school.greenwood.plus.ui.screens.LoginScreen
import school.greenwood.plus.ui.screens.OnboardingScreen
import school.greenwood.plus.ui.screens.devoirs.DevoirsScreen
import school.greenwood.plus.ui.screens.demandes.DemandesScreen
import school.greenwood.plus.ui.screens.documents.DocumentsScreen
import school.greenwood.plus.ui.screens.documents.QuizScreen
import school.greenwood.plus.ui.screens.messages.ConversationScreen
import school.greenwood.plus.ui.screens.messages.MessagesScreen
import school.greenwood.plus.ui.screens.messages.NouveauMessageScreen
import school.greenwood.plus.ui.screens.registre.PostDetailScreen
import school.greenwood.plus.ui.screens.registre.RegistreScreen

/*
 * Navigation racine. Trois états pilotés par la session (pas un NavController
 * racine) : onboarding → connexion → registre. Quand le serveur tue la session
 * (« disconnect »), l'état retombe sur la connexion, où que l'on soit.
 *
 * Le verre (docs/product/DESIGN.md §2) ne capture que l'aurore immobile.
 * Les écrans et leurs listes paresseuses restent hors de tout GraphicsLayer
 * backdrop ; la barre basse emploie sa capsule calme sans capture dynamique.
 *
 * Onglets (docs/product/DESIGN.md §3) : chaque onglet garde sa pile via
 * saveState/restoreState, retour depuis un onglet racine → Registre,
 * retour depuis le Registre → quitter l'app. Contrat inchangé.
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

    // La barre flottante vit à côté de la coquille (l'échantillonnage de verre
    // a besoin de la couche capturée partagée), donc le NavController et la
    // route courante remontent ici.
    val navController = rememberNavController()
    val destinationCourante by navController.currentBackStackEntryAsState()
    val routeCourante = destinationCourante?.destination?.route
    val ongletCourant = ongletPourRoute(routeCourante)

    // Une seule capture stable : l'aurore immobile. Capturer la scène
    // complète retenait aussi les LazyColumn ; leur recyclage pendant un
    // défilement pouvait laisser des coordonnées détachées dans backdrop.
    val fondAurore = rememberLayerBackdrop()
    val portée = rememberCoroutineScope()

    CompositionLocalProvider(LocalGlassBackdrop provides fondAurore) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AuroraBackdrop(Modifier.layerBackdrop(fondAurore))
                Crossfade(targetState = écran, animationSpec = tween(250), label = "racine") { é ->
                    when (é) {
                        ÉcranRacine.Chargement -> {}
                        ÉcranRacine.Onboarding -> OnboardingScreen(
                            onFini = { portée.launch { container.session.marquerOnboardingVu() } },
                        )
                        ÉcranRacine.Connexion -> LoginScreen(
                            onConnecté = { /* l'état de session bascule vers le registre */ },
                            container = container,
                        )
                        ÉcranRacine.Registre -> Shell(container = container, navController = navController)
                    }
                }
            }

            // Barre flottante de verre : hors de la couche capturée, visible
            // seulement dans le registre.
            AnimatedVisibility(
                visible = écran == ÉcranRacine.Registre,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it },
            ) {
                GlassBottomBar(
                    onglets = Onglets.map { onglet ->
                        VerreOnglet(
                            icône = onglet.icone,
                            libellé = onglet.label,
                            sélectionné = ongletCourant == onglet.route,
                            onClick = { navController.allerÀLOnglet(onglet.route) },
                        )
                    },
                    // Barre calme sans capture dynamique : aucun GraphicsLayer
                    // ne dépend du contenu paresseux qui défile.
                    backdrop = null,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** La coquille : piles d'onglets sous une barre flottante. Le Scaffold ne
 *  dessine plus rien (transparent, pas de barre) — il ne porte que les insets,
 *  et complète le bas de la hauteur de la barre de verre pour que les
 *  écrans défilent sous elle. */
@Composable
fun Shell(
    container: AppContainer,
    navController: NavHostController,
) {
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
        containerColor = Color.Transparent,
        bottomBar = {},
    ) { padding ->
        val paddingÉcrans = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + GlassDefaults.BarTotal,
        )
        NavHost(
            navController = navController,
            startDestination = "registre",
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 24 } },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(140)) + slideOutVertically(tween(200)) { it / 24 } },
        ) {
            composable("registre") {
                RegistreScreen(
                    container = container,
                    padding = paddingÉcrans,
                    ouvrirDemandes = { navController.allerDétail("demandes") },
                    ouvrirPost = { id -> navController.allerDétail("post/$id") },
                )
            }
            composable("devoirs") {
                DevoirsScreen(container = container, padding = paddingÉcrans)
            }
            composable("documents") {
                DocumentsScreen(
                    container = container,
                    padding = paddingÉcrans,
                    onOuvrirQuiz = { id -> navController.allerDétail("quiz/$id") },
                )
            }
            composable("quiz/{quizId}") { entrée ->
                QuizScreen(
                    container = container,
                    padding = paddingÉcrans,
                    quizId = entrée.arguments?.getString("quizId") ?: "",
                    retour = { navController.popBackStack() },
                )
            }
            composable("messages") {
                MessagesScreen(
                    container = container,
                    padding = paddingÉcrans,
                    onOuvrirConversation = { id -> navController.allerDétail("conversation/$id") },
                    onNouveauMessage = { navController.allerDétail("nouveau-message") },
                )
            }
            composable("conversation/{conversationId}") { entrée ->
                ConversationScreen(
                    container = container,
                    padding = paddingÉcrans,
                    conversationId = entrée.arguments?.getString("conversationId") ?: "",
                    retour = { navController.popBackStack() },
                )
            }
            composable("nouveau-message") {
                NouveauMessageScreen(
                    container = container,
                    padding = paddingÉcrans,
                    retour = { navController.popBackStack() },
                    onEnvoyé = { navController.popBackStack() },
                )
            }
            composable("demandes") {
                DemandesScreen(
                    container = container,
                    padding = paddingÉcrans,
                    retour = { navController.popBackStack() },
                )
            }
            composable("post/{postId}") { entrée ->
                PostDetailScreen(
                    container = container,
                    padding = paddingÉcrans,
                    postId = entrée.arguments?.getString("postId") ?: "",
                    retour = { navController.popBackStack() },
                )
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

/** Associe chaque détail à l'onglet qui porte sa pile. La barre basse garde
 *  ainsi une source de vérité même quand la destination visible n'est pas
 *  elle-même une racine d'onglet. */
internal fun ongletPourRoute(route: String?): String? = when (route?.substringBefore('/')) {
    "registre", "demandes", "post" -> "registre"
    "devoirs" -> "devoirs"
    "documents", "quiz" -> "documents"
    "messages", "conversation", "nouveau-message" -> "messages"
    else -> null
}
