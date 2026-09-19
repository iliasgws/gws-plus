package school.greenwood.plus.data.repo

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.model.Post
import java.util.concurrent.ConcurrentHashMap

/*
 * La liste `nouveautes` ne porte que titre, catégorie, date, intro, image et
 * fichiers — les corps (description, HTML) ne viennent QUE de
 * admin_nouveautes, une réponse d'environ 19 Mo qu'il ne faut ni poller ni
 * matérialiser en arbre JSON. On la lit en streaming (android.util.JsonReader),
 * on ne garde que id → description, une fois par session.
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

    suspend fun liste(): List<Post> {
        val rep = client.get("nouveautes", mapOf("start" to "0", "limit" to "30"))
        return Normalizers.listePosts(rep)
    }

    suspend fun épinglés(): List<Post> {
        val rep = runCatching { client.get("pinned_posts") }.getOrElse { return emptyList() }
        return Normalizers.listePosts(rep)
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
