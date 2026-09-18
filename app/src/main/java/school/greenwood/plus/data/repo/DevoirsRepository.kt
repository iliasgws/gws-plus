package school.greenwood.plus.data.repo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.util.extractDate
import school.greenwood.plus.util.extractDateTime
import java.time.LocalDate

/*
 * Les devoirs arrivent en trois seaux : `devoirs_remettre` (à rendre, items
 * parsés), `devoirs_ancien` (passés, parsés) et `data` (bruts, avec la date
 * de publication ISO). Les items parsés portent `date_remise` (échéance ISO,
 * vérifiée le 18/09/2026) ; les bruts portent la publication exacte. On
 * fusionne par id, on dédoublonne.
 */
class DevoirsRepository(private val client: BotiClient) {

    suspend fun liste(): List<Devoir> {
        val rep = client.get("devoirs", mapOf("start" to "0", "limit" to "60"))

        val brutes = publicationParId(rep)
        val parsés: List<JsonObject> = buildList {
            rep["devoirs_remettre"]?.let { addAll((it as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()) }
            rep["devoirs_ancien"]?.let { addAll((it as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()) }
        }
        val bruts: List<JsonObject> =
            (rep["data"] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()

        return if (parsés.isNotEmpty()) {
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
    }

    /** Filtre client sur l'échéance — le paramètre `date` du serveur est ignoré. */
    suspend fun pourDate(date: LocalDate): List<Devoir> =
        liste().filter { it.dateRemise == date }

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
