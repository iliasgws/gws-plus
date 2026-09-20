package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.api.BotiEnvelope

/*
 * HTTP 200 même en erreur — l'enveloppe est le seul juge. « disconnect » tue
 * la session, « error » porte le message, sinon les données passent. Un corps
 * qui n'est pas du JSON (HTML d'interception, vide) est marqué « illisible »
 * avec un extrait court pour le diagnostic (issue #14).
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
    fun `corps illisible - drapeau et extrait courts sans débordement`() {
        val page = "<html>" + "<p>Service Temporarily Unavailable </p>".repeat(20)
        val r = BotiEnvelope.analyser(page, "messages") as BotiEnvelope.Résultat.Erreur
        assertTrue(r.illisible)
        assertTrue(r.message.startsWith("Réponse illisible du serveur (messages) : « "))
        // Extrait borné : le message reste tenu sur une capture d'écran.
        val extrait = r.message.substringAfter("« ").substringBefore(" »")
        assertTrue(extrait.length <= 80)
        assertFalse(extrait.contains("\n"))
    }

    @Test
    fun `corps vide - réponse vide, jamais un extrait fantôme`() {
        val r = BotiEnvelope.analyser("   ", "messages") as BotiEnvelope.Résultat.Erreur
        assertTrue(r.illisible)
        assertEquals("Réponse vide du serveur (messages)", r.message)
    }

    @Test
    fun `erreur enveloppe classique n'est pas marquée illisible`() {
        val r = BotiEnvelope.analyser("""{"error":true,"msg":"Mot de passe incorrect"}""", "login")
            as BotiEnvelope.Résultat.Erreur
        assertFalse(r.illisible)
    }

    @Test
    fun `tableau nu - réponse inattendue`() {
        val r = BotiEnvelope.analyser("""[1,2,3]""", "x")
        assertTrue(r is BotiEnvelope.Résultat.Erreur)
    }

    /** Vu en production le 20/09/2026 : le serveur `messages` a renvoyé un
     *  `print_r` PHP (« Models\Inscription Object (…) ») à la place du JSON. */
    private val dumpPhp = "Models\\Inscription Object (\n" +
        "    [months] =>\n" +
        "    [attrs:protected] => Array\n" +
        "        (\n" +
        "            [ID] => 481\n" +
        "        )\n" +
        "\n" +
        ")"

    @Test
    fun `dump php devant le json - données récupérées`() {
        val r = BotiEnvelope.analyser("$dumpPhp\n{\"data\":[],\"themes\":[]}", "messages")
        assertTrue(r is BotiEnvelope.Résultat.Données)
        assertTrue((r as BotiEnvelope.Résultat.Données).objet.containsKey("data"))
    }

    @Test
    fun `dump php après le json - données récupérées`() {
        val r = BotiEnvelope.analyser("""{"data":[]} $dumpPhp""", "messages")
        assertTrue(r is BotiEnvelope.Résultat.Données)
    }

    @Test
    fun `dump php avec error enveloppé - l'erreur du serveur passe quand même`() {
        val r = BotiEnvelope.analyser("$dumpPhp\n{\"error\":true,\"msg\":\"Erreur interne\"}", "messages")
        assertEquals("Erreur interne", (r as BotiEnvelope.Résultat.Erreur).message)
        assertFalse(r.illisible)
    }

    @Test
    fun `dump php seul sans json - reste illisible avec extrait`() {
        val r = BotiEnvelope.analyser(dumpPhp, "messages") as BotiEnvelope.Résultat.Erreur
        assertTrue(r.illisible)
        assertTrue(r.message.contains("Models\\Inscription"))
    }

    @Test
    fun `json cassé au milieu - pas de fausse récupération`() {
        val r = BotiEnvelope.analyser("""{"data":[,garbage}""", "messages") as BotiEnvelope.Résultat.Erreur
        assertTrue(r.illisible)
    }
}
