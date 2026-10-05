package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.logic.TitresDuJour
import kotlin.random.Random

class TitresDuJourTest {
    @Test
    fun `cent variations distinctes avec compteur singulier pluriel et zéro`() {
        assertEquals(100, TitresDuJour.textes.size)
        assertEquals(100, TitresDuJour.textes.toSet().size)
        assertTrue(TitresDuJour.textes.all { it.isNotBlank() && it.length <= 30 })
        for (index in TitresDuJour.textes.indices) {
            assertTrue(TitresDuJour.titre(index, 0).startsWith("0 devoir · "))
            assertTrue(TitresDuJour.titre(index, 1).startsWith("1 devoir · "))
            assertTrue(TitresDuJour.titre(index, 2).startsWith("2 devoirs · "))
            assertTrue(TitresDuJour.titre(index, 12).startsWith("12 devoirs · "))
        }
    }

    @Test
    fun `chaque autre variation est accessible sans répétition immédiate`() {
        for (actuel in TitresDuJour.textes.indices) {
            val random = Random(126)
            val tirages = List(2_000) { TitresDuJour.suivant(actuel, random) }.toSet()
            assertEquals(TitresDuJour.textes.indices.toSet() - actuel, tirages)
        }
    }
}
