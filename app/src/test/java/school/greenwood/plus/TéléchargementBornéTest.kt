package school.greenwood.plus

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import school.greenwood.plus.util.Fichiers

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
}
