package school.greenwood.plus.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Modèles du domaine — ce que l'UI consomme. Les réponses brutes du serveur
 * sont normalisées dans les dépôts (data/repo) : ici, tout est propre.
 */

/** Pièce jointe avec son URL média *réelle* (déjà décodée une fois, voir docs/api/BOTI-API.md). */
data class Attachment(
    val name: String,
    val url: String,
)

/** Un devoir : entrée du registre et ligne de l'onglet Devoirs. */
data class Devoir(
    val id: String,
    val title: String,
    val matiere: String,
    val categorie: String? = null,
    val enseignant: String? = null,
    val description: String? = null,
    /** Échéance — vérifié sur appel réel le 18/09/2026 : `date_remise` du
     *  serveur est bien la date de rendu (ISO), `publication`/`date` la date
     *  de publication. La carte « Ce soir » filtre là-dessus. */
    val dateRemise: LocalDate? = null,
    val publication: LocalDateTime? = null,
    val fait: Boolean = false,
    val filesSent: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
)

/** Une actualité de l'école. */
data class Post(
    val id: String,
    val title: String,
    val categorie: String? = null,
    val date: LocalDateTime? = null,
    val intro: String? = null,
    /** Corps HTML complet — uniquement via admin_nouveautes (docs/api/BOTI-API.md). */
    val description: String? = null,
    val image: String? = null,
    val attachments: List<Attachment> = emptyList(),
)

/** Un fil de messages avec l'administration. */
data class Conversation(
    val id: String,
    val sujet: String,
    val messages: List<Message>,
    /** Thème du fil (id serveur) — repris tel quel dans la réponse (bundle :
     *  `theme: this.result.theme`). Rarement présent, tolérant. */
    val theme: String? = null,
) {
    val dernierDate: LocalDateTime? get() = messages.maxOfOrNull { it.date ?: LocalDateTime.MIN }
}

/** Catégorie de message (serveur `themes[]` — Scolarité, Vie Scolaire…). */
data class ThemeMessage(
    val id: String,
    val label: String,
    val description: String? = null,
)

/**
 * Message en cours d'envoi, poussé de façon optimiste au bas du fil puis
 * remplacé par la version serveur (bundle : `conversation[index] = _.message`).
 * Un échec reste affiché, marqué, avec une relance manuelle — jamais
 * silencieux : un parent doit toujours savoir si l'école n'a rien reçu.
 */
data class MessageEnvoi(
    val texte: String,
    val pièces: List<java.io.File> = emptyList(),
    val audio: java.io.File? = null,
    val statut: Statut = Statut.EnCours,
) {
    enum class Statut { EnCours, Échec }
}

/** Un message dans un fil — de l'administration ou du parent. */
data class Message(
    val id: String,
    val deLAdmin: Boolean,
    val texte: String,
    val date: LocalDateTime? = null,
    /** Accusé de lecture du destinataire (`vu_le` du serveur). */
    val vuLe: LocalDateTime? = null,
    val attachments: List<Attachment> = emptyList(),
    /** Message vocal (`audio` du serveur — nul dans toutes les observations à ce jour). */
    val audio: Attachment? = null,
)

/** Une demande administrative et son statut. */
data class Demande(
    val id: String,
    val titre: String,
    val statut: String?,
    val cree: String?,
    val dateCreation: LocalDateTime? = null,
    val dateAffichee: String? = null,
    val file: String? = null,
    val reponses: List<DemandeReponse> = emptyList(),
)

/** Une réponse de l'administration sur une demande. */
data class DemandeReponse(
    val label: String?,
    val reponse: String,
)

/** Une ressource pédagogique de l'espace documents. */
data class Ressource(
    val id: String,
    val matiere: String,
    val label: String,
    val presentation: String? = null,
    val type: String? = null,
    val couleur: String? = null,
    val icone: String? = null,
)

/** Une absence notée. */
data class Absence(
    val id: String,
    val motif: String?,
    val du: LocalDate?,
    val au: LocalDate?,
    val justifiee: Boolean,
)

/** Bilan d'absences (statut du compte parent). */
data class BilanAbsences(
    val justifiees: List<Absence> = emptyList(),
    val nonJustifiees: List<Absence> = emptyList(),
    val total: Int = 0,
    val retards: Int = 0,
)

/** Coordonnées de contact de l'administration (GET contact). */
data class ContactEcole(
    val titre: String? = null,
    val texte: String? = null,
    val tel: String? = null,
    val siteWeb: String? = null,
    val facebook: String? = null,
    val logo: String? = null,
    val socials: List<SocialLink> = emptyList(),
)

data class SocialLink(
    val label: String,
    val url: String,
)

/** Un élève d'un compte parent (champ `eleves` du login). */
data class Eleve(
    val id: String,
    val nomComplet: String,
    val prenom: String? = null,
    val nom: String? = null,
    val niveau: String? = null,
    val image: String? = null,
) {
    /** Initiales pour l'avatar quand l'image manque. */
    val initiales: String
        get() = buildString {
            prenom?.take(1)?.uppercase()?.let(::append)
            nom?.take(1)?.uppercase()?.let(::append)
        }.ifEmpty { nomComplet.take(2).uppercase() }
}

/** Compte parent, issu du login. */
data class ParentInfo(
    val id: String,
    val nomComplet: String,
    val image: String? = null,
)
