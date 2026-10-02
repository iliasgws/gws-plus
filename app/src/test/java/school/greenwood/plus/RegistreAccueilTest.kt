package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.ui.screens.registre.actualiteUneVerticale
import school.greenwood.plus.ui.screens.registre.ceSoirVertical
import school.greenwood.plus.ui.screens.registre.échéanceCeSoir
import school.greenwood.plus.ui.screens.registre.piluleAccueil
import java.time.LocalDate

/*
 * Les logiques pures du haut de l'accueil (issue #101) : pilule contextuelle,
 * échéance complète de « Ce soir » et bascules de composition réactive.
 * Elles sont testées ici sans Compose pour que la règle de conception (une
 * vraie condition, jamais un état inventé) reste vérifiable.
 */
class RegistreAccueilTest {

    private val vendredi = LocalDate.of(2026, 10, 2)   // vendredi 2 octobre
    private val lundi = LocalDate.of(2026, 10, 5)       // lundi 5 octobre

    @Test
    fun `pilule - le vendredi, une seule vraie condition de jour`() {
        assertEquals("Bonne fin de semaine !", piluleAccueil(vendredi))
    }

    @Test
    fun `pilule - aucun autre jour de la semaine ne fabrique d'état`() {
        for (jour in 0L..6L) {
            val date = vendredi.plusDays(jour)
            if (date.dayOfWeek == java.time.DayOfWeek.FRIDAY) continue
            assertNull("pilule inattendue pour $date", piluleAccueil(date))
        }
    }

    @Test
    fun `échéance - date complète, en français accentué`() {
        assertEquals("à rendre pour lundi 5 octobre", échéanceCeSoir(lundi))
    }

    @Test
    fun `échéance - la rentrée du lendemain porte aussi sa date`() {
        assertEquals("à rendre pour mardi 6 octobre", échéanceCeSoir(lundi.plusDays(1)))
    }

    @Test
    fun `ce soir - composition verticale sur petit écran`() {
        assertTrue(ceSoirVertical(280f))   // 320 dp de large
        assertTrue(ceSoirVertical(320f))   // 360 dp de large
        assertTrue(ceSoirVertical(343.9f))
    }

    @Test
    fun `ce soir - composition en deux colonnes sur écran standard`() {
        assertFalse(ceSoirVertical(344f))
        assertFalse(ceSoirVertical(353f))   // 393 dp de large
        assertFalse(ceSoirVertical(390f))   // 430 dp de large
    }

    @Test
    fun `actualité à la une - vignette au-dessus seulement sous 300 dp utiles`() {
        assertTrue(actualiteUneVerticale(280f, échellePolice = 1f))   // 320 dp de large
        assertTrue(actualiteUneVerticale(299.9f, échellePolice = 1f))
        assertFalse(actualiteUneVerticale(300f, échellePolice = 1f))
    }

    @Test
    fun `actualité à la une - vignette à gauche sur écran standard`() {
        assertFalse(actualiteUneVerticale(320f, échellePolice = 1f))  // 360 dp de large
        assertFalse(actualiteUneVerticale(353f, échellePolice = 1f))
        assertFalse(actualiteUneVerticale(353f, échellePolice = 1.3f))
    }

    @Test
    fun `actualité à la une - police agrandie repasse en colonne`() {
        assertTrue(actualiteUneVerticale(353f, échellePolice = 1.31f))
        assertTrue(actualiteUneVerticale(390f, échellePolice = 2f))
    }
}
