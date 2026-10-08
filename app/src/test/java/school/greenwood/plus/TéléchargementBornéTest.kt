package school.greenwood.plus

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.util.Fichiers

/*
 * Copie bornée (issue #141) : la taille réellement lue prime sur tout
 * en-tête — un serveur qui ment sur Content-Length, ou qui n'en envoie
 * aucun, ne peut pas déborder le disque.
 */
class TéléchargementBornéTest {
    @Test fun copieExactementLaLimite() {
        val octets = ByteArray(16) { it.toByte() }
        val sortie = ByteArrayOutputStream()
        Fichiers.copierBorné(ByteArrayInputStream(octets), sortie, 16)
        assertArrayEquals(octets, sortie.toByteArray())
    }

    @Test fun rejetteLeDépassementSansÉcrireLeBlocEnTrop() {
        val sortie = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) {
            Fichiers.copierBorné(ByteArrayInputStream(ByteArray(17)), sortie, 16)
        }
        assertArrayEquals(ByteArray(0), sortie.toByteArray())
    }

    @Test fun rejetteUnDépassementProgressifEnConservantLePréfixeLegal() {
        // Corps plus grand que la limite, lu par petits blocs : le rejet
        // survient au bloc qui dépasse ; le préfixe déjà lu (strictement
        // dans la limite) avait transité par la sortie — le fichier n'est
        // publié que via le temporaire, que le caller supprime sur échec.
        val corps = object : java.io.InputStream() {
            var restant = 40
            override fun read(): Int = if (restant-- > 0) 1 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (restant <= 0) return -1
                val n = minOf(len, restant, 8)
                restant -= n
                java.util.Arrays.fill(b, off, off + n, 1)
                return n
            }
        }
        val sortie = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) {
            Fichiers.copierBorné(corps, sortie, 16)
        }
        assertArrayEquals(ByteArray(16) { 1 }, sortie.toByteArray())
    }

    @Test fun copieUnCorpsVideDansUneLimiteNulle() {
        val sortie = ByteArrayOutputStream()
        Fichiers.copierBorné(ByteArrayInputStream(ByteArray(0)), sortie, 0)
        assertArrayEquals(ByteArray(0), sortie.toByteArray())
    }

    @Test fun rejetteUnOctetAvecUneLimiteNulle() {
        assertThrows(IllegalStateException::class.java) {
            Fichiers.copierBorné(ByteArrayInputStream(ByteArray(1)), ByteArrayOutputStream(), 0)
        }
    }

    @Test fun refuseUneLimiteNégative() {
        assertThrows(IllegalArgumentException::class.java) {
            Fichiers.copierBorné(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), -1)
        }
    }

    @Test fun uneErreurDeFluxPropageSansÉcrire() {
        val corps = object : java.io.InputStream() {
            override fun read(): Int = throw IOException("réseau coupé")
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                throw IOException("réseau coupé")
        }
        val sortie = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            Fichiers.copierBorné(corps, sortie, 1024)
        }
        assertArrayEquals(ByteArray(0), sortie.toByteArray())
    }

    @Test fun lesApkOntUnPlafondPlusLargeQueLesDocuments() {
        assertTrue(Fichiers.LIMITE_APK > Fichiers.LIMITE_DOCUMENT)
        assertEquals(Fichiers.LIMITE_APK, Fichiers.limitePourNom("GWS-v1.apk"))
        assertEquals(Fichiers.LIMITE_APK, Fichiers.limitePourNom("GWS-V1.APK"))
        assertEquals(Fichiers.LIMITE_DOCUMENT, Fichiers.limitePourNom("devoir.pdf"))
        assertEquals(Fichiers.LIMITE_DOCUMENT, Fichiers.limitePourNom("photo.jpg"))
    }
}
