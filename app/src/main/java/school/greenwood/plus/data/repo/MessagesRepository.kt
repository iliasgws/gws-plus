package school.greenwood.plus.data.repo

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.model.ContactEcole
import school.greenwood.plus.model.Demande
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Message
import school.greenwood.plus.model.SocialLink
import school.greenwood.plus.model.ThemeMessage
import java.io.File
import java.time.LocalDateTime

/*
 * Messages avec l'administration. Lecture (fils embarqués dans GET `messages`)
 * et — issue #10, seconde partie — écriture par POST `nouveau-message`, dont
 * les champs ont été lus dans le bundle officiel 2.4.14 (docs/product/ROADMAP.md, statique) :
 * - réponse à un fil : ref, sujet, message, theme (= theme du fil), files[],
 *   eleve_id, parent_id, key, audio, index (longueur du fil avant le push
 *   optimiste ; la réponse `.message` remplace l'élément en attente) ;
 * - nouveau fil : sujet, message, theme (choisi dans themes[]), eleve (imbriqué,
 *   vestigial), file ("null", vestigial — l'uploader d'origine est du code mort),
 *   eleve_id, parent_id, user_id, key. Limite officielle : 1 Mo par pièce.
 * L'envoi réel reste à valider une fois (kill switch côté app, désactivé par
 * défaut) — jusqu'à ce test, un envoi raté resterait muet pour l'école.
 */

/** Réponse du GET `messages` : les fils plus les métadonnées du composeur. */
data class MessagesPage(
    val conversations: List<Conversation>,
    val themes: List<ThemeMessage>,
)

class MessagesRepository(
    private val client: BotiClient,
    private val session: SessionStore,
    private val caches: CachesSession,
) {

    /**
     * Cache TTL court sur la grosse réponse `GET messages` (issue #14) :
     * Registre, l'onglet Messages et Nouveau message en ont besoin chacun de
     * leur côté — à moins de 45 s d'écart, la même page suffit au lieu de
     * recharger. `fraîche = true` (bouton Réessayer, ouverture d'un fil)
     * ignore le cache : les URLs signées rajeunissent alors. La page est
     * estampillée session (issue #21) : changer de compte ou d'enfant la
     * rend mécaniquement introuvable.
     */
    suspend fun conversations(fraîche: Boolean = false): MessagesPage {
        val clé = caches.clé()
        val cache = clé?.let { caches.messages.lire(it) }
        val maintenant = System.currentTimeMillis()
        if (!fraîche && cache != null && maintenant - cacheÉpoque < TtlCacheMs) return cache
        val rep = client.get("messages", mapOf("page" to "1"))
        return parserPage(rep).also { page ->
            clé?.let { caches.messages.écrire(it, page) }
            caches.écrireDisque("messages", rep)
            cacheÉpoque = maintenant
        }
    }

    /** Réponse brute `GET messages` → page normalisée (fils + thèmes). */
    private fun parserPage(rep: JsonObject) = MessagesPage(
        conversations = Normalizers.arr(rep, "data")
            .mapNotNull { (it as? JsonObject)?.let(Normalizers::conversation) }
            .filter { it.messages.isNotEmpty() }
            .sortedByDescending { it.dernierDate ?: LocalDateTime.MIN },
        themes = Normalizers.themes(rep),
    )

    /** Dernière page connue, estampillée session, SANS condition de TTL —
     *  pour servir du contenu malgré un échec serveur au lieu d'un mur
     *  d'erreur (lecture « stale-while-revalidate »). */
    suspend fun conversationsEnCache(): MessagesPage? {
        val clé = caches.clé() ?: return null
        caches.messages.lire(clé)?.let { return it }
        // Redémarrage hors connexion : la page scellée repeuple la mémoire.
        val brut = caches.lireDisque("messages") as? JsonObject ?: return null
        val page = parserPage(brut)
        caches.messages.écrire(clé, page)
        return page
    }

    /** Un fil de la dernière page connue (sans refetch) — null sinon. */
    suspend fun conversationEnCache(id: String): Conversation? =
        conversationsEnCache()?.conversations?.firstOrNull { it.id == id }

    private var cacheÉpoque: Long = 0

    internal companion object {
        internal const val TtlCacheMs = 45_000L
    }

    /**
     * Un fil par son id. Le serveur n'a pas de détail par conversation :
     * tout est embarqué dans le GET `messages` (page 1). On refetch en frais
     * (URLs signées rajeunies, docs/api/BOTI-API.md) — sauf si une page de
     * moins de 45 s traîne, alors elle suffit.
     */
    suspend fun conversation(id: String): Conversation? {
        val clé = caches.clé()
        val cache = clé?.let { caches.messages.lire(it) }
        if (cache != null && System.currentTimeMillis() - cacheÉpoque < TtlCacheMs) {
            return cache.conversations.firstOrNull { it.id == id }
        }
        return conversations(fraîche = true).conversations.firstOrNull { it.id == id }
    }

    /** Réponse dans un fil existant. Retourne le message tel que le serveur
     *  l'a enregistré (normalisé), ou null si la réponse n'en porte pas.
     *  `theme` : catégorie choisie dans le composeur (issue #99) — sinon
     *  celle du fil, reprise telle quelle (bundle : `theme: this.result.theme`). */
    suspend fun envoyerRéponse(
        conversation: Conversation,
        texte: String,
        theme: String? = null,
        pièces: List<File> = emptyList(),
        audio: File? = null,
    ): Message? {
        val s = session.state.first() ?: error("Session absente")
        val parties = buildList {
            pièces.forEach { add(partiePièce(it)) }
            audio?.let { add(partieAudio(it)) }
        }
        val rep = client.post(
            endpoint = "nouveau-message",
            fields = mapOf(
                "ref" to conversation.id,
                "sujet" to conversation.sujet,
                "message" to texte,
                "theme" to (theme ?: conversation.theme ?: ""),
                "index" to conversation.messages.size.toString(),
                "eleve_id" to s.eleveId,
                "parent_id" to s.parentId,
            ),
            partiesMultiples = parties,
        )
        return (rep["message"] as? JsonObject)?.let(Normalizers::message)
    }

    /** Nouveau fil. Miroir du `send()` officiel : `file` part en "null"
     *  (vestigial — JSON.stringify(null) du bundle), `eleve` en champs
     *  imbriqués eleve[...] (vestigial aussi, reproduit tel quel). */
    suspend fun envoyerNouveau(
        sujet: String,
        texte: String,
        theme: String,
        pièces: List<File> = emptyList(),
        audio: File? = null,
    ) {
        val s = session.state.first() ?: error("Session absente")
        val eleve = s.eleves.firstOrNull { it.id == s.eleveId } ?: s.eleves.firstOrNull()
        val parties = buildList {
            pièces.forEach { add(partiePièce(it)) }
            audio?.let { add(partieAudio(it)) }
        }
        client.post(
            endpoint = "nouveau-message",
            fields = mapOf(
                "sujet" to sujet,
                "message" to texte,
                "theme" to theme,
                "file" to "null",
                "eleve_id" to s.eleveId,
                "parent_id" to s.parentId,
                "user_id" to s.userId,
            ) + champsÉlèveImbriqués(eleve),
            partiesMultiples = parties,
        )
    }

    /** eleve[clé]=valeur — la sérialisation imbriquée de l'objet eleve par
     *  l'ApiService d'origine. Sans effet côté serveur (eleve_id suffit),
     *  reproduit pour rester au plus près du fil officiel. */
    private fun champsÉlèveImbriqués(eleve: school.greenwood.plus.model.Eleve?): Map<String, String> {
        eleve ?: return emptyMap()
        return buildMap {
            put("eleve[id]", eleve.id)
            eleve.nomComplet.takeIf { it.isNotBlank() }?.let { put("eleve[nomcomplet]", it) }
            eleve.prenom?.takeIf { it.isNotBlank() }?.let { put("eleve[prenom]", it) }
            eleve.nom?.takeIf { it.isNotBlank() }?.let { put("eleve[nom]", it) }
            eleve.niveau?.takeIf { it.isNotBlank() }?.let { put("eleve[niveau]", it) }
        }
    }

    /** `files[]` : une part par pièce, nom conservé, mime déduit de l'extension. */
    private fun partiePièce(fichier: File) = BotiClient.PartieFichier(
        champ = "files[]",
        fichier = fichier,
        nom = fichier.name,
        mime = mimeDe(fichier.extension),
    )

    /** Audio : `{file, name}` → part `audio`, nom `audio_<epoch>.<ext>`
     *  (bundle : `audio_ + epoch_s + . + type.split('/')[1]`). */
    private fun partieAudio(fichier: File) = BotiClient.PartieFichier(
        champ = "audio",
        fichier = fichier,
        nom = "audio_${System.currentTimeMillis() / 1000}.${fichier.extension.ifBlank { "m4a" }}",
        mime = mimeDe(fichier.extension.ifBlank { "m4a" }),
    )

    suspend fun contact(): ContactEcole {
        val rep = client.get("contact")
        caches.écrireDisque("contact", rep)
        return ContactEcole(
            titre = Normalizers.str(rep, "title"),
            texte = Normalizers.str(rep, "text"),
            tel = Normalizers.str(rep, "tel"),
            siteWeb = Normalizers.str(rep, "website"),
            facebook = Normalizers.str(rep, "facebook"),
            logo = Normalizers.str(rep, "logo"),
            socials = Normalizers.arr(rep, "socials").mapNotNull { s ->
                (s as? JsonObject)?.let {
                    val url = Normalizers.str(it, "link") ?: return@mapNotNull null
                    SocialLink(
                        label = Normalizers.str(it, "icone")?.removePrefix("logo-") ?: url,
                        url = url,
                    )
                }
            },
        )
    }

    /** Contact de l'école déjà vu, relu hors connexion. */
    suspend fun contactEnCache(): ContactEcole? {
        val brut = caches.lireDisque("contact") as? JsonObject ?: return null
        return ContactEcole(
            titre = Normalizers.str(brut, "title"),
            texte = Normalizers.str(brut, "text"),
            tel = Normalizers.str(brut, "tel"),
            siteWeb = Normalizers.str(brut, "website"),
            facebook = Normalizers.str(brut, "facebook"),
            logo = Normalizers.str(brut, "logo"),
            socials = Normalizers.arr(brut, "socials").mapNotNull { e ->
                (e as? JsonObject)?.let {
                    val url = Normalizers.str(it, "link") ?: return@mapNotNull null
                    SocialLink(
                        label = Normalizers.str(it, "icone")?.removePrefix("logo-") ?: url,
                        url = url,
                    )
                }
            },
        )
    }
}

/** Mime raisonnable pour une pièce jointe (serveur : types usuels du bureau). */
internal fun mimeDe(ext: String): String = when (ext.lowercase()) {
    "pdf" -> "application/pdf"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "doc", "docx" -> "application/msword"
    "xls", "xlsx" -> "application/vnd.ms-excel"
    "m4a", "mp4" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "ogg" -> "audio/ogg"
    "wav" -> "audio/wav"
    else -> "application/octet-stream"
}

class DemandesRepository(
    private val client: BotiClient,
    private val caches: CachesSession,
) {

    suspend fun liste(): List<Demande> {
        val rep = client.get("demandes", mapOf("page" to "1"))
        val résultat = parserListe(rep)
        // Dernière liste connue (issue #21) — une liste vide est un état valide.
        caches.clé()?.let { clé -> caches.demandes.écrire(clé, résultat) }
        caches.écrireDisque("demandes", rep)
        return résultat
    }

    private fun parserListe(rep: JsonObject): List<Demande> =
        Normalizers.arr(rep, "data")
            .mapNotNull { (it as? JsonObject)?.let(Normalizers::demande) }
            .sortedByDescending { it.dateCreation ?: LocalDateTime.MIN }

    /** Dernière liste connue, estampillée session — null si rien en cache ou
     *  si la session a changé depuis l'écriture. */
    suspend fun listeEnCache(): List<Demande>? {
        val clé = caches.clé() ?: return null
        caches.demandes.lire(clé)?.let { return it }
        // Redémarrage hors connexion : la réponse scellée repeuple la mémoire.
        val brut = caches.lireDisque("demandes") as? JsonObject ?: return null
        val liste = parserListe(brut)
        caches.demandes.écrire(clé, liste)
        return liste
    }
}

class DocumentsRepository(
    private val client: BotiClient,
    private val session: SessionStore,
    private val caches: CachesSession,
) {

    /** Ressources pédagogiques, groupées par matière côté UI. L'appel sans
     *  recherche est aussi l'écriture du cache de dernière liste connue
     *  (issue #21) ; une recherche ne l'alimente pas. */
    suspend fun ressources(recherche: String? = null): List<school.greenwood.plus.model.Ressource> {
        val rep = client.get(
            "ressources_v2",
            buildMap {
                put("search", recherche ?: "")
            },
        )
        val résultat = Normalizers.arr(rep, "data")
            .mapNotNull { (it as? JsonObject)?.let(Normalizers::ressource) }
        if (recherche == null) {
            caches.clé()?.let { clé -> caches.documents.écrire(clé, résultat) }
            caches.écrireDisque("documents", rep)
        }
        return résultat
    }

    /** Dernière liste complète connue, estampillée session — null si rien en
     *  cache ou si la session a changé depuis l'écriture. */
    suspend fun ressourcesEnCache(): List<school.greenwood.plus.model.Ressource>? {
        val clé = caches.clé() ?: return null
        caches.documents.lire(clé)?.let { return it }
        // Redémarrage hors connexion : la réponse scellée repeuple la mémoire.
        val brut = caches.lireDisque("documents") as? JsonObject ?: return null
        val liste = Normalizers.arr(brut, "data")
            .mapNotNull { (it as? JsonObject)?.let(Normalizers::ressource) }
        caches.documents.écrire(clé, liste)
        return liste
    }

    /** Bibliothèque — documents mis en ligne par les enseignants, rangés en
     *  unités (matières). Formes vérifiées en sonde lecture-seule le
     *  22/09/2026 (issue #43, docs/api/ENDPOINT-MAP.md) :
     *  - GET `bibliotheque` → `unites[]` (matières avec count_resources),
     *  - GET `bibliotheque?unite=<id>` → `data[]` (fiches, matière portée par
     *    l'unité).
     *  Fusionne ici le tout en une liste plate pour l'UI (groupage par
     *  matière côté écran). Une unité vide ou un échec sur une unité ne
     *  masque pas les autres. */
    suspend fun bibliotheque(): List<school.greenwood.plus.model.FicheBibliotheque> {
        val unites = unitesBibliotheque()
        if (unites.isEmpty()) return emptyList()
        val brutes: List<BruteBibliotheque> = unites.map { unite ->
            runCatching { fichesBrutes(unite) }.getOrElse { emptyList() }
        }.flatten()
        val fiches = brutes.mapNotNull { b ->
            Normalizers.ficheBibliotheque(b.objet, b.uniteId, b.matiere)
        }
        caches.clé()?.let { clé -> caches.bibliotheque.écrire(clé, fiches) }
        caches.écrireDisque("bibliotheque", enveloppeBibliotheque(brutes))
        return fiches
    }

    /** Une fiche brute, avec l'unité qui la porte — ce qu'il faut pour
     *  reconstruire la liste depuis le disque sans re-appeler le serveur. */
    private data class BruteBibliotheque(val uniteId: String, val matiere: String, val objet: JsonObject)

    private suspend fun fichesBrutes(unite: school.greenwood.plus.model.UniteBibliotheque): List<BruteBibliotheque> {
        val rep = client.get("bibliotheque", mapOf("unite" to unite.id))
        return Normalizers.arr(rep, "data")
            .filterIsInstance<JsonObject>()
            .map { BruteBibliotheque(unite.id, unite.label, it) }
    }

    private fun enveloppeBibliotheque(brutes: List<BruteBibliotheque>): kotlinx.serialization.json.JsonElement =
        kotlinx.serialization.json.buildJsonObject {
            put("fiches", kotlinx.serialization.json.buildJsonArray {
                brutes.forEach { b ->
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("u", kotlinx.serialization.json.JsonPrimitive(b.uniteId))
                        put("m", kotlinx.serialization.json.JsonPrimitive(b.matiere))
                        put("f", b.objet)
                    })
                }
            })
        }

    /** Les matières de la Bibliothèque — sert aussi à l'UI pour compter les
     *  unités vides. */
    suspend fun unitesBibliotheque(): List<school.greenwood.plus.model.UniteBibliotheque> {
        val rep = client.get("bibliotheque")
        return Normalizers.arr(rep, "unites")
            .mapNotNull { (it as? JsonObject)?.let(Normalizers::uniteBibliotheque) }
    }

    /** Dernière liste de fiches connue, estampillée session — null si rien en
     *  cache ou si la session a changé depuis l'écriture (issue #21). */
    suspend fun bibliothequeEnCache(): List<school.greenwood.plus.model.FicheBibliotheque>? {
        val clé = caches.clé() ?: return null
        caches.bibliotheque.lire(clé)?.let { return it }
        // Redémarrage hors connexion : les fiches scellées repeuplent la mémoire.
        val brut = caches.lireDisque("bibliotheque") as? JsonObject ?: return null
        val liste = (brut["fiches"] as? kotlinx.serialization.json.JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.mapNotNull { e ->
                val uniteId = (e["u"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
                val matiere = (e["m"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
                (e["f"] as? JsonObject)?.let { Normalizers.ficheBibliotheque(it, uniteId, matiere) }
            }
            ?: return null
        caches.bibliotheque.écrire(clé, liste)
        return liste
    }

    suspend fun fichesDeUnite(uniteId: String, matiere: String): List<school.greenwood.plus.model.FicheBibliotheque> {
        val rep = client.get("bibliotheque", mapOf("unite" to uniteId))
        return Normalizers.arr(rep, "data")
            .mapNotNull { (it as? JsonObject)?.let { f -> Normalizers.ficheBibliotheque(f, uniteId, matiere) } }
    }

    /** Détail d'une fiche : les pièces jointes y portent enfin leur URL média
     *  signée (le `file.link` de la liste n'est qu'un nom de fichier). */
    suspend fun détailFiche(ressourceId: String): school.greenwood.plus.model.FicheBibliothequeDetail? {
        val rep = client.get("ressource_details", mapOf("ressource" to ressourceId))
        caches.écrireDisque("fiche-$ressourceId", rep)
        return Normalizers.détailBibliotheque(rep)
    }

    /** Détail déjà vu, relu hors connexion (URLs signées comprises). */
    suspend fun détailFicheEnCache(ressourceId: String): school.greenwood.plus.model.FicheBibliothequeDetail? {
        val brut = caches.lireDisque("fiche-$ressourceId") as? JsonObject ?: return null
        return Normalizers.détailBibliotheque(brut)
    }

    /**
     * Détail d'un quiz (GET `quiz?quiz_id=…`) — forme vérifiée en sonde
     * lecture-seule le 19/09/2026 (issue #17). Le serveur renvoie aussi la
     * liste paginée (`start`/`limit`) et le score de la dernière tentative
     * (`lastPlay`) ; l'espace documents suffit à l'un comme à l'autre.
     */
    suspend fun quiz(quizId: String): QuizChargé {
        val rep = client.get("quiz", mapOf("quiz_id" to quizId))
        val détail = Normalizers.quiz(rep) ?: error("Quiz illisible")
        val data = (rep["data"] as? JsonObject) ?: rep
        return QuizChargé(détail, Normalizers.arr(data, "questions"))
    }

    /**
     * Enregistrement d'une tentative (POST `quiz`) — champs lus dans le
     * bundle officiel 2.4.14 (page parent `/parent/quiz`, chunk 1140.js) :
     * `quiz_id`, `questions` (le tableau GET sérialisé, `answer.answer` =
     * texte choisi et `answer.answered` = secondes consommées), eleve_id,
     * user_id, parent_id, key. Envoi réel à valider une fois sur un vrai
     * compte (issue #17) — en échec, l'écran garde le score local.
     */
    suspend fun envoyerRésultatQuiz(
        quiz: school.greenwood.plus.model.QuizDetail,
        brutes: JsonArray,
        jouées: Map<Int, school.greenwood.plus.model.RéponseJouée>,
    ): school.greenwood.plus.model.QuizRésultat {
        val s = session.state.first() ?: error("Session absente")
        val rep = client.post(
            endpoint = "quiz",
            fields = mapOf(
                "quiz_id" to quiz.id,
                "questions" to questionsPourEnvoi(brutes, jouées).toString(),
                "eleve_id" to s.eleveId,
                "user_id" to s.userId,
                "parent_id" to s.parentId,
            ),
        )
        return Normalizers.quizRésultat(rep)
    }
}

/**
 * Le tableau `questions` du POST : chaque question telle que le GET l'a
 * envoyée, seul `answer` est mis à jour (`answer.answer` = texte choisi,
 * `answer.answered` = secondes utilisées) — exactement ce que fait le
 * bundle avant son `JSON.stringify(questions)`. Une question sans objet
 * `answer` en reçoit un neuf ; une question non jouée part telle quelle.
 */
internal fun questionsPourEnvoi(
    brutes: JsonArray,
    jouées: Map<Int, school.greenwood.plus.model.RéponseJouée>,
): JsonArray = JsonArray(brutes.mapIndexed { index, question ->
    val jouée = jouées[index] ?: return@mapIndexed question
    val objet = question as? JsonObject ?: return@mapIndexed question
    val answer = objet["answer"] as? JsonObject ?: JsonObject(emptyMap())
    val answerMisÀJour = JsonObject(
        buildMap {
            putAll(answer)
            put("answer", JsonPrimitive(jouée.texte))
            put("answered", JsonPrimitive(jouée.secondes))
        },
    )
    JsonObject(objet.toMap() + ("answer" to answerMisÀJour))
})

/** Détail normalisé + questions telles que reçues du GET — le POST `quiz`
 *  renvoie ce tableau sérialisé avec `answer` mis à jour. */
data class QuizChargé(
    val détail: school.greenwood.plus.model.QuizDetail,
    val questionsBrutes: JsonArray,
)
