package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.api.BotiEnvelope

/*
 * HTTP 200 même en erreur — l'enveloppe est le seul juge. « disconnect » tue
 * la session, « error » porte le message, sinon les données passent.
 */
class BotiEnvelopeTest {

    @Test
    fun `erreur avec message`() {
        val r = BotiEnvelope.analyser("""{"error":true,"msg":"Mot de passe incorrect"}""", "login")
        assertTrue(r is BotiEnvelope.Résultat.Erreur)
        assertEquals("Mot de passe incorrect", (r as BotiEnvelope.Résultat.Erreur).message)
    }

    @Test
    fun `erreur sans message - message par défaut`() {
        val r = BotiEnvelope.analyser("""{"error":true}""", "login")
        assertEquals("Erreur du serveur", (r as BotiEnvelope.Résultat.Erreur).message)
    }

    @Test
    fun `disconnect - session tuée`() {
        val r = BotiEnvelope.analyser("""{"disconnect":true}""", "devoirs")
        assertEquals(BotiEnvelope.Résultat.Déconnecté, r)
    }

    @Test
    fun `disconnect false n'est pas une déconnexion`() {
        val r = BotiEnvelope.analyser("""{"disconnect":false,"data":[]}""", "devoirs")
        assertTrue(r is BotiEnvelope.Résultat.Données)
    }

    @Test
    fun `données normales`() {
        val r = BotiEnvelope.analyser("""{"status":200,"msg":"Bienvenue !","keyToken":"abc"}""", "login")
        assertTrue(r is BotiEnvelope.Résultat.Données)
        assertTrue((r as BotiEnvelope.Résultat.Données).objet.containsKey("keyToken"))
    }

    @Test
    fun `corps illisible - erreur lisible`() {
        val r = BotiEnvelope.analyser("<br /><b>Notice</b>: PHP notice", "devoirs_date_v2")
        assertTrue(r is BotiEnvelope.Résultat.Erreur)
        assertTrue((r as BotiEnvelope.Résultat.Erreur).message.contains("devoirs_date_v2"))
    }

    @Test
    fun `tableau nu - réponse inattendue`() {
        val r = BotiEnvelope.analyser("""[1,2,3]""", "x")
        assertTrue(r is BotiEnvelope.Résultat.Erreur)
    }
}
