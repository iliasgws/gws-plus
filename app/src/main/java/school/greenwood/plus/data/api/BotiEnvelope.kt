package school.greenwood.plus.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/*
 * L'enveloppe de réponse Boti, isolée pour être testable : le serveur renvoie
 * HTTP 200 même en erreur, donc on branche sur le JSON :
 * - « error »: true      → BotiErreur avec le message du serveur,
 * - « disconnect »: true → SessionExpirée (la clé a été tuée),
 * - sinon                → l'objet est exploitable.
 */
object BotiEnvelope {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Résultat {
        data class Données(val objet: JsonObject) : Résultat
        data class Erreur(val message: String) : Résultat
        data object Déconnecté : Résultat
    }

    fun analyser(texte: String, endpoint: String): Résultat {
        val element = runCatching { json.parseToJsonElement(texte) }.getOrElse {
            return Résultat.Erreur("Réponse illisible du serveur ($endpoint)")
        }
        val obj = element as? JsonObject
            ?: return Résultat.Erreur("Réponse inattendue ($endpoint)")

        fun champ(clef: String): JsonPrimitive? = obj[clef] as? JsonPrimitive

        if (champ("disconnect")?.booleanOrNull == true) return Résultat.Déconnecté
        if (champ("error")?.booleanOrNull == true) {
            return Résultat.Erreur(champ("msg")?.content ?: "Erreur du serveur")
        }
        return Résultat.Données(obj)
    }
}
