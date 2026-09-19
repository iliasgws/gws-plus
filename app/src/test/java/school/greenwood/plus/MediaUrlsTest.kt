package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.api.MediaUrls

/*
 * Les liens média sont double-encodés et passent par un Google Docs viewer.
 * Le décodage doit être unique : %2B / %2F / %3D restent littéraux, la
 * signature porte dessus (docs/api/BOTI-API.md).
 */
class MediaUrlsTest {

    private val viewer =
        "http://docs.google.com/viewer?url=https%3A%2F%2Fmedia.boti.education%2Fview" +
            "%2FZC9RQRZSo%2Bf0VpWF%2F7LdMJTKRM2OLncc6W%2Bndwo0mXc%3D.1788283943" +
            "%2Foriginal%2Fassets%2Fschools%2Fgreenwood%2Fdocs%2Fposts%2Fdevoir.pdf"

    @Test
    fun `lien viewer décodé exactement une fois`() {
        val décodé = MediaUrls.lienRéel(viewer)!!
        assertEquals(
            "https://media.boti.education/view/ZC9RQRZSo+f0VpWF/7LdMJTKRM2OLncc6W+ndwo0mXc" +
                "=.1788283943/original/assets/schools/greenwood/docs/posts/devoir.pdf",
            décodé,
        )
    }

    @Test
    fun `double décodage interdit`() {
        // Le décodé ne doit contenir aucun caractère déjà décodé deux fois :
        // les + du token restent des + (jamais remplacés par des espaces).
        val décodé = MediaUrls.lienRéel(viewer)!!
        assertTrue(décodé.contains("ZC9RQRZSo+f0VpWF"))
        assertTrue(!décodé.contains(" "))
    }

    @Test
    fun `lien direct rendu tel quel`() {
        val direct = "https://media.boti.education/view/ABC%2Bdef.123/original/a.pdf"
        assertEquals(direct, MediaUrls.lienRéel(direct))
    }

    @Test
    fun `null et vide`() {
        assertEquals(null, MediaUrls.lienRéel(null))
        assertEquals(null, MediaUrls.lienRéel(""))
    }

    @Test
    fun `plusieurs fichiers dans un lien - éclatés`() {
        val lien =
            "https://media.boti.education/view/ABC.1/original/a.pdf,https://media.boti.education/view/DEF.2/original/b.pdf"
        val pièces = MediaUrls.piècesJointes(lien, "fichier")
        assertEquals(2, pièces.size)
        assertEquals("a.pdf", pièces[0].first)
        assertEquals("b.pdf", pièces[1].first)
        assertTrue(pièces[0].second.startsWith("https://media.boti.education/view/ABC.1"))
    }

    @Test
    fun `nom de secours quand pas d'extension`() {
        val pièces = MediaUrls.piècesJointes("https://media.boti.education/view/ABC.1/original/asset", "mon fichier")
        assertEquals(1, pièces.size)
        assertEquals("mon fichier", pièces[0].first)
    }
}
