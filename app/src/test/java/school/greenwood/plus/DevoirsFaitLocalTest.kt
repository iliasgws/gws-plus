package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.repo.avecFaitLocal
import school.greenwood.plus.model.Devoir

/*
 * Marquage « fait pour moi » (issue #82) : la fusion entre la liste serveur et
 * les ids marqués localement ne touche QUE `faitLocal` — le fait officiel du
 * serveur reste ce qu'il est, et rien d'autre ne bouge.
 */
class DevoirsFaitLocalTest {

    private fun devoir(id: String, fait: Boolean = false) =
        Devoir(id = id, title = "Devoir $id", matiere = "Mathématiques", fait = fait)

    @Test
    fun `liste vide de marques ne change rien`() {
        val liste = listOf(devoir("a"), devoir("b", fait = true))
        assertEquals(liste, avecFaitLocal(liste, emptySet()))
    }

    @Test
    fun `les ids marqués portent faitLocal`() {
        val liste = listOf(devoir("a"), devoir("b"))
        val résultat = avecFaitLocal(liste, setOf("b"))
        assertFalse(résultat[0].faitLocal)
        assertTrue(résultat[1].faitLocal)
    }

    @Test
    fun `le fait officiel du serveur est préservé`() {
        val liste = listOf(devoir("a", fait = true), devoir("b"))
        // « a » : fait officiel seulement ; « b » : marqué localement seulement.
        val résultat = avecFaitLocal(liste, setOf("b"))
        assertTrue(résultat[0].fait)
        assertFalse(résultat[0].faitLocal)
        assertTrue(résultat[1].faitLocal)
        assertFalse(résultat[1].fait)
    }

    @Test
    fun `un id inconnu est simplement ignoré`() {
        val liste = listOf(devoir("a"))
        val résultat = avecFaitLocal(liste, setOf("supprimé-du-serveur"))
        assertFalse(résultat[0].faitLocal)
        assertEquals(1, résultat.size)
    }
}
