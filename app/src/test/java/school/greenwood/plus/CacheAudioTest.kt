package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.util.CacheAudio
import java.io.File

/*
 * Cache voix (issue #108) : le nom local doit survivre à la ré-signature de
 * l'URL (même ressource, jeton différent → même fichier, jamais le jeton
 * dans le nom) et l'évacuation doit vider les pièces les plus anciennes sans
 * jamais toucher à la fraîchement ajoutée. Logique pure : ni Context, ni
 * réseau, ni disque réel.
 */
class CacheAudioTest {

    private val signée =
        "https://media.boti.education/view/AAAABBBBCCCC.1788283943/original/" +
            "assets/schools/greenwood/audio/voix1.mp3"
    private val réSignée =
        "https://media.boti.education/view/ZZZZYYYYXXXX.1788289999/original/" +
            "assets/schools/greenwood/audio/voix1.mp3"
    private val autre =
        "https://media.boti.education/view/KKKKLLLLMMMM.1788283943/original/" +
            "assets/schools/greenwood/audio/voix2.mp3"

    @Test
    fun `nom en cache stable malgré la ré-signature`() {
        assertEquals(CacheAudio.nomEnCache(signée), CacheAudio.nomEnCache(réSignée))
    }

    @Test
    fun `nom en cache distinct selon la ressource`() {
        assertFalse(CacheAudio.nomEnCache(signée) == CacheAudio.nomEnCache(autre))
    }

    @Test
    fun `le jeton signé n'entraîne jamais dans le nom`() {
        val nom = CacheAudio.nomEnCache(signée)
        assertFalse(nom.contains("AAAABBBBCCCC"))
        assertFalse(nom.contains("1788283943"))
        assertTrue(nom.startsWith("voix"))
    }

    @Test
    fun `sous la limite rien n'est évacué`() {
        val a = File("audio/a.mp3")
        val b = File("audio/b.mp3")
        val entrées = listOf(
            CacheAudio.Entrée(a, 10, modifiéLe = 1_000),
            CacheAudio.Entrée(b, 10, modifiéLe = 2_000),
        )
        assertEquals(emptyList<File>(), CacheAudio.fichiersÀÉvacuer(entrées, limite = 25, àGarder = b))
    }

    @Test
    fun `au-delà les plus anciens partent d'abord`() {
        val a = File("audio/a.mp3")
        val b = File("audio/b.mp3")
        val c = File("audio/c.mp3")
        val entrées = listOf(
            CacheAudio.Entrée(c, 10, modifiéLe = 3_000),
            CacheAudio.Entrée(a, 10, modifiéLe = 1_000),
            CacheAudio.Entrée(b, 10, modifiéLe = 2_000),
        )
        // Total 30 > 15 : a puis b partent, c (la fraîchement ajoutée) reste.
        assertEquals(
            listOf(a, b),
            CacheAudio.fichiersÀÉvacuer(entrées, limite = 15, àGarder = c),
        )
    }

    @Test
    fun `la pièce fraîchement ajoutée est protégée même la plus ancienne`() {
        val neuve = File("audio/neuve.mp3")
        val vieille = File("audio/vieille.mp3")
        val entrées = listOf(
            CacheAudio.Entrée(neuve, 20, modifiéLe = 500),
            CacheAudio.Entrée(vieille, 20, modifiéLe = 50_000),
        )
        // Total 40 > 30 : seule la vieille peut partir, la neuve (la plus
        // ancienne des deux) est exclue de l'évacuation.
        assertEquals(
            listOf(vieille),
            CacheAudio.fichiersÀÉvacuer(entrées, limite = 30, àGarder = neuve),
        )
    }

    @Test
    fun `une seule pièce au-dessus de la limite ne s'évacue pas elle-même`() {
        val seule = File("audio/seule.mp3")
        val entrées = listOf(CacheAudio.Entrée(seule, 50, modifiéLe = 1_000))
        assertEquals(
            emptyList<File>(),
            CacheAudio.fichiersÀÉvacuer(entrées, limite = 40, àGarder = seule),
        )
    }
}
