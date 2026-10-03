package school.greenwood.plus.data.repo

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonArray
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.api.BotiErreur
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.data.session.SessionState
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.model.Eleve
import school.greenwood.plus.model.ParentInfo

/*
 * Connexion, déconnexion, mot de passe oublié, validation de session au
 * démarrage (acces_check renvoie une clé fraîche + la liste d'élèves).
 */
class AuthRepository(
    private val client: BotiClient,
    private val session: SessionStore,
    private val caches: CachesSession,
    // Médias binaires de la session : mêmes moments de purge que les caches de
    // données (issue #108).
    private val purgeMedias: suspend () -> Unit = {},
) {

    suspend fun connexion(
        téléphone: String,
        motDePasse: String,
        retenir: Boolean,
    ): SessionState {
        val rep = client.post(
            "login",
            fields = mapOf(
                "login" to téléphone.trim(),
                "password" to motDePasse,
                // L'app d'origine expose une case « privacy » ; on n'expose pas :
                // le champ part à true, comme un parent qui accepte.
                "privacy" to "true",
                "remembreMe" to if (retenir) "true" else "false",
            ),
        )
        val keyToken = Normalizers.str(rep, "keyToken")
            ?: throw BotiErreur("Connexion refusée — vérifie ton numéro et ton mot de passe")
        val userId = Normalizers.str(rep, "userId") ?: ""
        val parentId = Normalizers.str(rep, "parentId") ?: ""
        val eleveId = Normalizers.str(rep, "eleveId") ?: ""
        val role = Normalizers.str(rep, "role")
        val parent = Normalizers.parent(rep["parent"] as? kotlinx.serialization.json.JsonObject)
        val eleves = (rep["eleves"] as? JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.let(Normalizers::eleve) }
            ?: emptyList()
        val eleveChoisi = eleveId.ifBlank { eleves.firstOrNull()?.id ?: "" }

        // Les caches de dernière donnée connue appartiennent à la session
        // précédente (issue #21) — purgés avant d'écrire la nouvelle. Idem
        // pour les médias binaires (issue #108).
        caches.vider()
        purgeMedias()
        session.enregistrer(
            keyToken = keyToken,
            userId = userId,
            parentId = parentId,
            eleveId = eleveChoisi,
            role = role,
            parent = parent,
            eleves = eleves,
            retenir = retenir,
        )
        return SessionState(keyToken, userId, parentId, eleveChoisi, role, parent, eleves)
    }

    /** Rappel du mot de passe par l'école. L'app ne crée pas de compte. */
    suspend fun motDePasseOublié(téléphone: String) {
        client.post(
            "forgot-password",
            fields = mapOf("login" to téléphone.trim()),
        )
    }

    suspend fun déconnexion() {
        runCatching { client.post("logout") }
        caches.vider()
        purgeMedias()
        session.effacer()
    }

    /**
     * Démarrage à froid avec une session en stock : acces_check valide la clé
     * et la rafraîchit (le serveur renvoie keyToken + userId + eleves).
     */
    suspend fun validerSession(): SessionState? {
        val courant = session.state.firstOrNull() ?: return null
        val rep = runCatching { client.get("acces_check") }.getOrElse { return null }
        // Une déconnexion ou un changement d'élève pendant la requête ne
        // doit jamais être annulé par une réponse arrivée en retard.
        val maintenant = session.state.firstOrNull()
        if (maintenant != courant) return maintenant
        val key = Normalizers.str(rep, "keyToken") ?: courant.keyToken
        val user = Normalizers.str(rep, "userId") ?: courant.userId
        val parentId = Normalizers.str(rep, "parentId").takeUnless { it.isNullOrBlank() } ?: courant.parentId
        val role = Normalizers.str(rep, "role") ?: courant.role
        val parent = Normalizers.parent(rep["parent"] as? kotlinx.serialization.json.JsonObject) ?: courant.parent
        val eleves = (rep["eleves"] as? JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.let(Normalizers::eleve) }
            ?.takeIf { it.isNotEmpty() } ?: courant.eleves
        val eleveId = courant.eleveId.takeIf { id -> eleves.any { it.id == id } }
            ?: Normalizers.str(rep, "eleveId").takeUnless { it.isNullOrBlank() }
            ?: eleves.firstOrNull()?.id.orEmpty()

        val rafraîchi = SessionState(key, user, parentId, eleveId, role, parent, eleves)
        if (rafraîchi != courant) {
            session.enregistrer(
                keyToken = key,
                userId = user,
                parentId = parentId,
                eleveId = eleveId,
                role = role,
                parent = parent,
                eleves = eleves,
                retenir = session.retenir.firstOrNull() ?: true,
            )
        }
        return rafraîchi
    }

    companion object {
        fun elevesDe(rep: kotlinx.serialization.json.JsonObject): List<Eleve> =
            (rep["eleves"] as? JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.let(Normalizers::eleve) }
                ?: emptyList()
    }
}
