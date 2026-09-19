package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import school.greenwood.plus.data.cache.MemoireSession

/*
 * Le cache de dernière donnée connue (issue #21) n'est servi que si la clé de
 * session lue est exactement celle de l'écriture : changer de compte ou
 * d'enfant rend mécaniquement l'ancien contenu introuvable.
 */
class CacheSessionTest {

    @Test
    fun `même clé sert la valeur`() {
        val cache = MemoireSession<String>()
        cache.écrire("parent/enfant-1", "registre du jour")
        assertEquals("registre du jour", cache.lire("parent/enfant-1"))
    }

    @Test
    fun `clé différente renvoie null`() {
        val cache = MemoireSession<String>()
        cache.écrire("parent/enfant-1", "registre du jour")
        assertNull(cache.lire("parent/enfant-2"))
        assertNull(cache.lire("autre-parent/enfant-1"))
    }

    @Test
    fun `clé nulle ou blanche renvoie null`() {
        val cache = MemoireSession<String>()
        cache.écrire("parent/enfant-1", "registre du jour")
        assertNull(cache.lire(null))
        assertNull(cache.lire(""))
        assertNull(cache.lire("   "))
    }

    @Test
    fun `rien en cache avant la première écriture`() {
        val cache = MemoireSession<String>()
        assertNull(cache.lire("parent/enfant-1"))
    }

    @Test
    fun `vider efface donnée et clé`() {
        val cache = MemoireSession<String>()
        cache.écrire("parent/enfant-1", "registre du jour")
        cache.vider()
        // Même la clé d'origine ne resservira rien : la clé a disparu aussi.
        assertNull(cache.lire("parent/enfant-1"))
    }

    @Test
    fun `écriture écrase la valeur précédente`() {
        val cache = MemoireSession<String>()
        cache.écrire("parent/enfant-1", "ancien")
        cache.écrire("parent/enfant-1", "nouveau")
        assertEquals("nouveau", cache.lire("parent/enfant-1"))
    }

    @Test
    fun `liste vide servie comme dernière donnée connue`() {
        // Une liste vide est un état valide — pas un échec à cacher.
        val cache = MemoireSession<List<String>>()
        cache.écrire("parent/enfant-1", emptyList())
        assertEquals(emptyList<String>(), cache.lire("parent/enfant-1"))
    }
}
