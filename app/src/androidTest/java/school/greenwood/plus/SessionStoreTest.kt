package school.greenwood.plus

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import school.greenwood.plus.data.session.SessionStore

/*
 * SessionStore sur DataStore réel (issues #138/#139/#140) :
 *  - « Rester connecté » coché → jeton scellé sur disque, jamais en clair ;
 *  - décoché → RIEN de la session n'atteint le disque (jeton mémoire) ;
 *  - déconnexion A → connexion B → le compte communautaire de l'appareil
 *    survit, la session scolaire est entièrement celle de B ;
 *  - purge complète (oubli communautaire).
 */
@RunWith(AndroidJUnit4::class)
class SessionStoreTest {

    private val contexte: Context = ApplicationProvider.getApplicationContext()
    private val magasin = SessionStore(contexte)

    /** Le DataStore brut, pour vérifier ce qui touche réellement le disque. */
    private fun fichierDataStore(): File =
        File(contexte.filesDir, "datastore/gws_session.preferences_pb")

    private fun octetsDisque(): ByteArray =
        fichierDataStore().takeIf { it.exists() }?.readBytes() ?: ByteArray(0)

    @Before
    fun étatNeuf() = runBlocking {
        magasin.effacer()
        magasin.oublierTout()
        // Laisse l'édition se matérialiser sur disque avant les assertions.
        magasin.state.first()
    }

    @Test
    fun sessionRetenueJetonScelléSurDisque() = runBlocking {
        magasin.enregistrer(
            keyToken = "jeton-clair-de-test",
            userId = "user-a",
            parentId = "parent-a",
            eleveId = "eleve-a",
            role = "parent",
            parent = null,
            eleves = emptyList(),
            retenir = true,
        )

        val état = magasin.state.first()
        assertNotNull(état)
        assertTrue(état!!.isAuthentifie)
        assertEquals("jeton-clair-de-test", état.keyToken)

        val disque = octetsDisque().toString(Charsets.ISO_8859_1)
        assertTrue("le jeton scellé (enc:v1:) doit être sur disque", disque.contains("enc:v1:"))
        assertFalse("le jeton en clair ne doit JAMAIS toucher le disque", disque.contains("jeton-clair-de-test"))
    }

    @Test
    fun sessionNonRetenueJamaisÉcriteSurDisque() = runBlocking {
        magasin.enregistrer(
            keyToken = "jeton-éphémère-de-test",
            userId = "user-éphémère",
            parentId = "",
            eleveId = "eleve-x",
            role = null,
            parent = null,
            eleves = emptyList(),
            retenir = false,
        )

        // La session fonctionne en mémoire.
        val état = magasin.state.first()
        assertNotNull(état)
        assertTrue(état!!.isAuthentifie)
        assertEquals("jeton-éphémère-de-test", état.keyToken)

        // … mais rien n'a été écrit sur disque pour une reconnexion auto.
        val disque = octetsDisque().toString(Charsets.ISO_8859_1)
        assertFalse(disque.contains("jeton-éphémère-de-test"))
        assertFalse(disque.contains("user-éphémère"))
        assertFalse(disque.contains("enc:v1:"))
    }

    @Test
    fun déconnexionNettoieSessionÉphémèreEtPersistée() = runBlocking {
        magasin.enregistrer(
            keyToken = "jeton",
            userId = "user",
            parentId = "",
            eleveId = "e",
            role = null,
            parent = null,
            eleves = emptyList(),
            retenir = false,
        )
        assertNotNull(magasin.state.first())

        magasin.effacer()

        assertNull(magasin.state.first())
        val disque = octetsDisque().toString(Charsets.ISO_8859_1)
        assertFalse(disque.contains("user"))
    }

    @Test
    fun compteApresLogoutLeCompteCommunautaireSurvit() = runBlocking {
        // École A + compte communautaire de l'appareil + vote local.
        magasin.enregistrer(
            keyToken = "jeton-a",
            userId = "user-a",
            parentId = "",
            eleveId = "eleve-a",
            role = null,
            parent = null,
            eleves = emptyList(),
            retenir = true,
        )
        magasin.enregistrerJeton("jeton-communauté", mentionsVersion = "v1")
        magasin.noterVoteLocal(42, 1)

        // Déconnexion de l'école.
        magasin.effacer()
        assertNull(magasin.state.first())

        // Le compte communautaire de l'appareil est intact (issue #139).
        assertEquals("jeton-communauté", magasin.jeton())
        assertEquals(mapOf("42" to 1), magasin.votesLocaux.first())

        // Compte scolaire B sur le même appareil.
        magasin.enregistrer(
            keyToken = "jeton-b",
            userId = "user-b",
            parentId = "",
            eleveId = "eleve-b",
            role = null,
            parent = null,
            eleves = emptyList(),
            retenir = true,
        )
        val étatB = magasin.state.first()!!
        // La session est entièrement celle de B — aucun champ résiduel de A.
        assertEquals("user-b", étatB.userId)
        assertEquals("jeton-b", étatB.keyToken)
        assertEquals("eleve-b", étatB.eleveId)
        // La communauté reste celle de l'appareil, pas celle de B.
        assertEquals("jeton-communauté", magasin.jeton())
    }

    @Test
    fun oubliCommunautaireSupprimeJetonEtVotes() = runBlocking {
        magasin.enregistrerJeton("jeton", mentionsVersion = "v1")
        magasin.noterVoteLocal(1, -1)

        magasin.oublierTout()

        assertNull(magasin.jeton())
        assertTrue(magasin.votesLocaux.first().isEmpty())
    }

    @Test
    fun passageDeSessionRetenueÀÉphémèrePurgeLeDisque() = runBlocking {
        // D'abord une session retenue (jeton sur disque).
        magasin.enregistrer(
            keyToken = "ancien-jeton-retenu",
            userId = "ancien-user",
            parentId = "",
            eleveId = "e",
            role = null,
            parent = null,
            eleves = emptyList(),
            retenir = true,
        )
        assertTrue(octetsDisque().toString(Charsets.ISO_8859_1).contains("enc:v1:"))

        // Puis une connexion éphémère : l'ancien jeton scellé doit partir.
        magasin.enregistrer(
            keyToken = "nouveau-jeton-mémoire",
            userId = "nouvel-user",
            parentId = "",
            eleveId = "e",
            role = null,
            parent = null,
            eleves = emptyList(),
            retenir = false,
        )
        val disque = octetsDisque().toString(Charsets.ISO_8859_1)
        assertFalse("l'ancien jeton scellé doit être purgé du disque", disque.contains("enc:v1:"))
        assertEquals("nouveau-jeton-mémoire", magasin.state.first()!!.keyToken)
    }

    @Test
    fun préférenceDeRetenueMémorisée() = runBlocking {
        magasin.enregistrer(
            keyToken = "j",
            userId = "u",
            parentId = "",
            eleveId = "e",
            role = null,
            parent = null,
            eleves = emptyList(),
            retenir = false,
        )
        assertFalse(magasin.retenir.first())
        magasin.effacer()
        // La préférence de connexion (pas un secret) survit à la déconnexion
        // pour pré-cocher le prochain écran de connexion.
        assertFalse(magasin.retenir.first())
    }
}
