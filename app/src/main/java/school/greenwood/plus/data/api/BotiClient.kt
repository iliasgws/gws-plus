package school.greenwood.plus.data.api

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.RequestBody.Companion.asRequestBody
import school.greenwood.plus.data.session.SessionStore
import java.io.File

/*
 * Le client unique par lequel passent tous les appels. Il porte :
 * - l'enveloppe standard (user_id, key, parent_id, eleve_id, versionCode,
 *   versionNumber, paltform — sic, la coquille est dans le serveur — et lang),
 * - le décodage de l'enveloppe de réponse : HTTP 200 même en erreur, donc on
 *   branche sur le JSON ; « disconnect »: true → événement d'expiration global,
 *   « error »: true → BotiErreur avec le message du serveur.
 *
 * Aucun log de paramètres ni de jetons (docs/SECURITY-NOTES.md, F3).
 */

class BotiErreur(val messageUtilisateur: String) : Exception(messageUtilisateur)

/** Session tuée côté serveur : la navigation racine renvoie vers la connexion. */
class SessionExpirée : Exception()

class BotiClient(
    private val api: BotiApi,
    private val session: SessionStore,
) {

    private suspend fun baseParams(): Map<String, String> {
        val s = session.state.first()
        return buildMap {
            put("versionCode", "24140")
            put("versionNumber", "2.4.14")
            put("paltform", "android")
            put("lang", "fr")
            s?.let {
                put("key", it.keyToken)
                put("user_id", it.userId)
                put("parent_id", it.parentId)
                put("eleve_id", it.eleveId)
            }
        }
    }

    suspend fun get(endpoint: String, extra: Map<String, String> = emptyMap()): JsonObject {
        val params = baseParams() + extra
        return unwrap(api.get(endpoint, params), endpoint)
    }

    /**
     * GET brut, pour les réponses volumineuses qu'il ne faut pas matérialiser
     * en arbre JSON (admin_nouveautes ~19 Mo) : le lecteur est à fermer.
     * Pas de contrôle d'enveloppe ici — appel réservé aux gros flux de lecture.
     */
    suspend fun flux(endpoint: String, extra: Map<String, String> = emptyMap()): java.io.BufferedReader {
        val params = baseParams() + extra
        return java.io.BufferedReader(api.get(endpoint, params).charStream())
    }

    /**
     * POST multipart : champs plats ajoutés tels quels (la convention imbriquée
     * clé[sous-clé] du bundle d'origine n'est utile qu'aux formulaires riches,
     * hors v1), `key` en champ comme l'ApiService d'origine.
     *
     * [partiesMultiples] : le même nom de champ répété, une part par fichier —
     * la convention `files[]` du bundle (une part par pièce, nom conservé),
     * et l'audio envoyé sous `{file, name}` (bundle ApiService).
     */
    suspend fun post(
        endpoint: String,
        fields: Map<String, String> = emptyMap(),
        fichiers: Map<String, File> = emptyMap(),
        mimeType: String = "application/octet-stream",
        partiesMultiples: List<PartieFichier> = emptyList(),
    ): JsonObject {
        val base = baseParams()
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("key", base["key"] ?: "")
        fields.forEach { (k, v) -> builder.addFormDataPart(k, v) }
        fichiers.forEach { (champ, fichier) ->
            builder.addFormDataPart(
                champ,
                fichier.name,
                fichier.asRequestBody(mimeType.toMediaType()),
            )
        }
        partiesMultiples.forEach { partie ->
            builder.addFormDataPart(
                partie.champ,
                partie.nom,
                partie.fichier.asRequestBody(partie.mime.toMediaType()),
            )
        }
        val body: RequestBody = builder.build()
        return unwrap(api.post(endpoint, body), endpoint)
    }

    /** Une part de fichier répétée : `files[]` (pièces jointes) ou `audio`. */
    class PartieFichier(
        val champ: String,
        val fichier: File,
        val nom: String,
        val mime: String,
    )

    private suspend fun unwrap(body: ResponseBody, endpoint: String): JsonObject {
        val texte = runCatching { body.string() }.getOrDefault("")
        return when (val résultat = BotiEnvelope.analyser(texte, endpoint)) {
            BotiEnvelope.Résultat.Déconnecté -> {
                // Session tuée côté serveur : purge + événement global.
                session.effacer()
                session.signalerExpiration()
                throw SessionExpirée()
            }
            is BotiEnvelope.Résultat.Erreur -> throw BotiErreur(résultat.message)
            is BotiEnvelope.Résultat.Données -> résultat.objet
        }
    }
}
