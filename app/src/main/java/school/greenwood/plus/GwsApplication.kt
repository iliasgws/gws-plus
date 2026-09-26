package school.greenwood.plus

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.api.BotiHttp
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.data.repo.AuthRepository
import school.greenwood.plus.data.repo.BoutiqueRepository
import school.greenwood.plus.data.repo.CoursRepository
import school.greenwood.plus.data.repo.DevoirsRepository
import school.greenwood.plus.data.repo.DemandesRepository
import school.greenwood.plus.data.repo.DocumentsRepository
import school.greenwood.plus.data.repo.MessagesRepository
import school.greenwood.plus.data.repo.NouveautesRepository
import school.greenwood.plus.data.repo.RegistreRepository
import school.greenwood.plus.data.repo.UpdatesRepository
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.data.session.VeilleSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * Conteneur manuel Android ; les dépôts viennent du module partagé :composeApp.
 */
private val Context.sessionDataStore by preferencesDataStore(name = "gws_session")

class AppContainer(context: Context) {
    val session = SessionStore(context.sessionDataStore)
    private val api = BotiHttp.api()
    private val client = BotiClient(api, session)

    // Caches de dernière donnée connue, isolés par session (issue #21).
    val caches = CachesSession(session)

    // Signal « un quiz est en cours de jeu » (issue #17) : piloté par le
    // QuizViewModel, observé par la coquille — sortie d'un quiz en jeu
    // (onglet, retour) doit être confirmée, le score d'un quiz abandonné
    // n'est pas enregistré.
    val quizEnJeu = MutableStateFlow(false)

    // Signal « un devoir vient d'être modifié » (issue #68) : émis par le
    // détail après une soumission, collecté par DevoirsViewModel pour
    // rafraîchir la liste — le marquage « fait » se voit au retour.
    val devoirsModifiés = MutableSharedFlow<String>(extraBufferCapacity = 4)

    // Veille de l'app : au retour au premier plan après une absence plus
    // longue que la durée réglée dans les Paramètres, chaque écran chargé
    // rafraîchit en silence — le contenu connu reste affiché, jamais de
    // remise à zéro.
    val veille = VeilleSession()

    val auth = AuthRepository(client, session, caches)
    val registre = RegistreRepository(client, session, caches)
    val cours = CoursRepository(client, caches)
    val devoirs = DevoirsRepository(client, caches, session)
    val nouveautes = NouveautesRepository(client, session, caches)
    val messages = MessagesRepository(client, session, caches)
    val demandes = DemandesRepository(client, caches)
    val documents = DocumentsRepository(client, session, caches)
    val boutique = BoutiqueRepository(client, session)

    // Mises à jour de l'app — GitHub Releases, sans serveur (issue #46).
    val misesÀJour = UpdatesRepository(context, session)
}

class GwsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
