package school.greenwood.plus

import java.util.Arrays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.session.FormatSecrets
import school.greenwood.plus.data.session.SessionSecrets
import school.greenwood.plus.data.session.valeurÀMigrer

/*
 * Format et décisions de migration des credentials scellés (issue #138) —
 * pur JVM : le chiffreur Keystore lui-même est couvert par les tests
 * instrumentés (androidTest/…/SessionSecretsKeystoreTest.kt).
 */

/** Chiffreur factice : IV incrémental, « chiffrement » XOR — même format. */
private class ChiffreurFactice : school.greenwood.plus.data.session.Chiffreur {
    var ivDonnées = 0
    var déchiffrements = 0
        private set

    override fun chiffrer(clair: ByteArray): ByteArray {
        ivDonnées++
        val iv = byteArrayOf(
            (ivDonnées shr 24).toByte(),
            (ivDonnées shr 16).toByte(),
            (ivDonnées shr 8).toByte(),
            ivDonnées.toByte(),
        ) + ByteArray(8)
        val chiffré = clair.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        return iv + chiffré
    }

    override fun déchiffrer(payload: ByteArray): ByteArray {
        déchiffrements++
        require(payload.size > 12) { "payload trop court" }
        return payload.copyOfRange(12, payload.size).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
}

class SecretsSessionTest {

    @Test
    fun `aller-retour - le clair revient identique`() {
        val secrets = SessionSecrets(ChiffreurFactice())
        val clair = "jeton-sécurisé-éàç"
        val scellé = secrets.seal(clair)
        assertTrue(secrets.estChiffré(scellé))
        assertNotEquals(clair, scellé)
        assertEquals(clair, secrets.open(scellé))
    }

    @Test
    fun `chaque scellage - porte un IV distinct`() {
        val secrets = SessionSecrets(ChiffreurFactice())
        val a = FormatSecrets.déballer(secrets.seal("secret"))!!.first
        val b = FormatSecrets.déballer(secrets.seal("secret"))!!.first
        assertFalse(Arrays.equals(a, b))
    }

    @Test
    fun `les valeurs en clair des anciennes releases - passent telles quelles`() {
        val secrets = SessionSecrets(ChiffreurFactice())
        val legacy = "0123456789abcdef0123456789abcdef01234567"
        assertFalse(secrets.estChiffré(legacy))
        assertEquals(legacy, secrets.open(legacy))
    }

    @Test
    fun `un payload corrompu - devient vide sans lever`() {
        val secrets = SessionSecrets(ChiffreurFactice())
        // Base64 valide mais IV absent/tronqué.
        assertEquals("", secrets.open("enc:v1:AAAA"))
        // Base64 invalide.
        assertEquals("", secrets.open("enc:v1:%%%pas-base64%%%"))
    }

    @Test
    fun `un chiffrage refuse - devient vide sans lever`() {
        val refuse = object : school.greenwood.plus.data.session.Chiffreur {
            override fun chiffrer(clair: ByteArray) = ByteArray(12) + clair
            override fun déchiffrer(payload: ByteArray): ByteArray =
                throw java.security.GeneralSecurityException("clé Keystore invalidée")
        }
        val secrets = SessionSecrets(refuse)
        // Payload base64 valide, IV 12 octets + corps — mais la clé refuse.
        assertEquals("", secrets.open("enc:v1:AAAAAAAAAAAAAAAAAAAAAAAA"))
    }

    @Test
    fun `une valeur vide - reste vide dans les deux sens`() {
        val secrets = SessionSecrets(ChiffreurFactice())
        assertEquals("", secrets.seal(""))
        assertEquals("", secrets.open(""))
    }

    @Test
    fun `deux secrets differents - deux chiffrés differents`() {
        val secrets = SessionSecrets(ChiffreurFactice())
        assertNotEquals(secrets.seal("alice"), secrets.seal("bob"))
    }

    // — Migration (valeurÀMigrer) ------------------------------------------

    @Test
    fun `migration - un clair non vide est scelle`() {
        val scellé = valeurÀMigrer("plaintext") { "enc:v1:$it" }
        assertEquals("enc:v1:plaintext", scellé)
    }

    @Test
    fun `migration - rien a migrer pour un deja scelle ou un vide`() {
        assertNull(valeurÀMigrer(null) { "enc:v1:$it" })
        assertNull(valeurÀMigrer("") { "enc:v1:$it" })
        assertNull(valeurÀMigrer("enc:v1:déjà") { "NE DEVRAIT PAS TOURNER" })
    }
}
