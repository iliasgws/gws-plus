package school.greenwood.plus.data.repo

import kotlinx.serialization.json.JsonObject
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.model.ContactEcole
import school.greenwood.plus.model.Demande
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.SocialLink

/*
 * Messages avec l'administration. Lecture seule en v1 : l'envoi (POST
 * nouveau-message) a des noms de champs non vérifiés — un envoi raté ferait
 * croire à un parent que l'école a été prévenue. On attend une vérification
 * avant d'ouvrir le composeur. En attendant : la carte contact (téléphone,
 * Facebook, site) rend l'administration joignable tout de suite.
 */
class MessagesRepository(private val client: BotiClient) {

    suspend fun conversations(): List<Conversation> {
        val rep = client.get("messages", mapOf("page" to "1"))
        return Normalizers.arr(rep, "data")
            .mapNotNull { (it as? JsonObject)?.let(Normalizers::conversation) }
            .filter { it.messages.isNotEmpty() }
            .sortedByDescending { it.dernierDate ?: java.time.LocalDateTime.MIN }
    }

    /**
     * Un fil par son id. Le serveur n'a pas de détail par conversation :
     * tout est embarqué dans le GET `messages` (page 1). On refetch — les
     * URLs média signées expirent après 15–20 minutes, une lecture fraîche
     * les rafraîchit (docs/BOTI-API.md).
     */
    suspend fun conversation(id: String): Conversation? =
        conversations().firstOrNull { it.id == id }

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

class DemandesRepository(private val client: BotiClient) {

    suspend fun liste(): List<Demande> {
        val rep = client.get("demandes", mapOf("page" to "1"))
        return Normalizers.arr(rep, "data")
            .mapNotNull { (it as? JsonObject)?.let(Normalizers::demande) }
            .sortedByDescending { it.dateCreation ?: java.time.LocalDateTime.MIN }
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
