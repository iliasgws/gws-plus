package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import school.greenwood.plus.data.api.CommunParams

/*
 * Les paramètres des listes publiques (APP.md §3) : les noms sans accent
 * (`matiere`, `problemeId`, `etat`) partent toujours tels quels — le serveur
 * les lit aussi bien que les formes accentuées — et les valeurs bornées
 * arrivent valides, jamais un 400 silencieux.
 */
class CommunParamsTest {

    @Test
    fun `aucun paramètre - limite et offset par défaut`() {
        assertEquals(
            mapOf("limite" to "50", "offset" to "0"),
            CommunParams.listes(),
        )
    }

    @Test
    fun `les noms de paramètres sont sans accent`() {
        val params = CommunParams.listes(
            tri = "votes",
            matière = "Mathématiques",
            état = "ouvert",
            problèmeId = 42L,
        )
        assertEquals("votes", params["tri"])
        assertEquals("Mathématiques", params["matiere"])
        assertEquals("ouvert", params["etat"])
        assertEquals("42", params["problemeId"])
        assertNull(params["matière"])
        assertNull(params["état"])
        assertNull(params["problèmeId"])
    }

    @Test
    fun `limite bornée entre 1 et 500`() {
        assertEquals("500", CommunParams.listes(limite = 9_999)["limite"])
        assertEquals("1", CommunParams.listes(limite = 0)["limite"])
        assertEquals("1", CommunParams.listes(limite = -3)["limite"])
    }

    @Test
    fun `offset négatif ramené à zéro`() {
        assertEquals("0", CommunParams.listes(offset = -10)["offset"])
        assertEquals("120", CommunParams.listes(offset = 120)["offset"])
    }

    @Test
    fun `valeurs vides ou en blanc ignorées`() {
        val params = CommunParams.listes(tri = "  ", matière = "", état = null)
        assertNull(params["tri"])
        assertNull(params["matiere"])
        assertNull(params["etat"])
    }

    @Test
    fun `depuis envoyé tel quel`() {
        assertEquals("1759000000000", CommunParams.listes(depuis = 1_759_000_000_000L)["depuis"])
    }
}
