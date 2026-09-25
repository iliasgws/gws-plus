package school.greenwood.plus.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/*
 * Le composeur IA (issue #56) — un client minimal OpenAI-compatible
 * (`POST {base}/chat/completions`), BYOK : la clé vit dans les préférences
 * de l'app (jamais loguée — docs/security/SECURITY-NOTES.md, F3), l'utilisateur
 * choisit son fournisseur parmi des presets (OpenRouter, Groq, DeepSeek,
 * Mistral, Together, Fireworks, Cerebras) ou entre une URL libre.
 *
 * Aucune donnée scolaire ne part au-delà du texte que le parent compose
 * lui-même : seul le brouillon en cours (et sa consigne) est envoyé au
 * fournisseur choisi par l'utilisateur.
 */

/** Un preset de fournisseur OpenAI-compatible (issue #56). */
data class FournisseurIA(val nom: String, val base: String)

val PresetsFournisseurs = listOf(
    FournisseurIA("OpenRouter", "https://openrouter.ai/api/v1"),
    FournisseurIA("Groq", "https://api.groq.com/openai/v1"),
    FournisseurIA("DeepSeek", "https://api.deepseek.com/v1"),
    FournisseurIA("Mistral AI", "https://api.mistral.ai/v1"),
    FournisseurIA("Together AI", "https://api.together.xyz/v1"),
    FournisseurIA("Fireworks AI", "https://api.fireworks.ai/inference/v1"),
    FournisseurIA("Cerebras", "https://api.cerebras.ai/v1"),
)

/** Les tons prédéfinis du panneau (libellés français, prompts en clair). */
enum class TonIA(val libellé: String) {
    AMICAL("Amical"),
    PROFESSIONNEL("Professionnel"),
    CONCIS("Concis"),
}

/** Les actions du panneau, inspirées des Writing Tools d'Apple (libellés FR). */
enum class ActionIA(val libellé: String, val consigne: String) {
    RELIRE("Relire", "Corrige l'orthographe, la grammaire et la ponctuation du texte, sans changer le style ni le sens. Renvoie uniquement le texte corrigé."),
    RÉÉCRIRE("Réécrire", "Réécris le texte pour le rendre plus clair et plus fluide, en conservant son sens et sa langue. Renvoie uniquement le texte réécrit."),
    RÉSUMÉ("Résumé", "Résume le texte en quelques phrases naturelles. Renvoie uniquement le résumé."),
    POINTS("Points clés", "Extrais les points clés du texte sous forme de liste à puces courtes, une par ligne, sans numérotation. Renvoie uniquement la liste."),
    TABLEAU("Tableau", "S'il contient des données structurées, organise le texte en tableau markdown. Renvoie uniquement le tableau."),
    LISTE("Liste", "Réorganise le texte en liste à puces claire et ordonnée. Renvoie uniquement la liste."),
}

/** Réglages tels que stockés (SessionStore) et consommés par le composeur. */
data class RéglagesIA(
    val actif: Boolean = false,
    val base: String = PresetsFournisseurs.first().base,
    val modèle: String = "",
    val clé: String = "",
    val ton: TonIA = TonIA.AMICAL,
) {
    val prête: Boolean get() = actif && base.isNotBlank() && modèle.isNotBlank() && clé.isNotBlank()
}

/** Le prompt système complet pour une action et un ton donnés. */
fun promptSystème(action: ActionIA, ton: TonIA, consigne: String?): String {
    val tonTexte = when (ton) {
        TonIA.AMICAL -> "Ton : chaleureux et amical, adapté à un message destiné à l'école de son enfant."
        TonIA.PROFESSIONNEL -> "Ton : professionnel et respectueux, adapté à un message destiné à l'administration de l'école."
        TonIA.CONCIS -> "Ton : concis, va droit au but sans perdre la politesse."
    }
    return buildString {
        append("Tu aides un parent à rédiger un message à l'école de son enfant. ")
        append("Tu réponds toujours en français, avec les accents corrects. ")
        append(tonTexte)
        append(" ")
        append(action.consigne)
        if (!consigne.isNullOrBlank()) {
            append(" Consigne supplémentaire du parent : « ")
            append(consigne.trim())
            append(" ».")
        }
        append(" Ne renvoie jamais d'explication, de préambule ni de guillemets autour du résultat.")
    }
}

/** Le client — une seule instance paresseuse, comme BotiHttp. */
object ComposeurIA {

    private val json = Json { ignoreUnknownKeys = true }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    class ErreurIA(message: String) : Exception(message)

    /**
     * Appel OpenAI-compatible : POST {base}/chat/completions, une seule
     * requête, température basse (écrire, pas inventer). Le corps de la
     * réponse ne contient que le texte transformé.
     */
    suspend fun transformer(
        réglages: RéglagesIA,
        action: ActionIA,
        consigne: String?,
        texte: String,
    ): String = withContext(Dispatchers.IO) {
        if (texte.isBlank()) throw ErreurIA("Il n'y a pas de texte à transformer.")
        if (!réglages.prête) throw ErreurIA("Configure l'assistant IA dans les Paramètres.")

        val corps = buildJsonObject {
            put("model", réglages.modèle)
            put("temperature", 0.3)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", promptSystème(action, réglages.ton, consigne))
                    },
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", texte)
                    },
                )
            }
        }

        val url = réglages.base.trimEnd('/') + "/chat/completions"
        val requête = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${réglages.clé}")
            .post(
                corps.toString().toRequestBody("application/json".toMediaType()),
            )
            .build()

        val réponse = runCatching { http.newCall(requête).execute() }
            .getOrElse { throw ErreurIA("Réseau indisponible — réessaie.") }
        réponse.use { r ->
            val corpsBrut = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw ErreurIA(
                    if (r.code == 401) "Clé API refusée par le fournisseur."
                    else "Le fournisseur a répondu ${r.code}.",
                )
            }
            extraireContenu(corpsBrut)
                ?: throw ErreurIA("Réponse inattendue du fournisseur.")
        }
    }

    /** `choices[0].message.content` du format OpenAI, ou null si absent. */
    fun extraireContenu(corpsBrut: String): String? = runCatching {
        val arbre = json.parseToJsonElement(corpsBrut) as? JsonObject ?: return null
        val choix = arbre["choices"] as? JsonArray ?: return null
        val message = (choix.firstOrNull() as? JsonObject)?.get("message") as? JsonObject ?: return null
        (message["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
