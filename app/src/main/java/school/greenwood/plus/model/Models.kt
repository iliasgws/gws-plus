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
    /** Marquage « fait pour moi » local (issue #82) : purement dans l'app,
     *  jamais envoyé à l'école — les professeurs et l'administration ne le
     *  voient pas. Réversible d'un clic, contrairement au fait serveur. */
    val faitLocal: Boolean = false,
)

/** Payload détail d'un devoir (issue #68) — GET `devoirs&devoir=<id>`, la
 *  seule réponse qui porte les pièces jointes, l'état de soumission et les
 *  droits. Les formes viennent de la sonde du 26/09/2026 (devoirs_single_*). */
data class DevoirDétail(
    val devoir: Devoir,
    /** Bouton « fait » permis par le serveur — sinon l'action est masquée. */
    val peutMarquerFait: Boolean = false,
    /** Envoi de copies permis après le fait (section d'envoi officielle). */
    val peutAjouterFichiers: Boolean = false,
    val montrerFichiers: Boolean = false,
    /** Copies envoyées au serveur, avec leur lien de téléchargement. */
    val copiesEnvoyées: List<Attachment> = emptyList(),
)

/** Réponse du POST `devoirs_date_v2` : soit les copies sont parties
 *  (`file_sent` true, liens serveur en retour), soit le devoir est juste
 *  marqué fait. `titre`/`message` portent l'alerte du serveur. */
data class SoumissionDétail(
    val envoyées: Boolean,
    val fait: Boolean,
    val copies: List<Attachment> = emptyList(),
    val titre: String? = null,
    val message: String? = null,
)

/** Une actualité de l'école. */
data class Post(
    val id: String,
    val title: String,
    val categorie: String? = null,
    val date: LocalDateTime? = null,
    val intro: String? = null,
    /** Corps HTML complet — uniquement via admin_nouveautes (docs/api/BOTI-API.md) ou post_view. */
    val description: String? = null,
    val image: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val bookmark: Boolean = false,
    val auteur: String? = null,
    val permitComments: Boolean = false,
    val permitNewComments: Boolean = false,
    val permitQuiz: Boolean = false,
)

/** Un commentaire sous un post d'actualité. */
data class Commentaire(
    val auteur: String,
    val texte: String,
    val date: LocalDateTime? = null,
    val image: String? = null,
    val sousCommentaires: List<Commentaire> = emptyList(),
)

/** Une question de quiz rattachée à un post d'actualité (GET post_view). */
data class QuestionPost(
    val alias: String? = null,
    val label: String,
    val réponses: List<String> = emptyList(),
    val réponseChoisie: String? = null,
)

/**
 * Détail complet d'un post (GET `post_view` — forme vérifiée 2026-09-20).
 * Remplace la lecture du flux admin_nouveautes (~19 Mo), gardé en repli.
 */
data class PostDetail(
    val id: String,
    val title: String,
    val categorie: String? = null,
    val date: LocalDateTime? = null,
    val intro: String? = null,
    val descriptionHtml: String? = null,
    val image: String? = null,
    val bookmark: Boolean = false,
    val auteur: String? = null,
    val files: List<Attachment> = emptyList(),
    val images: List<String> = emptyList(),
    val commentaires: List<Commentaire> = emptyList(),
    val peutCommenter: Boolean = false,
    val peutNouveauCommentaire: Boolean = false,
    val peutRépondre: Boolean = false,
    val peutQuiz: Boolean = false,
    val questions: List<QuestionPost> = emptyList(),
) {
    val attachments: List<Attachment> get() = files
}

/**
 * Un créneau de cours (GET `cours_v2`, `seances[].seances[]`). La forme
 * intérieure n'a JAMAIS été observée (sondage 2026-09-20 : `seances[]` vide,
 * ENDPOINT-MAP « do not rely ») — parsing défensif sur des noms de champs
 * plausibles, tout est facultatif.
 */
data class Créneau(
    val matière: String? = null,
    val début: String? = null,
    val fin: String? = null,
    val salle: String? = null,
    val enseignant: String? = null,
)

/** Un jour de la semaine d'emploi du temps (`seances[]` externe, lundi = 1). */
data class JournéeCours(
    val jour: Int,
    val label: String? = null,
    /** Date réelle du jour, dérivée du lundi de semaine (le libellé serveur
     *  « Le 14 Sep 2026 » n'est pas analysable — mois abrégé anglais). */
    val date: LocalDate? = null,
    val créneaux: List<Créneau> = emptyList(),
)

/**
 * Une semaine d'emploi du temps (GET `cours_v2` — forme de tête vérifiée
 * 2026-09-20, ENDPOINT-MAP). Les journées non reconnues sont tolérées.
 */
data class SemaineCours(
    /** Libellé serveur « Du  2026/09/14 Au  2026/09/20 ». */
    val label: String? = null,
    /** Lundi de la semaine affichée (extrait du libellé ou dérivé de last_week). */
    val lundi: LocalDate? = null,
    /** Jour présélectionné par le serveur (`selected_day`, 1-based). */
    val jourSélectionné: Int? = null,
    val journées: List<JournéeCours> = emptyList(),
    /** `next_week` / `last_week` (ISO) pour la navigation entre semaines. */
    val semaineSuivante: LocalDate? = null,
    val semainePrécédente: LocalDate? = null,
    /** Textes serveur (translation) avec repli côté app. */
    val aucunCours: String? = null,
    val restreint: Boolean = false,
    /** `restricted.label` (HTML brut, conservé tel quel — aplati à l'écran). */
    val messageRestriction: String? = null,
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

/** Une matière (unité) de la Bibliothèque — GET `bibliotheque`, forme vérifiée
 *  en sonde lecture-seule le 22/09/2026 (docs/api/ENDPOINT-MAP.md). */
data class UniteBibliotheque(
    val id: String,
    val label: String,
    val image: String? = null,
    val couleur: String? = null,
    val nombreRessources: Int? = null,
    val aDuNeuf: Boolean = false,
)

/** Une fiche de la Bibliothèque (GET `bibliotheque?unite=<id>`). La fiche ne
 *  porte pas sa matière : elle vient de l'unité consultée. Son `file` ne
 *  contient qu'un nom de fichier — le vrai lien signé est dans le détail. */
data class FicheBibliotheque(
    val id: String,
    val uniteId: String,
    val matiere: String,
    val titre: String,
    val categorie: String? = null,
    val date: String? = null,
    val par: String? = null,
    val couleur: String? = null,
    val image: String? = null,
)

/** Le détail d'une fiche (GET `ressource_details?ressource=<id>` — forme
 *  vérifiée en sonde le 22/09/2026) : c'est ici que les pièces jointes
 *  portent leur URL média signée, prête pour Fichiers.télécharger. */
data class FicheBibliothequeDetail(
    val id: String,
    val matiere: String? = null,
    val description: String? = null,
    val fichiers: List<Attachment> = emptyList(),
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

/** Une réponse possible d'une question de quiz (GET `quiz` — vérifié en sonde
 *  lecture-seule le 19/09/2026). */
data class QuizReponse(
    val texte: String,
    val correcte: Boolean,
)

/** Une réponse jouée pendant une tentative : texte choisi (« » si le temps
 *  est écoulé sans choix) et secondes consommées (`answer.answer` /
 *  `answer.answered` du POST `quiz`). */
data class RéponseJouée(
    val texte: String,
    val secondes: Int,
)

/** Une question de quiz (GET `quiz`, `questions[]`). */
data class QuizQuestion(
    val texte: String,
    val image: String? = null,
    /** Temps alloué à la question, en secondes (`temps_reponse`). */
    val tempsReponse: Int? = null,
    val reponses: List<QuizReponse> = emptyList(),
)

/** Un quiz de l'espace documents, tel que le renvoie GET `quiz?quiz_id=…`. */
data class QuizDetail(
    val id: String,
    val label: String,
    val matiere: String? = null,
    val niveau: String? = null,
    val couleur: String? = null,
    val image: String? = null,
    /** Durée totale telle qu'affichée par le serveur (« 05:00 »). */
    val minutes: String? = null,
    val peutJouer: Boolean = true,
    val peutRejouer: Boolean = false,
    val questions: List<QuizQuestion> = emptyList(),
) {
    val nbQuestions: Int get() = questions.size
}

/** Score d'une tentative, renvoyé par le POST `quiz` (tolérant : enveloppe
 *  plate ou `data{}` — le bundle lit `resultatScore.score` directement). */
data class QuizRésultat(
    val score: String? = null,
    val temps: String? = null,
    val peutRejouer: Boolean? = null,
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

/** — Boutique de l'école (GET/POST `shop`, sondé le 21/09/2026) ----------- */

/** Une rubrique du catalogue (« Tout » a l'id -1). `notif` est le badge
 *  compteur du serveur, absent sur la plupart des rubriques. */
data class RubriqueBoutique(
    val id: String,
    val label: String,
    val icone: String? = null,
    val fond: String? = null,
    val notif: Int = 0,
)

/** Un produit du catalogue : le prix arrive en chaîne d'affichage (« 250 DH »). */
data class ProduitBoutique(
    val id: String,
    val label: String,
    val image: String? = null,
    val prix: String? = null,
)

/** Un jour du planning des repas de la cantine (rubrique « Repas invité »,
 *  GET `shop?rubrique=2` — `products` y est vide, le serveur renvoie
 *  `cantines[]`). `id` est l'id du produit à commander. */
data class CantineJour(
    val id: String,
    val jourLabel: String? = null,
    val jourDate: String? = null,
    val jourValeur: String? = null,
    val dispoLabel: String? = null,
    val dispoCouleur: String? = null,
    val image: String? = null,
    val label: String = "",
    val description: String? = null,
    val prix: String? = null,
    val actif: Boolean = false,
    val peutRéserver: Boolean = false,
    val déjàRéservé: Boolean = false,
    /** Libellé serveur du geste (« Réserver ») ou de l'état (« Réservé 1/1 »). */
    val réservéLibellé: String? = null,
)

/** Une variante d'un produit (taille déclinée) : `amount` = prix du produit
 *  dans cette taille, `qte` = stock disponible — deux chaînes numériques. */
data class VarianteBoutique(
    val id: String,
    val label: String,
    val couleur: String? = null,
    val montant: String? = null,
    val stock: Int? = null,
)

/** Le détail d'un produit (GET `shop?product=…`). `prixRaw` est le prix de
 *  base, sans unité (« 150 »). */
data class ProduitDétail(
    val id: String,
    val label: String,
    val image: String? = null,
    val description: String? = null,
    val prixRaw: String? = null,
    val peutCommander: Boolean = true,
    val variantes: List<VarianteBoutique> = emptyList(),
    /** Préremplissage d'une commande à modifier (GET avec `commande=…`). */
    val prérempli: PrérempliCommande? = null,
)

/** Les choix d'une commande existante, renvoyés par le GET de modification. */
data class PrérempliCommande(
    val taille: String? = null,
    val quantité: Int = 1,
    val commentaire: String? = null,
)

/** Un article dans une commande passée. */
data class ArticleCommande(
    val id: String,
    val produitId: String? = null,
    val image: String? = null,
    val label: String,
    val taille: String? = null,
    val quantité: Int? = null,
    val prix: String? = null,
    val modifiable: Boolean = false,
    val supprimable: Boolean = false,
)

/** Une commande passée, avec son état serveur (« en-cours » / « validée »). */
data class CommandeBoutique(
    val id: String,
    val date: String? = null,
    val prix: String? = null,
    val étatAlias: String? = null,
    val étatLabel: String? = null,
    val articles: List<ArticleCommande> = emptyList(),
    val supprimable: Boolean = false,
)

/** La réponse du POST de commande : les champs d'alerte du serveur. */
data class RésultatCommande(
    val succès: Boolean,
    val titre: String? = null,
    val message: String? = null,
)
