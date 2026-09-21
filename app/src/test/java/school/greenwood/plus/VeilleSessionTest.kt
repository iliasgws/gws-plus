package school.greenwood.plus

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.session.VeilleSession

/*
 * La veille : une absence d'au moins la durée choisie (réglée dans les
 * Paramètres) émet un seul signal de rafraîchissement à la reprise — jamais
 * au démarrage à froid, jamais en dessous du seuil, jamais quand le réglage
 * vaut « jamais » (0).
 */
class VeilleSessionTest {

    private val cinqMinutes = 5 * 60_000L

    @Test
    fun `reprise après la durée choisie émet le signal`() {
        val veille = VeilleSession()
        veille.enregistrerArrêt(0L)
        assertTrue(veille.enregistrerReprise(minutes = 5, horodatage = cinqMinutes + 60_000L))
    }

    @Test
    fun `reprise exactement au seuil émet le signal`() {
        val veille = VeilleSession()
        veille.enregistrerArrêt(0L)
        assertTrue(veille.enregistrerReprise(minutes = 5, horodatage = cinqMinutes))
    }

    @Test
    fun `reprise en dessous du seuil n'émet rien`() {
        val veille = VeilleSession()
        veille.enregistrerArrêt(0L)
        assertFalse(veille.enregistrerReprise(minutes = 5, horodatage = cinqMinutes - 1L))
    }

    @Test
    fun `aucun arrêt enregistré la reprise au démarrage n'émet rien`() {
        val veille = VeilleSession()
        assertFalse(veille.enregistrerReprise(minutes = 5, horodatage = 100 * 60_000L))
    }

    @Test
    fun `une seule émission par reprise`() {
        val veille = VeilleSession()
        veille.enregistrerArrêt(0L)
        assertTrue(veille.enregistrerReprise(minutes = 5, horodatage = cinqMinutes + 60_000L))
        // Sans nouvel arrêt, la reprise suivante ne réémet pas.
        assertFalse(veille.enregistrerReprise(minutes = 5, horodatage = cinqMinutes + 120_000L))
    }

    @Test
    fun `réglage jamais ne rafraîchit jamais`() {
        val veille = VeilleSession()
        veille.enregistrerArrêt(0L)
        assertFalse(veille.enregistrerReprise(minutes = 0, horodatage = 10 * 60_000L))
    }

    @Test
    fun `la durée choisie remplace le seuil par défaut`() {
        val longue = VeilleSession()
        longue.enregistrerArrêt(0L)
        // 6 minutes d'absence ne suffisent pas pour un réglage à 10 minutes.
        assertFalse(longue.enregistrerReprise(minutes = 10, horodatage = 6 * 60_000L))

        val courte = VeilleSession()
        courte.enregistrerArrêt(0L)
        // 1 minute et demie suffit pour un réglage à 1 minute.
        assertTrue(courte.enregistrerReprise(minutes = 1, horodatage = 90_000L))
    }

    @Test
    fun `cycles courts puis une absence longue émettent une fois`() {
        val veille = VeilleSession()
        veille.enregistrerArrêt(0L)
        assertFalse(veille.enregistrerReprise(minutes = 5, horodatage = 60_000L))
        veille.enregistrerArrêt(60_000L)
        assertFalse(veille.enregistrerReprise(minutes = 5, horodatage = 90_000L))
        veille.enregistrerArrêt(90_000L)
        assertTrue(veille.enregistrerReprise(minutes = 5, horodatage = 90_000L + 6 * 60_000L))
    }

    @Test
    fun `un nouvel arrêt remplace l'ancien`() {
        val veille = VeilleSession()
        veille.enregistrerArrêt(0L)
        veille.enregistrerArrêt(60_000L)
        // L'absence compte depuis l'arrêt le plus récent.
        assertFalse(veille.enregistrerReprise(minutes = 5, horodatage = 60_000L + cinqMinutes - 1_000L))
    }

    @Test
    fun `le signal parvient aux écrans à l'écoute`() = runBlocking {
        val veille = VeilleSession()
        veille.enregistrerArrêt(0L)
        val reçu = async(start = CoroutineStart.UNDISPATCHED) { veille.retoursPérimés.first() }
        assertTrue(veille.enregistrerReprise(minutes = 5, horodatage = cinqMinutes + 60_000L))
        assertEquals(Unit, withTimeout(1_000) { reçu.await() })
    }
}
