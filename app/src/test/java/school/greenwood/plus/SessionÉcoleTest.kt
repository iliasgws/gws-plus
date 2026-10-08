package school.greenwood.plus

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.session.purgerSessionÉcole
import school.greenwood.plus.data.session.écrireSessionÉcole

/*
 * Cycle de vie de la session école (issues #139/#140) : ce que la
 * déconnexion purge — et surtout ce qu'elle ne purge JAMAIS — vérifié sur
 * les vraies fonctions d'écriture, sans Context.
 */

private val keyToken = stringPreferencesKey("key_token")
private val userId = stringPreferencesKey("user_id")
private val eleveId = stringPreferencesKey("eleve_id")
private val eleveIndex = intPreferencesKey("eleve_index")
private val retenir = booleanPreferencesKey("retenir")

private val commJeton = stringPreferencesKey("communautaire_jeton")
private val commVotes = stringPreferencesKey("communautaire_votes_json")
private val iaClé = stringPreferencesKey("ia_cle")
private val banniereRegistre = booleanPreferencesKey("banniere_registre_activee")

/** Une préférence d'app typique + une session école + un compte communautaire. */
private fun préférencesComplètes(): MutablePreferences = mutablePreferencesOf(
    banniereRegistre to true,
    iaClé to "enc:v1:scellé",
    commJeton to "enc:v1:jeton-commun",
    commVotes to """{"1":1}""",
    keyToken to "enc:v1:jeton-école",
    userId to "user-a",
    eleveId to "eleve-a",
    eleveIndex to 2,
    retenir to true,
)

class SessionÉcoleTest {

    @Test
    fun `la purge de session - ne touche ni la communaute ni les prefs d app`() {
        val p = préférencesComplètes()

        p.purgerSessionÉcole()

        // Session école : tout parti.
        assertNull(p[keyToken])
        assertNull(p[userId])
        assertNull(p[eleveId])
        assertNull(p[eleveIndex])
        // Compte communautaire INDEPENDANT : survit (issue #139).
        assertEquals("enc:v1:jeton-commun", p[commJeton])
        assertEquals("""{"1":1}""", p[commVotes])
        // Préférences d'app : survit.
        assertEquals("enc:v1:scellé", p[iaClé])
        assertEquals(true, p[banniereRegistre])
    }

    @Test
    fun `l ecriture persistante - le jeton est scelle et l index remis a zero`() {
        val p = mutablePreferencesOf(eleveIndex to 3, retenir to false)

        p.écrireSessionÉcole(
            keyTokenScellé = "enc:v1:nouveau",
            userId = "user-b",
            parentId = "parent-b",
            eleveId = "eleve-b",
            role = null,
            parentNom = null,
            parentImage = null,
            elevesJson = "[]",
            retenir = true,
        )

        assertEquals("enc:v1:nouveau", p[keyToken])
        assertTrue(p[keyToken]!!.startsWith("enc:v1:"))
        assertEquals("user-b", p[userId])
        assertEquals(0, p[eleveIndex]) // pas de residue d'un compte precedent
        assertEquals(true, p[retenir])
    }

    @Test
    fun `passage de compte - ecrase la session precedente sans purger la communaute`() {
        // Compte scolaire A persisté.
        val p = préférencesComplètes()
        // Déconnexion de A : purge.
        p.purgerSessionÉcole()
        // Connexion de B avec retenir.
        p.écrireSessionÉcole(
            keyTokenScellé = "enc:v1:jeton-b",
            userId = "user-b",
            parentId = "",
            eleveId = "eleve-b",
            role = null,
            parentNom = null,
            parentImage = null,
            elevesJson = "[]",
            retenir = true,
        )

        assertEquals("user-b", p[userId])
        assertEquals("enc:v1:jeton-b", p[keyToken])
        // L'ancien jeton scolaire d'A ne traîne nulle part.
        assertFalse(p.toString().contains("jeton-école"))
        // La communauté d'appareil reste celle de l'appareil (issue #139).
        assertEquals("enc:v1:jeton-commun", p[commJeton])
    }
}
