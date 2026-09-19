package school.greenwood.plus

import android.app.Application
import android.content.Context
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.api.BotiHttp
import school.greenwood.plus.data.repo.AuthRepository
import school.greenwood.plus.data.repo.DevoirsRepository
import school.greenwood.plus.data.repo.DemandesRepository
import school.greenwood.plus.data.repo.DocumentsRepository
import school.greenwood.plus.data.repo.MessagesRepository
import school.greenwood.plus.data.repo.NouveautesRepository
import school.greenwood.plus.data.repo.RegistreRepository
import school.greenwood.plus.data.session.SessionStore

/*
 * Conteneur manuel — un module :app, pas de framework DI (DESIGN.md §5).
 */
class AppContainer(context: Context) {
    val session = SessionStore(context)
    private val api = BotiHttp.api()
    private val client = BotiClient(api, session)

    val auth = AuthRepository(client, session)
    val registre = RegistreRepository(client, session)
    val devoirs = DevoirsRepository(client)
    val nouveautes = NouveautesRepository(client, session)
    val messages = MessagesRepository(client, session)
    val demandes = DemandesRepository(client)
    val documents = DocumentsRepository(client)
}

class GwsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
