package school.greenwood.plus.data.repo

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.DevoirDétail
import school.greenwood.plus.model.SoumissionDétail
import school.greenwood.plus.util.Fichiers
import school.greenwood.plus.util.extractDate
import school.greenwood.plus.util.extractDateTime
import java.io.File
import java.time.LocalDate

/*
 * Les devoirs arrivent en trois seaux : `devoirs_remettre` (à rendre, items
 * parsés), `devoirs_ancien` (passés, parsés) et `data` (bruts, avec la date
 * de publication ISO). Les items parsés portent `date_remise` (échéance ISO,
 * vérifiée le 18/09/2026) ; les bruts portent la publication exacte. On
 * fusionne par id, on dédoublonne.
 *
 * Le détail d'un devoir (issue #68) a SA propre réponse : GET `devoirs` avec
 * le param `devoir=<id>` — la page DevoirDetailsPage du bundle officiel —
 * qui seule porte les pièces jointes (`files[]`), l'état de soumission
 * (`devoir_fait{fait, file_sent, files}`) et les droits (`can_set_done`,
 * `can_add_files`, `show_files`). La soumission est un POST `devoirs_date_v2`
 * (définitif côté serveur : l'UI demande confirmation).
 */
class DevoirsRepository(
    private val client: BotiClient,
    private val caches: CachesSession,
    private val session: SessionStore,
) {

    suspend fun liste(): List<Devoir> {
        val rep = client.get("devoirs", mapOf("start" to "0", "limit" to "60"))

        val brutes = publicationParId(rep)
        val parsés: List<JsonObject> = buildList {
            rep["devoirs_remettre"]?.let { addAll((it as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()) }
            rep["devoirs_ancien"]?.let { addAll((it as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()) }
        }
        val bruts: List<JsonObject> =
            (rep["data"] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()

        val résultat = if (parsés.isNotEmpty()) {
            parsés.mapNotNull { o ->
                Normalizers.devoir(o, brutes[Normalizers.str(o, "id")])
            }.distinctBy { it.id }
        } else {
            // Aucun seau parsé : on repasse par les items bruts (champ `de`).
            bruts.mapNotNull { o ->
                Normalizers.devoir(o).let { d ->
                    d?.copy(dateRemise = d.dateRemise ?: Normalizers.str(o, "de")?.let(::extractDate))
                }
            }.distinctBy { it.id }
        }
        // Dernière liste connue (issue #21) — une liste vide est un état valide.
        val avecLocal = avecFaitLocal(résultat, session.devoirsFaitLocal.first())
        caches.clé()?.let { clé -> caches.devoirs.écrire(clé, avecLocal) }
        return avecLocal
    }

    /** Dernière liste connue, estampillée session — null si rien en cache ou
     *  si la session a changé depuis l'écriture. */
    suspend fun listeEnCache(): List<Devoir>? {
        val clé = caches.clé() ?: return null
        val liste = caches.devoirs.lire(clé) ?: return null
        // On réapplique le marquage local (issue #82) : les caches écrits avant
        // cette fonctionnalité ne le portent pas encore.
        return avecFaitLocal(liste, session.devoirsFaitLocal.first())
    }

    /** Filtre client sur l'échéance — le paramètre `date` du serveur est ignoré. */
    suspend fun pourDate(date: LocalDate): List<Devoir> =
        liste().filter { it.dateRemise == date }

    /** Payload détail (issue #68) — GET `devoirs&devoir=<id>` : pièces jointes,
     *  état de soumission et droits, absents des items de la liste. */
    suspend fun détail(id: String): DevoirDétail? {
        val rep = client.get("devoirs", mapOf("devoir" to id))
        if (Normalizers.str(rep, "id") == null) return null
        val infos = Normalizers.devoirDétail(rep) ?: return null
        val ids = session.devoirsFaitLocal.first()
        return if (id in ids) infos.copy(devoir = infos.devoir.copy(faitLocal = true)) else infos
    }

    /**
     * Marquage « fait pour moi » (issue #82) : bascule un devoir fait / pas
     * fait **localement** — rien ne part au serveur, les professeurs et
     * l'administration ne le voient pas. Réversible d'un clic, contrairement
     * au fait officiel (POST définitif). Retourne le nouvel état.
     */
    suspend fun basculerFaitLocal(id: String): Boolean {
        val fait = id !in session.devoirsFaitLocal.first()
        session.marquerDevoirFaitLocal(id, fait)
        rafraîchirCache(id) { it.copy(faitLocal = fait) }
        return fait
    }

    /**
     * Soumission d'un devoir (issue #68) — POST `devoirs_date_v2`, exactement
     * le fil officiel : champs `eleve_id`/`user_id`/`parent_id` et
     * `devoir` = JSON {id, files}. Les copies partent encodées en base64
     * dans le JSON (`base64File`), comme l'upload du bundle. Définitif :
     * l'UI demande confirmation avant d'appeler.
     */
    suspend fun soumettre(devoirId: String, copies: List<File> = emptyList()): SoumissionDétail {
        val s = session.state.first() ?: error("Session absente")
        val fichiers = copies.map { pièce ->
            buildMap {
                put("name", JsonPrimitive(pièce.name))
                put("extention", JsonPrimitive(pièce.extension))
                put("base64File", JsonPrimitive(Fichiers.enDataURL(pièce)))
            }
        }.map(::JsonObject)
        val rep = client.post(
            endpoint = "devoirs_date_v2",
            fields = mapOf(
                "eleve_id" to s.eleveId,
                "user_id" to s.userId,
                "parent_id" to s.parentId,
                "devoir" to JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(devoirId),
                        "files" to JsonArray(fichiers),
                    ),
                ).toString(),
            ),
        )
        return Normalizers.soumission(rep)
    }

    /** Répercute un changement d'état (fait / copies envoyées) dans la
     *  dernière liste connue, pour que le retour à l'onglet soit à jour. */
    suspend fun rafraîchirCache(id: String, transformer: (Devoir) -> Devoir) {
        val clé = caches.clé() ?: return
        val liste = caches.devoirs.lire(clé) ?: return
        caches.devoirs.écrire(clé, liste.map { if (it.id == id) transformer(it) else it })
    }

    private fun publicationParId(rep: JsonObject): Map<String, java.time.LocalDateTime> {
        val data = rep["data"] as? JsonArray ?: return emptyMap()
        return data.mapNotNull { item ->
            (item as? JsonObject)?.let { o ->
                val id = Normalizers.str(o, "id") ?: return@mapNotNull null
                val dateIso = Normalizers.str(o, "date")?.let { extractDateTime(it) }
                id to (dateIso ?: java.time.LocalDateTime.MIN)
            }
        }.toMap()
    }
}

/** Marquage local « fait pour moi » (issue #82) : les devoirs dont l'id est
 *  dans [idsMarqués] portent `faitLocal = true` — purement local, le serveur
 *  n'en sait rien. Fonction pure, testée dans `DevoirsFaitLocalTest`. */
internal fun avecFaitLocal(liste: List<Devoir>, idsMarqués: Set<String>): List<Devoir> {
    if (idsMarqués.isEmpty()) return liste
    return liste.map { if (it.id in idsMarqués) it.copy(faitLocal = true) else it }
}
