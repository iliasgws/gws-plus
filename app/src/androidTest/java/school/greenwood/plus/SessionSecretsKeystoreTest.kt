package school.greenwood.plus

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import school.greenwood.plus.data.session.SessionSecrets

/*
 * Chiffrement Keystore réel (issue #138) : ces tests exigent un runtime
 * Android — le Keystore n'existe pas sur JVM. Ils valident l'aller-retour
 * AES-GCM, l'unicité des IV, la tolérance aux payloads corrompus et la
 * reprise après invalidation de la clé.
 */
@RunWith(AndroidJUnit4::class)
class SessionSecretsKeystoreTest {

    private val secrets = SessionSecrets()

    @Test
    fun allerRetourKeystore() {
        val clair = "jeton-de-test-éàç-0123456789"
        val scellé = secrets.seal(clair)
        assertTrue(secrets.estChiffré(scellé))
        assertNotEquals(clair, scellé)
        assertFalse(scellé.contains(clair))
        assertEquals(clair, secrets.open(scellé))
    }

    @Test
    fun chaqueChiffrementPorteUnIvUnique() {
        val a = secrets.seal("identique")
        val b = secrets.seal("identique")
        assertNotEquals("IV réutilisé entre deux chiffrements", a, b)
        assertEquals("identique", secrets.open(a))
        assertEquals("identique", secrets.open(b))
    }

    @Test
    fun payloadCorrompuDevientVide() {
        val scellé = secrets.seal("secret")
        // Bit retourné dans le corps chiffré : le tag GCM échoue → "".
        val corrompu = scellé.dropLast(4) + "AAAA"
        assertEquals("", secrets.open(corrompu))
    }

    @Test
    fun legacyEnClairPasseeTelQuelle() {
        val legacy = "abcdef0123456789abcdef0123456789abcdef01"
        assertFalse(secrets.estChiffré(legacy))
        assertEquals(legacy, secrets.open(legacy))
    }

    @Test
    fun cléSuppriméeExigeUneNouvelleConnexionPuisReprend() {
        val scellé = secrets.seal("avant-suppression")
        assertEquals("avant-suppression", secrets.open(scellé))

        // Simule une clé Keystore invalidée (restauration d'appareil,
        // rotation matérielle…) : l'entrée est supprimée du magasin.
        val magasin = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        magasin.deleteEntry("gws_plus_session_v1")

        // Le secret scellé devient illisible : "" → reconnexion exigée,
        // sans exception ni secret logué.
        assertEquals("", secrets.open(scellé))

        // Un nouveau scellage régénère la clé et repart proprement.
        val neuf = secrets.seal("après-suppression")
        assertEquals("après-suppression", secrets.open(neuf))
    }
}
