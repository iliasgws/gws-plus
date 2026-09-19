package school.greenwood.plus.data.repo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import school.greenwood.plus.data.api.MediaUrls
import school.greenwood.plus.model.Absence
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Demande
import school.greenwood.plus.model.DemandeReponse
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Eleve
import school.greenwood.plus.model.Message
import school.greenwood.plus.model.ParentInfo
import school.greenwood.plus.model.Post
import school.greenwood.plus.model.Ressource
import school.greenwood.plus.model.ThemeMessage
import school.greenwood.plus.util.extractDate
import school.greenwood.plus.util.extractDateTime
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * Normalisation des réponses brutes → modèles du domaine. Le serveur mélange
 * chaînes d'affichage, champs manquants et structures variables ; chaque
 * normaliseur tolère tout ça et rend un modèle propre ou null.
 */

object Normalizers {

    fun str(obj: JsonObject, key: String): String? =
        (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    fun int(obj: JsonObject, key: String): Int? =
        (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toIntOrNull()

    fun bool(obj: JsonObject, key: String): Boolean? =
        (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull

    fun arr(obj: JsonObject, key: String): JsonArray =
        (obj[key] as? JsonArray) ?: JsonArray(emptyList())

    fun obj(item: JsonElementAlias): JsonObject? = item as? JsonObject

    // — Devoirs ------------------------------------------------------------

    /**
     * Devoir des listes parsées (`devoirs_remettre` / `devoirs_ancien`) :
     * `date_remise` ISO = échéance, `publication` en chaîne d'affichage.
     * La date de publication exacte vient des items bruts du bucket `data`
     * (champ `date` ISO) — fusionnée par le dépôt via `publicationBrute`.
     */
    fun devoir(raw: JsonObject, publicationBrute: LocalDateTime? = null): Devoir? {
        val id = str(raw, "id") ?: return null
        val pieces = buildList {
            (raw["file"] as? JsonObject)?.let { addAll(MediaUrls.piècesJointes(str(it, "link"), str(it, "text"))) }
            arr(raw, "files").forEach { f ->
                (f as? JsonObject)?.let { addAll(MediaUrls.piècesJointes(str(it, "link"), str(it, "filename") ?: str(it, "text"))) }
            }
        }.map { Attachment(it.first, it.second) }
        val fait = (raw["devoir_fait"] as? JsonObject)
        return Devoir(
            id = id,
            title = str(raw, "title") ?: "",
            matiere = str(raw, "matiere") ?: str(raw, "categorie") ?: "",
            categorie = str(raw, "categorie"),
            enseignant = str(raw, "enseignant"),
            description = str(raw, "description"),
            dateRemise = extractDate(str(raw, "date_remise")),
            publication = publicationBrute ?: extractDateTime(str(raw, "publication")),
            fait = bool(fait ?: JsonObject(emptyMap()), "fait") ?: false,
            filesSent = bool(fait ?: JsonObject(emptyMap()), "file_sent") ?: false,
            attachments = pieces,
        )
    }

    // — Actualités ---------------------------------------------------------

    fun post(raw: JsonObject): Post? {
        val id = str(raw, "id") ?: return null
        val pieces = arr(raw, "files").mapNotNull { f ->
            (f as? JsonObject)?.let { o ->
                MediaUrls.piècesJointes(str(o, "link"), str(o, "filename") ?: str(o, "text"))
                    .firstOrNull()
                    ?.let { Attachment(it.first, it.second) }
            }
        }
        return Post(
            id = id,
            title = str(raw, "title") ?: "",
            categorie = str(raw, "categorie"),
            date = extractDateTime(str(raw, "date")),
            intro = str(raw, "intro"),
            description = str(raw, "description"),
            image = MediaUrls.lienRéel(str(raw, "image")),
            attachments = pieces,
        )
    }

    // — Messages -----------------------------------------------------------

    fun conversation(raw: JsonObject): Conversation? {
        val id = str(raw, "id") ?: return null
        val messages = sansDoublonsConsécutifs(
            arr(raw, "conversation").mapNotNull { m ->
                (m as? JsonObject)?.let { message(it) }
            },
        )
        return Conversation(
            id = id,
            sujet = str(raw, "sujet") ?: str(raw, "to") ?: "",
            messages = messages,
            theme = str(raw, "theme"),
        )
    }

    /** Catégories du composeur (serveur `themes[]`, ids 8/9/10/11/13). */
    fun themes(rep: JsonObject): List<ThemeMessage> =
        arr(rep, "themes").mapNotNull { t ->
            (t as? JsonObject)?.let {
                ThemeMessage(
                    id = str(it, "id") ?: return@mapNotNull null,
                    label = str(it, "label") ?: "",
                    description = str(it, "description"),
                )
            }
        }

    fun message(raw: JsonObject): Message? {
        val texte = str(raw, "message") ?: return null
        val pieces = arr(raw, "files").mapNotNull { f ->
            (f as? JsonObject)?.let { o ->
                MediaUrls.piècesJointes(str(o, "link"), str(o, "filename") ?: str(o, "text"))
                    .firstOrNull()
                    ?.let { Attachment(it.first, it.second) }
            }
        }
        return Message(
            id = str(raw, "message_id") ?: str(raw, "id") ?: "",
            deLAdmin = bool(raw, "is_self") != true,
            texte = texte,
            date = extractDateTime(str(raw, "datetime")),
            vuLe = extractDateTime(str(raw, "vu_le")),
            attachments = pieces,
            audio = audio(raw),
        )
    }

    /**
     * Le serveur renvoie parfois le même message deux fois (double envoi
     * observé à une seconde d'écart, `message_id` distincts — ENDPOINT-MAP
     * quirk 7). On retire le doublon consécutif : même texte, même direction,
     * datés à moins de deux secondes d'écart. Jamais de dédoublonnage par
     * `message_id` — un vrai rappel du parent à quelques minutes d'écart est
     * légitime et doit rester visible.
     */
    fun sansDoublonsConsécutifs(messages: List<Message>): List<Message> =
        buildList {
            messages.forEach { m ->
                val précédent = lastOrNull()
                val doublon = précédent != null &&
                    précédent.texte == m.texte &&
                    précédent.deLAdmin == m.deLAdmin &&
                    précédent.date != null && m.date != null &&
                    kotlin.math.abs(java.time.Duration.between(précédent.date, m.date).seconds) < 2
                if (!doublon) add(m)
            }
        }

    /** Message vocal : lien direct (chaîne) ou objet `{link, …}` — forme non
     *  observée en production (toujours null dans le sondage), tolérant. */
    private fun audio(raw: JsonObject): Attachment? {
        val brut = raw["audio"] ?: return null
        return when (brut) {
            is JsonObject ->
                MediaUrls.piècesJointes(str(brut, "link"), str(brut, "filename") ?: str(brut, "text") ?: "Message vocal")
                    .firstOrNull()
                    ?.let { Attachment(it.first, it.second) }
            else ->
                MediaUrls.lienRéel((brut as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull)
                    ?.let { url ->
                        val nom = url.substringBefore('?').substringAfterLast('/').ifBlank { "Message vocal" }
                        Attachment(name = nom, url = url)
                    }
        }
    }

    // — Demandes -----------------------------------------------------------

    fun demande(raw: JsonObject): Demande? {
        val id = str(raw, "id") ?: return null
        return Demande(
            id = id,
            titre = str(raw, "type") ?: "",
            statut = str(raw, "statut"),
            cree = str(raw, "cree"),
            dateCreation = parseCreationCourte(str(raw, "created_at")),
            dateAffichee = str(raw, "date"),
            file = MediaUrls.lienRéel(str(raw, "file")),
            reponses = arr(raw, "reponses").mapNotNull { r ->
                (r as? JsonObject)?.let {
                    DemandeReponse(label = str(it, "label"), reponse = str(it, "reponse") ?: "")
                }
            },
        )
    }

    /** « 07/09/26 13h59 » — format court de l'administration. */
    fun parseCreationCourte(brut: String?): LocalDateTime? {
        if (brut.isNullOrBlank()) return null
        val m = Regex("""(\d{1,2})/(\d{1,2})/(\d{2})\s+(\d{1,2})h(\d{2})""").find(brut) ?: return null
        val (j, mo, a, h, mi) = m.destructured
        return runCatching {
            LocalDateTime.of(2000 + a.toInt(), mo.toInt(), j.toInt(), h.toInt(), mi.toInt())
        }.getOrNull()
    }

    // — Absences -----------------------------------------------------------

    /** Item non observé en production (aucune absence sur le compte sondé) —
     *  normalisation défensive sur les noms de champs plausibles. */
    fun absence(raw: JsonObject): Absence? {
        val id = str(raw, "id") ?: return null
        val justifiee = bool(raw, "justifie")
            ?: (str(raw, "justifie")?.equals("1", true) == true)
        return Absence(
            id = id,
            motif = str(raw, "motif") ?: str(raw, "description"),
            du = extractDate(str(raw, "date_debut") ?: str(raw, "date")),
            au = extractDate(str(raw, "date_fin") ?: str(raw, "date")),
            justifiee = justifiee,
        )
    }

    // — Ressources (espace documents) ---------------------------------------

    fun ressource(raw: JsonObject): Ressource? {
        val id = str(raw, "id") ?: return null
        return Ressource(
            id = id,
            matiere = str(raw, "matiere") ?: "",
            label = str(raw, "label") ?: "",
            presentation = str(raw, "presentation"),
            type = str(raw, "type"),
            couleur = str(raw, "color"),
            icone = str(raw, "icon")?.takeIf { it.startsWith("http") },
        )
    }

    // — Comptes ------------------------------------------------------------

    fun eleve(raw: JsonObject): Eleve? {
        val id = str(raw, "id") ?: return null
        return Eleve(
            id = id,
            nomComplet = str(raw, "nomcomplet") ?: "",
            prenom = str(raw, "prenom"),
            nom = str(raw, "nom"),
            niveau = str(raw, "niveau"),
            image = MediaUrls.lienRéel(str(raw, "img")),
        )
    }

    fun parent(raw: JsonObject?): ParentInfo? {
        val o = raw ?: return null
        val id = str(o, "id") ?: return null
        return ParentInfo(
            id = id,
            nomComplet = str(o, "nomcomplet") ?: "",
            image = MediaUrls.lienRéel(str(o, "image")),
        )
    }

    /** Le serveur renvoie parfois des chaînes vides au lieu de null. */
    fun listePosts(obj: JsonObject): List<Post> =
        arr(obj, "data").mapNotNull { (it as? JsonObject)?.let(::post) }

    fun aujourdhuiOuRecent(date: LocalDateTime?, aujourdhui: LocalDate): Boolean =
        date != null && (date.toLocalDate() == aujourdhui || date.isAfter(LocalDateTime.now()))
}

/** Alias court pour éviter un import bruyant. */
typealias JsonElementAlias = kotlinx.serialization.json.JsonElement
