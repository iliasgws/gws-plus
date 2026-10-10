package school.greenwood.plus.data.cache

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import school.greenwood.plus.data.repo.MessagesPage
import school.greenwood.plus.data.repo.RegistreDuJour
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.model.Demande
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.FicheBibliotheque
import school.greenwood.plus.model.Post
import school.greenwood.plus.model.Ressource
import school.greenwood.plus.model.SemaineCours

/** Les caches de dernière donnée connue, tous isolés par la même clé de
 *  session — le couple « userId/eleveId » : changer de compte OU d'enfant
 *  invalide mécaniquement tout le contenu (issue #21). */
class CachesSession(
    private val session: SessionStore,
    /** Cache de dernière réponse JSON sur disque (hors-ligne) — null dans
     *  les tests JVM pur Kotlin. */
    private val disque: CacheDisque? = null,
) {

    val registre = MemoireSession<RegistreDuJour>()
    val devoirs = MemoireSession<List<Devoir>>()
    val documents = MemoireSession<List<Ressource>>()
    val bibliotheque = MemoireSession<List<FicheBibliotheque>>()
    val demandes = MemoireSession<List<Demande>>()
    val messages = MemoireSession<MessagesPage>()
    val posts = MemoireSession<List<Post>>()
    val cours = MemoireSession<SemaineCours>()

    /** Clé d'isolation courante ; null (jamais "") hors session. */
    suspend fun clé(): String? {
        val s = session.state.first() ?: return null
        if (s.userId.isBlank()) return null
        return "${s.userId}/${s.eleveId}"
    }

    /**
     * Dernière réponse JSON utile sur disque — pour rouvrir l'application
     * déjà remplie après un redémarrage hors connexion. Rien n'est écrit
     * hors session, ni quand « Rester connecté » est décoché (issue #140).
     */
    suspend fun écrireDisque(nom: String, données: JsonElement) {
        if (disque == null) return
        if (!(session.retenir.first())) return
        val clé = clé() ?: return
        disque.écrire(nom, clé, données)
    }

    /** Entrée disque de CETTE session ; null partout ailleurs, et sur tout
     *  fichier illisible (clé Keystore perdue, corruption). */
    suspend fun lireDisque(nom: String): JsonElement? {
        val clé = clé() ?: return null
        return disque?.lire(nom, clé)
    }

    /** Vide les caches — appelé à la connexion et à la déconnexion. */
    suspend fun vider() {
        disque?.vider()
        registre.vider()
        devoirs.vider()
        documents.vider()
        bibliotheque.vider()
        demandes.vider()
        messages.vider()
        posts.vider()
        cours.vider()
    }
}
