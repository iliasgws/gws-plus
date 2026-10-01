package school.greenwood.plus.data.repo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import school.greenwood.plus.model.CorrectionHoraire
import school.greenwood.plus.model.DevoirSuggéré
import school.greenwood.plus.model.PièceJointeCommunautaire
import school.greenwood.plus.model.Mentions
import school.greenwood.plus.model.ProblèmeHoraire
import school.greenwood.plus.model.SectionMentions
import school.greenwood.plus.model.SignalementAbus
import school.greenwood.plus.util.extractDate

/*
 * Les réponses du serveur communautaire (APP.md §3) sont du JSON à clés
 * accentuées (« matière », « problèmeId », « état », « crééÀ »). Les champs
 * sans défaut (`id`, `votes`, `état`, `crééÀ`) figurent toujours, mais on
 * reste défensif : un champ absent donne une valeur raisonnable, jamais un
 * crash — sauf `id`, sans lequel la ligne n'existe pas.
 */

object CommunNormalizers {

    fun str(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    fun entier(obj: JsonObject, key: String): Int? =
        (obj[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    fun long(obj: JsonObject, key: String): Long? =
        (obj[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    /** Devoir suggéré — `dateRemise` facultative (clé absente si nulle). */
    fun devoirSuggéré(obj: JsonObject): DevoirSuggéré? {
        val id = long(obj, "id") ?: return null
        return DevoirSuggéré(
            id = id,
            auteurId = str(obj, "auteurId") ?: "",
            matière = str(obj, "matière") ?: "",
            contenu = str(obj, "contenu") ?: "",
            dateRemise = extractDate(str(obj, "dateRemise")),
            votes = entier(obj, "votes") ?: 0,
            crééÀ = long(obj, "crééÀ") ?: 0,
            piècesJointes = (obj["piecesJointes"] as? JsonArray).orEmpty().mapNotNull { item ->
                val file = item as? JsonObject ?: return@mapNotNull null
                val fileId = str(file, "id") ?: return@mapNotNull null
                val name = str(file, "nom") ?: "Pièce jointe"
                val path = str(file, "url") ?: return@mapNotNull null
                PièceJointeCommunautaire(
                    id = fileId,
                    nom = name,
                    type = str(file, "type") ?: "application/octet-stream",
                    taille = long(file, "taille") ?: 0L,
                    url = path,
                )
            },
        )
    }

    fun pièceJointe(obj: JsonObject): PièceJointeCommunautaire? {
        val id = str(obj, "id") ?: return null
        val url = str(obj, "url") ?: return null
        return PièceJointeCommunautaire(
            id = id,
            nom = str(obj, "nom") ?: "Pièce jointe",
            type = str(obj, "type") ?: "application/octet-stream",
            taille = long(obj, "taille") ?: 0L,
            url = url,
        )
    }

    /** Problème d'emploi du temps — `état` toujours servi, on le reprend tel quel. */
    fun problèmeHoraire(obj: JsonObject): ProblèmeHoraire? {
        val id = long(obj, "id") ?: return null
        return ProblèmeHoraire(
            id = id,
            auteurId = str(obj, "auteurId") ?: "",
            description = str(obj, "description") ?: "",
            date = extractDate(str(obj, "date")),
            état = str(obj, "état") ?: "ouvert",
            crééÀ = long(obj, "crééÀ") ?: 0,
        )
    }

    /** Correction — `problèmeId` facultatif (clé absente si nulle). */
    fun correctionHoraire(obj: JsonObject): CorrectionHoraire? {
        val id = long(obj, "id") ?: return null
        return CorrectionHoraire(
            id = id,
            auteurId = str(obj, "auteurId") ?: "",
            problèmeId = long(obj, "problèmeId"),
            description = str(obj, "description") ?: "",
            date = extractDate(str(obj, "date")),
            crééÀ = long(obj, "crééÀ") ?: 0,
        )
    }

    /** Signalement d'abus — `cible` sans accent (devoir/probleme/correction). */
    fun signalementAbus(obj: JsonObject): SignalementAbus? {
        val id = long(obj, "id") ?: return null
        return SignalementAbus(
            id = id,
            cible = str(obj, "cible") ?: "",
            cibleId = long(obj, "cibleId") ?: 0,
            raison = str(obj, "raison") ?: "",
            crééÀ = long(obj, "crééÀ") ?: 0,
        )
    }

    /** Notice légale — version + 5 sections à clés `id` stables. */
    fun mentions(élément: JsonElement): Mentions? {
        val obj = élément as? JsonObject ?: return null
        val sections = (obj["sections"] as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.mapNotNull { s ->
                val id = str(s, "id") ?: return@mapNotNull null
                SectionMentions(id = id, titre = str(s, "titre") ?: "", texte = str(s, "texte") ?: "")
            }
            ?: emptyList()
        val version = str(obj, "version") ?: return null
        return Mentions(version = version, sections = sections)
    }

    /** Une liste JSON → modèles, en écartant les lignes illisibles. */
    fun <T> liste(élément: JsonElement, parseur: (JsonObject) -> T?): List<T> =
        (élément as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.mapNotNull(parseur)
            ?: emptyList()
}
