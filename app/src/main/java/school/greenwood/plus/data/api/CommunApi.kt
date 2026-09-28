package school.greenwood.plus.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit

/*
 * Client JSON du serveur communautaire (gws-community-server) — protocole
 * APP.md de ce dépôt, entièrement séparé de BotiClient (form-data + enveloppe
 * Boti) : ici du JSON brut, lectures PUBLIQUES sans jeton, écritures avec
 * `Authorization: Bearer` et des codes HTTP qui portent le sens (401 jeton à
 * recréer, 409 déjà signalé, 429 débit…). Les messages d'erreur sont du texte
 * brut : on les affiche tels quels, ou un libellé générique par code.
 *
 * Aucun log de jeton ni de paramètre (docs/security/SECURITY-NOTES.md, F3).
 */

/** Erreur HTTP du serveur communautaire : code + message texte brut. */
open class CommunErreur(val code: Int, override val message: String) : Exception(message)

/** 401 — jeton absent, inconnu ou révoqué : le dépôt en recrée un seul (jamais en boucle). */
class JetonRévoqué : CommunErreur(401, "Session communautaire expirée")

/** 429 — budget de débit atteint ; [attenteSecondes] = en-tête `Retry-After`. */
class DépassementDébit(val attenteSecondes: Long?) : CommunErreur(
    429,
    attenteSecondes?.let { "Trop de requêtes — réessaie dans $it s" }
        ?: "Trop de requêtes — réessaie dans un instant",
)

/** URL de base absente ou sans schéma : réglée dans les Paramètres (issue #88). */
class ServeurNonConfiguré : CommunErreur(
    0,
    "Serveur communautaire à régler dans les Paramètres",
)

/** Corps écrit plus gros que la limite serveur (10 Ko) : tronqué côté client,
 *  jamais envoyé — le serveur répondrait 413. */
class CorpsTropLourd : CommunErreur(413, "Message trop long — raccourcis-le avant l'envoi")

/**
 * Normalise l'URL saisie dans les Paramètres : espaces et « /» de fin
 * retirés, schéma ajouté quand il manque (`http://` pour les hôtes locaux —
 * serveur de test sur la machine ou l'émulateur —, `https://` ailleurs).
 * Renvoie "" si l'entrée est vide ou inexploitable : laissée vide, l'app
 * affiche « serveur à régler » au lieu d'essayer de se connecter.
 */
fun normaliserUrlServeur(brut: String): String {
    val entrée = brut.trim()
    if (entrée.isEmpty()) return ""
    val schéma = when {
        entrée.startsWith("https://") -> "https://"
        entrée.startsWith("http://") -> "http://"
        else -> {
            val local = entrée.startsWith("localhost") ||
                entrée.startsWith("127.0.0.1") ||
                entrée.startsWith("10.0.2.2") ||
                entrée.startsWith("[::1]")
            if (local) "http://" else "https://"
        }
    }
    // Hôte nu : on retire les « /» de fin SANS toucher au schéma.
    val corps = entrée
        .removePrefix("https://")
        .removePrefix("http://")
        .trim()
        .trimEnd('/')
    if (corps.isEmpty()) return ""
    val complet = schéma + corps
    return if (complet.toHttpUrlOrNull() != null) complet else ""
}

/** Réponse d'une lecture : corps JSON + `X-Total-Count` (null si absent —
 *  les signalements ne paginent pas). */
data class Lecture(val corps: JsonElement, val total: Int?)

/**
 * Paramètres des listes publiques (APP.md §3). Les accents des noms sont
 * facultatifs côté client : on envoie les formes sans accent (`matiere`,
 * `problemeId`, `etat`) que le serveur accepte comme les autres. Une valeur
 * mal formée vaut 400 côté serveur — jamais une page vide silencieuse ; on
 * borne donc `limite`/`offset` avant d'envoyer.
 */
object CommunParams {

    const val LIMITE_DÉFAUT = 50
    const val LIMITE_MAXIMALE = 500

    fun listes(
        tri: String? = null,
        matière: String? = null,
        date: String? = null,
        état: String? = null,
        problèmeId: Long? = null,
        depuis: Long? = null,
        limite: Int = LIMITE_DÉFAUT,
        offset: Int = 0,
    ): Map<String, String> = buildMap {
        tri?.trim()?.takeIf { it.isNotEmpty() }?.let { put("tri", it) }
        matière?.trim()?.takeIf { it.isNotEmpty() }?.let { put("matiere", it) }
        date?.trim()?.takeIf { it.isNotEmpty() }?.let { put("date", it) }
        état?.trim()?.takeIf { it.isNotEmpty() }?.let { put("etat", it) }
        problèmeId?.let { put("problemeId", it.toString()) }
        depuis?.let { put("depuis", it.toString()) }
        put("limite", limite.coerceIn(1, LIMITE_MAXIMALE).toString())
        put("offset", offset.coerceAtLeast(0).toString())
    }
}

/**
 * Identifiant pseudonyme d'un auteur : SHA-256 du jeton, 16 caractères
 * hexadécimaux — la même empreinte que `auteurId()` du serveur
 * (gws-community-server, Main.kt). Elle sert à reconnaître NOS contenus
 * (suppression réservée à l'auteur, vote interdit pour soi) sans jamais
 * envoyer le jeton pour ça. Si le serveur change un jour d'empreinte, on ne
 * reconnaît plus rien : les boutons concernés disparaissent — jamais à tort.
 */
fun auteurIdDuJeton(jeton: String): String =
    HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(jeton.toByteArray(Charsets.UTF_8)))
        .take(16)

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

open class CommunApi(
    /** URL de base courante (réglée dans les Paramètres) — relue à chaque appel. */
    private val base: suspend () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** `GET` public — renvoie le corps JSON et `X-Total-Count` (les listes). */
    open suspend fun lecture(
        chemin: String,
        params: Map<String, String> = emptyMap(),
    ): Lecture {
        val url = urlBase(chemin).newBuilder().apply {
            params.forEach { (nom, valeur) -> addQueryParameter(nom, valeur) }
        }.build()
        val réponse = appeler(Request.Builder().url(url).get().build())
        val corps = réponse.corps
        if (corps.isBlank()) throw CommunErreur(500, "Réponse vide du serveur communautaire")
        val élément = runCatching { json.parseToJsonElement(corps) }.getOrElse {
            throw CommunErreur(500, "Réponse illisible du serveur communautaire")
        }
        return Lecture(élément, réponse.total)
    }

    /**
     * `POST` / `DELETE` authentifiés. [jeton] null = sans en-tête (réservé à
     * `POST /compte`, la seule écriture sans jeton). `204` → null. Le corps
     * est borné à la limite serveur (10 Ko) avant envoi.
     */
    open suspend fun écriture(
        méthode: String,
        chemin: String,
        corps: JsonObject?,
        jeton: String?,
    ): JsonElement? {
        val texte = corps?.toString()
        if (texte != null && texte.toByteArray(Charsets.UTF_8).size > 10 * 1024) {
            throw CorpsTropLourd()
        }
        val requête = Request.Builder().url(urlBase(chemin)).apply {
            header("Accept", "application/json")
            jeton?.let { header("Authorization", "Bearer $it") }
            when (méthode) {
                "POST" -> {
                    header("Content-Type", "application/json")
                    post(texte?.toRequestBody(JSON_MEDIA) ?: ByteArray(0).toRequestBody(null))
                }
                "DELETE" -> delete()
                else -> error("Méthode non supportée : $méthode")
            }
        }.build()
        val réponse = appeler(requête)
        if (réponse.corps.isBlank()) return null
        return runCatching { json.parseToJsonElement(réponse.corps) }.getOrNull()
    }

    /** `GET /health` — test de disponibilité silencieux (true = joignable). */
    open suspend fun santé(): Boolean = try {
        appeler(Request.Builder().url(urlBase("health")).get().build()).code == 200
    } catch (err: CommunErreur) {
        false
    } catch (err: IOException) {
        false
    }

    // — Mécanique ----------------------------------------------------------

    /** URL de base normalisée, chemin posé segment par segment. */
    private suspend fun urlBase(chemin: String): okhttp3.HttpUrl {
        val url = normaliserUrlServeur(base()).toHttpUrlOrNull() ?: throw ServeurNonConfiguré()
        return url.newBuilder().apply {
            chemin.split("/").filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
        }.build()
    }

    private data class RéponseHttp(val code: Int, val corps: String, val total: Int?)

    private suspend fun appeler(requête: Request): RéponseHttp {
        val réponse = try {
            withContext(Dispatchers.IO) { client.newCall(requête).execute() }
        } catch (err: IOException) {
            throw err // réseau coupé : message générique géré par l'appelant
        }
        réponse.use { r ->
            val corps = runCatching { r.body.string() }.getOrDefault("")
            if (!r.isSuccessful) {
                traiterÉchec(r.code, corps, r.header("Retry-After"))
            }
            return RéponseHttp(r.code, corps, r.header("X-Total-Count")?.toIntOrNull())
        }
    }

    /** Tous les codes d'erreur du serveur deviennent une exception typée. */
    private fun traiterÉchec(code: Int, corps: String, retryAfter: String?): Nothing = when (code) {
        401 -> throw JetonRévoqué()
        429 -> throw DépassementDébit(retryAfter?.trim()?.toLongOrNull())
        else -> throw CommunErreur(code, corps.ifBlank { libellé(code) })
    }

    /** Libellé générique par code, pour les réponses sans message texte. */
    private fun libellé(code: Int): String = when (code) {
        400 -> "Requête invalide"
        403 -> "Action non autorisée"
        404 -> "Contenu introuvable"
        409 -> "Déjà signalé"
        413 -> "Message trop long"
        else -> "Erreur serveur ($code)"
    }
}
