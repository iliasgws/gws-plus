package school.greenwood.plus

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import school.greenwood.plus.ui.screens.devoirs.joursDuMois

class CalendrierDevoirsTest {
    @Test fun `le mois commence dans la bonne colonne et contient tous ses jours`() {
        // February 2026 starts on Sunday, after six empty Monday-first cells.
        val mois = YearMonth.of(2026, 2)
        val grille = joursDuMois(mois)
        assertEquals(35, grille.size)
        (0..5).forEach { assertNull(grille[it]) }
        assertEquals(mois.atDay(1), grille[6])
        assertEquals((1..28).map(mois::atDay), grille.filterNotNull())
        assertNull(grille.last())
    }

    @Test fun `le calendrier inclut le 29 février et les mois sur six semaines`() {
        assertEquals(LocalDate.of(2024, 2, 29), joursDuMois(YearMonth.of(2024, 2)).filterNotNull().last())
        val août = joursDuMois(YearMonth.of(2026, 8))
        assertEquals(42, août.size)
        assertEquals(LocalDate.of(2026, 8, 31), août[35])
    }
}
