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
 *
 * Si le corps n'est PAS du JSON (page HTML d'interception, corps vide…),
 * l'erreur porte le drapeau « illisible » et un court extrait du corps :
 * une capture d'écran suffit alors à dire ce que le serveur a renvoyé
 * (issue #14) — sans jamais logger de paramètres ni de jetons.
 */
object BotiEnvelope {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Résultat {
        data class Données(val objet: JsonObject) : Résultat
        data class Erreur(val message: String, val illisible: Boolean = false) : Résultat
        data object Déconnecté : Résultat
    }

    fun analyser(texte: String, endpoint: String): Résultat {
        val element = runCatching { json.parseToJsonElement(texte) }.getOrElse {
            return Résultat.Erreur(messageIllisible(texte, endpoint), illisible = true)
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

    /**
     * Diagnostic lisible sans rien divulguer : un corps vide n'est pas une
     * page HTML, un extrait court (80 car., espaces compactés) distingue une
     * interception HTML d'une coupure. Les réponses du serveur ne portent ni
     * jeton ni paramètre de requête (docs/security/SECURITY-NOTES.md, F3).
     */
    internal fun messageIllisible(texte: String, endpoint: String): String {
        if (texte.isBlank()) return "Réponse vide du serveur ($endpoint)"
        val extrait = texte.trim().replace(Regex("\\s+"), " ").take(80)
        return "Réponse illisible du serveur ($endpoint) : « $extrait »"
    }
}
