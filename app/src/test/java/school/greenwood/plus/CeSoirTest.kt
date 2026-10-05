package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.logic.CeSoir
import school.greenwood.plus.model.Devoir
import java.time.LocalDate
import java.time.LocalTime

/*
 * La carte « Ce soir » (docs/product/DESIGN.md §2) : échéance = prochaine rentrée.
 * Vendredi → lundi ; samedi/dimanche → lundi ; sinon → demain.
 * « Ce soir » n'est pas « aujourd'hui » : une échéance du jour même n'entre pas.
 */
class CeSoirTest {

    private fun devoir(id: String, remise: LocalDate?) = Devoir(
        id = id,
        title = "Devoir $id",
        matiere = "Test",
        dateRemise = remise,
    )

    private val lundi = LocalDate.of(2026, 9, 14)      // lundi
    private val vendredi = LocalDate.of(2026, 9, 18)   // vendredi
    private val samedi = LocalDate.of(2026, 9, 19)
    private val dimanche = LocalDate.of(2026, 9, 20)
    private val lundiSuivant = LocalDate.of(2026, 9, 21)

    @Test
    fun `titre - soir à partir de 18 h jusqu'à minuit`() {
        val liste = listOf(devoir("a", lundiSuivant))
        assertEquals("1 devoir", CeSoir.titre(liste, LocalTime.of(17, 59, 59)))
        assertEquals("Ce soir", CeSoir.titre(liste, LocalTime.of(18, 0)))
        assertEquals("Ce soir", CeSoir.titre(liste, LocalTime.of(23, 59, 59)))
        assertEquals("1 devoir", CeSoir.titre(liste, LocalTime.MIDNIGHT))
        assertEquals("1 devoir", CeSoir.titre(liste, LocalTime.of(8, 0)))
    }

    @Test
    fun `compteur - exclut les faits officiels et locaux et suit leur annulation`() {
        val a = devoir("a", lundiSuivant)
        val b = devoir("b", lundiSuivant)
        val faits = listOf(a.copy(fait = true), b.copy(faitLocal = true))
        assertEquals(0, CeSoir.restants(faits))
        assertEquals("0 devoir", CeSoir.titre(faits, LocalTime.NOON))
        assertEquals("1 devoir", CeSoir.titre(listOf(faits[0], b), LocalTime.NOON))
        assertEquals("2 devoirs", CeSoir.titre(listOf(a, b), LocalTime.NOON))
        assertEquals("0 devoir", CeSoir.titre(emptyList(), LocalTime.NOON))
    }

    @Test
    fun `horizon - jeudi, demain vendredi`() {
        assertEquals(LocalDate.of(2026, 9, 18), CeSoir.prochaineRentree(LocalDate.of(2026, 9, 17)))
    }

    @Test
    fun `horizon - vendredi, lundi`() {
        assertEquals(lundiSuivant, CeSoir.prochaineRentree(vendredi))
    }

    @Test
    fun `horizon - samedi et dimanche, lundi`() {
        assertEquals(lundiSuivant, CeSoir.prochaineRentree(samedi))
        assertEquals(lundiSuivant, CeSoir.prochaineRentree(dimanche))
    }

    @Test
    fun `vendredi - échéances du week-end et lundi comprises`() {
        val devoirs = listOf(
            devoir("a", LocalDate.of(2026, 9, 19)),  // samedi
            devoir("b", dimanche),
            devoir("c", lundiSuivant),
            devoir("d", LocalDate.of(2026, 9, 22)),  // mardi, hors horizon
            devoir("e", vendredi),                    // rendu vendredi même : pas « ce soir »
            devoir("f", null),
        )
        val duSoir = CeSoir.devoirsDuSoir(devoirs, vendredi)
        assertEquals(listOf("a", "b", "c"), duSoir.map { it.id })
    }

    @Test
    fun `jeudi - seulement demain`() {
        val jeudi = LocalDate.of(2026, 9, 17)
        val duSoir = CeSoir.devoirsDuSoir(
            listOf(
                devoir("a", LocalDate.of(2026, 9, 18)),
                devoir("b", samedi),
                devoir("c", jeudi),
                devoir("d", null),
            ),
            jeudi,
        )
        assertEquals(listOf("a"), duSoir.map { it.id })
    }

    @Test
    fun `vide - rien à préparer`() {
        assertTrue(CeSoir.devoirsDuSoir(emptyList(), lundi).isEmpty())
    }

    @Test
    fun `tri par échéance puis matière`() {
        val duSoir = CeSoir.devoirsDuSoir(
            listOf(
                devoir("b", lundiSuivant),
                devoir("a", samedi),
            ),
            vendredi,
        )
        assertEquals(listOf("a", "b"), duSoir.map { it.id })
    }
}
