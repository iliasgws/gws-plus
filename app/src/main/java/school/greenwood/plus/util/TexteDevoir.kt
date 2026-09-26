package school.greenwood.plus.util

import java.time.LocalDate
import school.greenwood.plus.model.Devoir

/*
 * Issue #85 : appui long sur une carte devoir → copier toutes les infos
 * utiles dans le presse-papiers. Le texte est construit ici, hors Compose,
 * pour rester testable ; la carte se contente de le copier et d'afficher
 * un retour visuel.
 */

/** Texte multi-lignes lisible d'un devoir : titre, matière, enseignant,
 *  état « fait » (école ou « fait pour moi »), pièces jointes et corps. */
fun devoirTexteÀCopier(devoir: Devoir, aujourdhui: LocalDate): String {
    val lignes = buildList {
        add("Devoir : ${devoir.title}")
        if (devoir.matiere.isNotBlank()) add("Matière : ${devoir.matiere}")
        devoir.categorie?.takeIf { it.isNotBlank() }?.let { add("Catégorie : $it") }
        devoir.enseignant?.takeIf { it.isNotBlank() }?.let { add("Enseignant(e) : $it") }
        add(
            "État : " + when {
                devoir.fait -> "Travail fait (connu de l'école)"
                devoir.faitLocal -> "Fait pour moi (local, invisible à l'école)"
                devoir.dateRemise?.isBefore(aujourdhui) == true -> "À faire (échéance passée)"
                else -> "À faire"
            }
        )
        if (devoir.attachments.isNotEmpty()) {
            add("Pièces jointes :")
            devoir.attachments.forEach { add("- ${it.name}") }
        }
    }
    val corps = devoir.description?.takeIf { it.isNotBlank() }
        ?.let { it.htmlToPlainMultiline() }
        ?.takeIf { it.isNotBlank() }
    val entête = lignes.joinToString("\n")
    return if (corps != null) "$entête\n\n$corps" else entête
}