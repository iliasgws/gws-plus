package school.greenwood.plus.util

import java.time.LocalDateTime

/*
 * Issue #150 (v2) : le prompt pré-rempli envoyé à ChatGPT. Construit ici, hors
 * Compose et hors Android, pour rester testable. Structure imposée par l'issue :
 * profil de l'école vérifié → élément sélectionné EN ENTIER (avec ses vraies
 * pièces jointes partagées en flux) → index COMPLET des autres devoirs
 * (métadonnées seules) → index COMPLET des actualités (titres seulement, jamais
 * de corps) → instructions qui interdisent d'inventer à partir des titres.
 */

/**
 * Plafond du texte partagé : un `EXTRA_TEXT` de dizaines de kilo-octets passe
 * partout, mais au-delà de cette limite les INDEX sont tronqués — jamais en
 * silence, chaque troncature est annoncée dans le prompt et bascule l'index en
 * « partiel ».
 */
const val LIMITE_TEXTE_CHATGPT = 48_000

/**
 * Profil de l'école — uniquement des faits vérifiés sur le site officiel
 * https://greenwoodschool.ma/ (relu le 10/10/2026 : Bouskoura/Casablanca,
 * maternelle → lycée, trilingue arabe/français/anglais, pédagogie de projet et
 * PET®), plus le rôle réel de GWS Plus. Aucune promesse inventée.
 */
val PROFIL_ÉCOLE_CHATGPT = """
Greenwood School (GWS) — école privée à Bouskoura, Casablanca (Maroc), du
cycle maternel au lycée. Enseignement trilingue (arabe, français, anglais),
pédagogie de projet et apprentissage expérientiel : le PET® (Projet
Expérientiel Transversal), dans une approche centrée sur l'élève.
Site officiel : https://greenwoodschool.ma/

GWS Plus est l'application Android officielle de l'école pour les familles
(cahier de liaison, devoirs, actualités, documents, messages). Ce n'est pas
l'école : elle n'invente ni règlement, ni note, ni communication officielle.
""".trimIndent()

/** Complétude d'un index — jamais « complète » si on s'est arrêté trop tôt. */
enum class Completude { COMPLÈTE, PARTIELLE }

/** Une ligne d'index de devoir : métadonnées compactes, JAMAIS le corps. */
data class LigneDevoir(
    val titre: String,
    val matière: String? = null,
    val échéance: String? = null,
    val état: String? = null,
    val identifiant: String? = null,
)

/** Une ligne d'index d'actualité : titre et date, JAMAIS le corps. */
data class LigneActualité(
    val titre: String,
    val date: String? = null,
    val categorie: String? = null,
    val identifiant: String? = null,
)

/** L'élément sélectionné, en entier. */
data class ItemPartagé(
    /** « Devoir » ou « Actualité ». */
    val type: String,
    val titre: String,
    /** Lignes « Clé : valeur » déjà formatées (matière, échéance, auteur…). */
    val métadonnées: List<String> = emptyList(),
    /** Corps de l'élément en texte brut (HTML retiré), ou null. */
    val corps: String? = null,
    /** Noms des pièces jointes connues du serveur. */
    val piècesJointes: List<String> = emptyList(),
    /** Noms des fichiers réellement joints à l'intention Android. */
    val fichiersPartagés: List<String> = emptyList(),
)

/** Instructions finales — catalogue ≠ contenu, rien d'inventé, UI riche si utile. */
private val INSTRUCTIONS_CHATGPT = """
INSTRUCTIONS
- Aide pour l'élément sélectionné en utilisant son contenu complet et les
  fichiers joints à ce message.
- Les deux index ci-dessus sont un CATALOGUE d'éléments disponibles, pas leur
  contenu. Quand une question exige le détail d'un autre devoir ou d'une autre
  actualité, dis à l'utilisateur d'ouvrir précisément cet élément dans GWS Plus,
  puis de le copier ou de le partager dans cette conversation.
- N'invente jamais un détail à partir des seuls titres (consignes de travail,
  règlement, annonces non ouvertes : inconnus tant qu'ils ne sont pas partagés).
- Quand c'est utile, appuie-toi des interfaces riches prises en charge :
  sections structurées, tableaux, listes de contrôle, chronologies. Ne promets
  aucun composant interactif en particulier.
- Reste clair et orienté aide aux apprentissages.
""".trimIndent()

/** « - titre | matière | échéance | état | id » — jamais vide. */
internal fun ligneDevoirTexte(l: LigneDevoir): String = listOfNotNull(
    l.titre.ifBlank { "(sans titre)" },
    l.matière?.takeIf { it.isNotBlank() },
    l.échéance?.takeIf { it.isNotBlank() },
    l.état?.takeIf { it.isNotBlank() },
    l.identifiant?.takeIf { it.isNotBlank() }?.let { "id $it" },
).joinToString(" | ")

/** « - date | catégorie | titre | id » — le corps n'entre jamais ici. */
internal fun ligneActualitéTexte(l: LigneActualité): String = listOfNotNull(
    l.date?.takeIf { it.isNotBlank() },
    l.categorie?.takeIf { it.isNotBlank() },
    l.titre.ifBlank { "(sans titre)" },
    l.identifiant?.takeIf { it.isNotBlank() }?.let { "id $it" },
).joinToString(" | ")

/**
 * Le prompt complet : profil, élément sélectionné, deux index, instructions.
 * Si la taille dépasse [LIMITE_TEXTE_CHATGPT], les index perdent leurs
 * dernières lignes — une troncature est toujours annoncée et l'index bascule
 * en [Completude.PARTIELLE] ; si même ça ne suffit pas, c'est le CORPS de
 * l'élément qui est tronqué, explicitement.
 */
fun texteChatGPT(
    item: ItemPartagé,
    devoirs: List<LigneDevoir>,
    actualités: List<LigneActualité>,
    synchronisation: LocalDateTime,
    completudeDevoirs: Completude = Completude.COMPLÈTE,
    completudeActualités: Completude = Completude.COMPLÈTE,
): String {
    val lignesDevoirs = devoirs.map(::ligneDevoirTexte)
    val lignesActualités = actualités.map(::ligneActualitéTexte)

    var resteDevoirs = lignesDevoirs
    var resteActualités = lignesActualités
    var troncDevoirs = 0
    var troncActualités = 0
    var complétudeD = completudeDevoirs
    var complétudeA = completudeActualités

    var corpsTronqué = false
    fun assembler(corps: String? = item.corps): String = rendre(
        item = item.copy(corps = corps),
        devoirs = resteDevoirs,
        actualités = resteActualités,
        synchronisation = synchronisation,
        completudeDevoirs = complétudeD,
        completudeActualités = complétudeA,
        troncDevoirs = troncDevoirs,
        troncActualités = troncActualités,
        corpsTronqué = corpsTronqué,
    )

    // 1) On vide d'abord les INDEX (jamais l'élément sélectionné).
    while (assembler().length > LIMITE_TEXTE_CHATGPT && (resteDevoirs.isNotEmpty() || resteActualités.isNotEmpty())) {
        if (resteDevoirs.size >= resteActualités.size && resteDevoirs.isNotEmpty()) {
            resteDevoirs = resteDevoirs.dropLast(1)
            troncDevoirs++
            complétudeD = Completude.PARTIELLE
        } else {
            resteActualités = resteActualités.dropLast(1)
            troncActualités++
            complétudeA = Completude.PARTIELLE
        }
    }

    // 2) Index vides et toujours trop gros : le corps de l'élément est
    //    tronqué à son tour, et le prompt le dit.
    var corps = item.corps
    while (corps != null && corps.length > 1_000 && assembler(corps).length > LIMITE_TEXTE_CHATGPT) {
        corps = corps.take(corps.length / 2)
    }
    corpsTronqué = corps != null && item.corps != null && corps.length < item.corps.length
    return assembler(corps)
}

private fun rendre(
    item: ItemPartagé,
    devoirs: List<String>,
    actualités: List<String>,
    synchronisation: LocalDateTime,
    completudeDevoirs: Completude,
    completudeActualités: Completude,
    troncDevoirs: Int,
    troncActualités: Int,
    corpsTronqué: Boolean,
): String = buildString {
    appendLine("PROFIL DE L'ÉCOLE")
    appendLine(PROFIL_ÉCOLE_CHATGPT)
    appendLine()

    appendLine("ÉLÉMENT SÉLECTIONNÉ — CONTENU COMPLET")
    appendLine("Type : ${item.type}")
    appendLine("Titre : ${item.titre.ifBlank { "(sans titre)" }}")
    item.métadonnées.filter { it.isNotBlank() }.forEach { appendLine(it) }
    if (item.piècesJointes.isNotEmpty()) {
        appendLine("Pièces jointes connues : ${item.piècesJointes.joinToString(", ")}")
    }
    appendLine(
        if (item.fichiersPartagés.isEmpty()) {
            "Fichiers joints à ce message : aucun (noms listés ci-dessus si présents)"
        } else {
            "Fichiers joints à ce message : ${item.fichiersPartagés.joinToString(", ")}"
        }
    )
    appendLine()
    item.corps?.takeIf { it.isNotBlank() }?.let {
        appendLine(it)
        appendLine()
    }
    if (corpsTronqué) {
        appendLine(
            "CORPS TRONQUÉ pour la taille du partage — le texte intégral de " +
                "l'élément reste dans GWS Plus."
        )
        appendLine()
    }

    appendLine("AUTRES DEVOIRS DISPONIBLES — INDEX COMPLET (métadonnées seulement)")
    appendLine(libelléIndex(devoirs.size, completudeDevoirs, synchronisation, troncDevoirs, "devoirs"))
    devoirs.forEach { appendLine("- $it") }
    appendLine()

    appendLine("ACTUALITÉS DE L'ÉCOLE — INDEX COMPLET (titres seulement)")
    appendLine(libelléIndex(actualités.size, completudeActualités, synchronisation, troncActualités, "actualités"))
    actualités.forEach { appendLine("- $it") }
    appendLine()

    appendLine(INSTRUCTIONS_CHATGPT)
}.trimEnd()

private fun libelléIndex(
    taille: Int,
    completude: Completude,
    synchronisation: LocalDateTime,
    troncature: Int,
    pluriel: String,
): String = buildString {
    append("Index de $taille $pluriel — ")
    append(
        when (completude) {
            Completude.COMPLÈTE -> "complet"
            Completude.PARTIELLE -> "PARTIEL (pas tous les éléments accessibles sont listés)"
        }
    )
    append(", synchronisé le ${synchronisation.frenchFull().removePrefix("le ")}")
    if (troncature > 0) {
        append(". TRONQUÉ pour la taille du partage : $troncature $pluriel non listé")
        if (troncature > 1) append("s")
        append(" — ouvre-les un par un dans GWS Plus pour les partager")
    }
    append(".")
}
