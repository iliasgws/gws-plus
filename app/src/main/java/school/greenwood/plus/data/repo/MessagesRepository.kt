package school.greenwood.plus.data.repo

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import school.greenwood.plus.data.api.BotiClient
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
 * les champs ont été lus dans le bundle officiel 2.4.14 (TASKS.md, statique) :
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

class MessagesRepository(private val client: BotiClient, private val session: SessionStore) {

    suspend fun conversations(): MessagesPage {
        val rep = client.get("messages", mapOf("page" to "1"))
        return MessagesPage(
            conversations = Normalizers.arr(rep, "data")
                .mapNotNull { (it as? JsonObject)?.let(Normalizers::conversation) }
                .filter { it.messages.isNotEmpty() }
                .sortedByDescending { it.dernierDate ?: LocalDateTime.MIN },
            themes = Normalizers.themes(rep),
        )
    }

    /**
     * Un fil par son id. Le serveur n'a pas de détail par conversation :
     * tout est embarqué dans le GET `messages` (page 1). On refetch — les
     * URLs média signées expirent après 15–20 minutes, une lecture fraîche
     * les rafraîchit (docs/BOTI-API.md).
     */
    suspend fun conversation(id: String): Conversation? =
        conversations().conversations.firstOrNull { it.id == id }

    /** Réponse dans un fil existant. Retourne le message tel que le serveur
     *  l'a enregistré (normalisé), ou null si la réponse n'en porte pas. */
    suspend fun envoyerRéponse(
        conversation: Conversation,
        texte: String,
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
                "theme" to (conversation.theme ?: ""),
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

class DemandesRepository(private val client: BotiClient) {

    suspend fun liste(): List<Demande> {
        val rep = client.get("demandes", mapOf("page" to "1"))
        return Normalizers.arr(rep, "data")
            .mapNotNull { (it as? JsonObject)?.let(Normalizers::demande) }
            .sortedByDescending { it.dateCreation ?: LocalDateTime.MIN }
    }
}

class DocumentsRepository(private val client: BotiClient) {

    /** Ressources pédagogiques, groupées par matière côté UI. */
    suspend fun ressources(recherche: String? = null): List<school.greenwood.plus.model.Ressource> {
        val rep = client.get(
            "ressources_v2",
            buildMap {
                put("search", recherche ?: "")
            },
        )
        return Normalizers.arr(rep, "data")
            .mapNotNull { (it as? JsonObject)?.let(Normalizers::ressource) }
    }

    /** Bibliothèque — vide sur le compte sondé ; tolérant si le serveur
     *  renvoie une structure différente. */
    suspend fun bibliotheque(): List<school.greenwood.plus.model.Ressource> {
        val rep = runCatching { client.get("bibliotheque") }.getOrElse { return emptyList() }
        val data = rep["data"]
        return when (data) {
            is kotlinx.serialization.json.JsonArray -> data
                .filterIsInstance<JsonObject>()
                .mapNotNull(Normalizers::ressource)
            else -> emptyList()
        }
    }
}
