package school.greenwood.plus.logic

import school.greenwood.plus.model.Devoir
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * La carte focale du registre (docs/product/DESIGN.md §2).
 *
 * « Ce soir » n'est pas « aujourd'hui » : c'est ce que l'enfant doit préparer
 * pour la *prochaine rentrée* — les devoirs dont l'échéance est demain matin,
 * ou le lundi quand le week-end s'intercale. Un devoir rendu dans une semaine
 * reste une entrée discrète du flux, jamais dans la carte.
 *
 * ⚠︎ Question ouverte (docs/product/DESIGN.md §4) : la sémantique exacte des dates de
 * `devoirs` / `devoirs_date_v2` (date de don vs date de rendu). Le filtrage
 * vit uniquement ici — quand le doute sera levé sur un appel réel, un seul
 * endroit change.
 */
object CeSoir {

    /** Le prochain jour de rentrée après `today` : demain, sauf week-end. */
    fun prochaineRentree(today: LocalDate): LocalDate {
        var d = today.plusDays(1)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
            d = d.plusDays(1)
        }
        return d
    }

    /** Les devoirs à préparer pour la prochaine rentrée, triés par échéance. */
    fun devoirsDuSoir(devoirs: List<Devoir>, today: LocalDate): List<Devoir> {
        val horizon = prochaineRentree(today)
        return devoirs
            .filter { d -> d.dateRemise?.let { it > today && it <= horizon } == true }
            .sortedWith(compareBy({ it.dateRemise }, { it.matiere }))
    }
}
