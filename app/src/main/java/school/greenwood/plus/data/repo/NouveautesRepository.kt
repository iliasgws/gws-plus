package school.greenwood.plus.data.repo

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.model.Commentaire
import school.greenwood.plus.model.Post
import school.greenwood.plus.model.PostDetail
import java.util.concurrent.ConcurrentHashMap

/*
 * Dépôt des actualités de l'école (flux `nouveautes`).
 *
 * Pagination 1-based (vérifiée 2026-09-20) : départ = 0 ou longueur cumulée + 1.
 * Le détail unitaire arrive via GET `post_view?post=<id>`.
 * Le flux admin_nouveautes (~19 Mo) est lu en streaming en repli.
 */
class NouveautesRepository(
    private val client: BotiClient,
    private val session: SessionStore,
    private val caches: CachesSession,
) {

    private val corps = ConcurrentHashMap<String, String>()
    private var chargés = false

    /** Clé de session à laquelle `corps` a été rempli (issue #21) — changer
     *  de compte ou d'enfant invalide le contenu du flux admin. */
    private var cléCorps: String? = null

    suspend fun liste(départ: Int = 0, limite: Int = 10): List<Post> {
        val startVal = if (départ == 0) "0" else départ.toString()
        val rep = client.get("nouveautes", mapOf("start" to startVal, "limit" to limite.toString()))
        val résultat = Normalizers.listePosts(rep)
        if (départ == 0) {
            caches.clé()?.let { clé -> caches.posts.écrire(clé, résultat) }
        }
        return résultat
    }

    suspend fun listeEnCache(): List<Post>? {
        val clé = caches.clé() ?: return null
        return caches.posts.lire(clé)
    }

    suspend fun épinglés(): List<Post> {
        val rep = runCatching { client.get("pinned_posts") }.getOrElse { return emptyList() }
        return Normalizers.listePosts(rep)
    }

    /**
     * Détail d'une actualité via GET `post_view?post=<id>`.
     * Le serveur marque de facto la visite comme lue.
     * En cas d'échec ou d'absence de corps, on retombe sur le cache de liste
     * et le flux `admin_nouveautes` extrait en streaming.
     */
    suspend fun détail(postId: String): PostDetail {
        val repDetail = runCatching {
            client.get("post_view", mapOf("post" to postId))
        }.getOrNull()

        val parsed = repDetail?.let { Normalizers.postDetail(it) }

        if (parsed != null && !parsed.descriptionHtml.isNullOrBlank()) {
            return parsed
        }

        // Repli : post de la liste + corps issu de admin_nouveautes (~19 Mo)
        val postListe = listeEnCache()?.firstOrNull { it.id == postId }
        val corpsRepli = corps(postId)

        if (parsed != null) {
            return parsed.copy(descriptionHtml = parsed.descriptionHtml ?: corpsRepli)
        }

        if (postListe != null) {
            return PostDetail(
                id = postListe.id,
                title = postListe.title,
                categorie = postListe.categorie,
                date = postListe.date,
                intro = postListe.intro,
                descriptionHtml = corpsRepli ?: postListe.description,
                image = postListe.image,
                bookmark = postListe.bookmark,
                auteur = postListe.auteur,
                files = postListe.attachments,
                images = emptyList(),
                commentaires = emptyList(),
                peutCommenter = postListe.permitComments,
                peutNouveauCommentaire = postListe.permitNewComments,
                peutRépondre = false,
                questions = emptyList(),
            )
        }

        return PostDetail(
            id = postId,
            title = "Actualité",
            descriptionHtml = corpsRepli,
        )
    }

    /**
     * Sélectionne la dernière actualité par date parmi la première page
     * (utilisée pour la carte « Dernière actualité » du registre).
     */
    suspend fun dernière(): Post? {
        val enCache = listeEnCache()
        val posts = if (!enCache.isNullOrEmpty()) enCache else runCatching {
            liste(départ = 0, limite = 10)
        }.getOrElse { enCache ?: emptyList() }
        return Normalizers.dernière(posts)
    }

    /**
     * Publie un commentaire ou une réponse à un commentaire existant.
     * NB : cette action est protégée par les drapeaux serveur et le kill switch de session.
     */
    suspend fun commenter(postId: String, texte: String, replyTo: String? = null): Commentaire? {
        val s = session.state.first()
        val fields = buildMap {
            put("commentaire", texte)
            put("post", postId)
            put("eleve_id", s?.eleveId ?: "")
            put("user_id", s?.userId ?: "")
            put("parent_id", s?.parentId ?: "")
            if (!replyTo.isNullOrBlank()) put("replyTo", replyTo)
        }
        val rep = client.post("nouveautes", fields)
        val comObj = (rep["commentaire"] as? JsonObject) ?: rep
        return Normalizers.commentaire(comObj) ?: Commentaire(
            auteur = s?.parent?.nomComplet ?: "Moi",
            texte = texte,
            date = java.time.LocalDateTime.now(),
        )
    }

    /**
     * Répond à une question d'un quiz associé à un post d'actualité.
     */
    suspend fun répondreQuestionQuiz(postId: String, alias: String, réponse: String): JsonObject {
        val s = session.state.first()
        val fields = mapOf(
            "alias_question" to alias,
            "res" to réponse,
            "post" to postId,
            "eleve_id" to (s?.eleveId ?: ""),
            "user_id" to (s?.userId ?: ""),
            "parent_id" to (s?.parentId ?: ""),
        )
        return client.post("nouveautes", fields)
    }

    /** Corps HTML d'un post, à la demande (cache mémoire de session). */
    suspend fun corps(postId: String): String? {
        val clé = caches.clé()
        if (clé != cléCorps) {
            // La session (ou l'enfant choisi) a changé depuis le remplissage :
            // les corps appartiennent à l'ancien compte, on repart de zéro.
            corps.clear()
            chargés = false
            cléCorps = clé
        }
        if (!chargés) {
            runCatching { chargerCorps() }.onSuccess {
                corps.putAll(it)
                chargés = true
            }
        }
        return corps[postId]
    }

    private suspend fun chargerCorps(): Map<String, String> {
        val s = session.state.first()
        val reader = client.flux(
            "admin_nouveautes",
            mapOf(
                "parent_id" to (s?.parentId ?: ""),
                "eleve_id" to (s?.eleveId ?: ""),
            ),
        )
        reader.use {
            return extraireCorps(JsonReader(it))
        }
    }

    companion object {
        /** Lecture en flux d'une réponse admin_nouveautes → map id→description. */
        fun extraireCorps(reader: JsonReader): Map<String, String> {
            val out = mutableMapOf<String, String>()
            reader.use {
                it.beginObject()
                while (it.hasNext()) {
                    if (it.nextName() == "data" && it.peek() == JsonToken.BEGIN_ARRAY) {
                        it.beginArray()
                        while (it.hasNext()) lireUnPost(it, out)
                        it.endArray()
                    } else {
                        it.skipValue()
                    }
                }
                it.endObject()
            }
            return out
        }

        private fun lireUnPost(reader: JsonReader, out: MutableMap<String, String>) {
            var id: String? = null
            var description: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> if (reader.peek() == JsonToken.STRING) id = reader.nextString() else reader.skipValue()
                    "description" -> if (reader.peek() == JsonToken.STRING) description = reader.nextString() else reader.skipValue()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (id != null && !description.isNullOrBlank()) out[id] = description
        }
    }
}
