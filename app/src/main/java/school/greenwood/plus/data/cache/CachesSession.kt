package school.greenwood.plus.data.cache

import kotlinx.coroutines.flow.first
import school.greenwood.plus.data.repo.MessagesPage
import school.greenwood.plus.data.repo.RegistreDuJour
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.model.Demande
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Ressource

/** Les caches de dernière donnée connue, tous isolés par la même clé de
 *  session — le couple « userId/eleveId » : changer de compte OU d'enfant
 *  invalide mécaniquement tout le contenu (issue #21). */
class CachesSession(private val session: SessionStore) {

    val registre = MemoireSession<RegistreDuJour>()
    val devoirs = MemoireSession<List<Devoir>>()
    val documents = MemoireSession<List<Ressource>>()
    val demandes = MemoireSession<List<Demande>>()
    val messages = MemoireSession<MessagesPage>()

    /** Clé d'isolation courante ; null (jamais "") hors session. */
    suspend fun clé(): String? {
        val s = session.state.first() ?: return null
        if (s.userId.isBlank()) return null
        return "${s.userId}/${s.eleveId}"
    }

    /** Vide les cinq caches — appelé à la connexion et à la déconnexion. */
    suspend fun vider() {
        registre.vider()
        devoirs.vider()
        documents.vider()
        demandes.vider()
        messages.vider()
    }
}
