package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import school.greenwood.plus.util.extractDate
import school.greenwood.plus.util.extractDateTime
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * Le serveur envoie des dates d'affichage bricolées : emojis, préfixes « Vu
 * le », ISO, formats courts de l'administration. L'extraction par regex doit
 * encaisser tout ça.
 */
class DatesTest {

    @Test
    fun `date affichée avec emoji et préfixe`() {
        val d = extractDateTime("📥 le 01/09/2026 à 12:47")
        assertEquals(LocalDateTime.of(2026, 9, 1, 12, 47), d)
    }

    @Test
    fun `intro avec badge vu le`() {
        val d = extractDate("Vu le 01/09/2026 à 12:54")
        assertEquals(LocalDate.of(2026, 9, 1), d)
    }

    @Test
    fun `iso complet`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 9, 9, 32, 5),
            extractDateTime("2026-09-09 09:32:05"),
        )
    }

    @Test
    fun `iso date seule`() {
        assertEquals(LocalDate.of(2026, 6, 22), extractDate("2026-06-22"))
    }

    @Test
    fun `chaine de publication avec mois français`() {
        // « Publié le 2026 Juin 11 » — pas de chiffres collés en dd/mm :
        // l'extraction ne doit PAS inventer une date fausse.
        assertNull(extractDate("Publié le 2026 Juin 11"))
    }

    @Test
    fun `null et vide`() {
        assertNull(extractDate(null))
        assertNull(extractDate(""))
        assertNull(extractDateTime("pas de date ici"))
    }

    @Test
    fun `date invalide rejetée`() {
        assertNull(extractDate("32/13/2026"))
        assertNull(extractDate("99/99/9999"))
    }

    @Test
    fun `format court administration`() {
        val d = school.greenwood.plus.data.repo.Normalizers.parseCreationCourte("07/09/26 13h59")
        assertEquals(LocalDateTime.of(2026, 9, 7, 13, 59), d)
    }
}
