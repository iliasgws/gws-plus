package school.greenwood.plus

import android.app.Application
import android.content.Context
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.api.BotiHttp
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.data.repo.AuthRepository
import school.greenwood.plus.data.repo.CoursRepository
import school.greenwood.plus.data.repo.DevoirsRepository
import school.greenwood.plus.data.repo.DemandesRepository
import school.greenwood.plus.data.repo.DocumentsRepository
import school.greenwood.plus.data.repo.MessagesRepository
import school.greenwood.plus.data.repo.NouveautesRepository
import school.greenwood.plus.data.repo.RegistreRepository
import school.greenwood.plus.data.session.SessionStore

/*
 * Conteneur manuel — un module :app, pas de framework DI (docs/product/DESIGN.md §5).
 */
class AppContainer(context: Context) {
    val session = SessionStore(context)
    private val api = BotiHttp.api()
    private val client = BotiClient(api, session)

    // Caches de dernière donnée connue, isolés par session (issue #21).
    val caches = CachesSession(session)

    val auth = AuthRepository(client, session, caches)
    val registre = RegistreRepository(client, session, caches)
    val cours = CoursRepository(client, caches)
    val devoirs = DevoirsRepository(client, caches)
    val nouveautes = NouveautesRepository(client, session, caches)
    val messages = MessagesRepository(client, session, caches)
    val demandes = DemandesRepository(client, caches)
    val documents = DocumentsRepository(client, session, caches)
}

class GwsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
