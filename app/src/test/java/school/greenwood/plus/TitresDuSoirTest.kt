package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.logic.TitresDuSoir
import kotlin.random.Random

class TitresDuSoirTest {
    @Test
    fun `cent titres distincts et courts`() {
        assertEquals(100, TitresDuSoir.textes.size)
        assertEquals(100, TitresDuSoir.textes.toSet().size)
        assertTrue(TitresDuSoir.textes.all { it.isNotBlank() && it.length <= 30 })
        assertTrue("Ce soir" in TitresDuSoir.textes)
    }

    @Test
    fun `tous les autres titres sont accessibles sans répétition immédiate`() {
        for (actuel in TitresDuSoir.textes.indices) {
            val random = Random(126)
            val tirages = List(2_000) { TitresDuSoir.suivant(actuel, random) }.toSet()
            assertEquals(TitresDuSoir.textes.indices.toSet() - actuel, tirages)
            tirages.forEach { suivant -> assertNotEquals(actuel, suivant) }
        }
    }
}
