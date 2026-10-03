package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.util.IdentiteMedias

/*
 * Les médias signés changent de jeton à chaque réponse du serveur : la clé de
 * cache doit reconnecter les URLs d'une même ressource (issue #108) sans jamais
 * dépendre du jeton, ni dévier sur une autre ressource.
 */
class IdentiteMediasTest {

    // Forme réelle (décodée une fois) : le jeton contient + / = et traverse
    // plusieurs segments — MediaUrlsTest en montre un exemplaire.
    private val signée1 =
        "https://media.boti.education/view/ZC9RQRZSo+f0VpWF/7LdMJTKRM2OLncc6W+ndwo0mXc=" +
            ".1788283943/original/assets/schools/greenwood/docs/posts/devoir.pdf"
    private val signée2 =
        "https://media.boti.education/view/AutreJetonDeSignature+ailleurs/ici=.1788284001" +
            "/original/assets/schools/greenwood/docs/posts/devoir.pdf"

    @Test
    fun `même ressource sous deux signatures → même clé`() {
        assertEquals(IdentiteMedias.clé(signée1), IdentiteMedias.clé(signée2))
    }

    @Test
    fun `la clé retire le jeton mais garde hôte taille et chemin`() {
        assertEquals(
            "https://media.boti.education/view/original/assets/schools/greenwood/docs/posts/devoir.pdf",
            IdentiteMedias.clé(signée1),
        )
    }

    @Test
    fun `la clé ne contient aucune trace du jeton`() {
        val clé = IdentiteMedias.clé(signée1)
        assertTrue(!clé.contains("ZC9RQRZSo"))
        assertTrue(!clé.contains("1788283943"))
    }

    @Test
    fun `deux ressources différentes → deux clés différentes`() {
        val autre = signée1.replace("devoir.pdf", "absence.pdf")
        assertNotEquals(IdentiteMedias.clé(signée1), IdentiteMedias.clé(autre))
    }

    @Test
    fun `taille serveur différente → clé différente (représentations distinctes)`() {
        val petite = signée1.replace("/original/", "/small/")
        assertNotEquals(IdentiteMedias.clé(signée1), IdentiteMedias.clé(petite))
    }

    @Test
    fun `URL non signée rendue intacte`() {
        val plaine = "https://boti.education/assets/img/avatar.png"
        assertEquals(plaine, IdentiteMedias.clé(plaine))
    }

    @Test
    fun `médias de forme inattendue conservés tels quels`() {
        // Pas de timestamp numérique après le jeton : on ne devine pas, on garde.
        val étrange = "https://media.boti.education/view/sans-timestamp/original/f.pdf"
        assertEquals(étrange, IdentiteMedias.clé(étrange))
    }

    @Test
    fun `empreinte stable et distincte par identité`() {
        val autre = signée1.replace("devoir", "absence")
        assertEquals(
            IdentiteMedias.empreinte(IdentiteMedias.clé(signée1)),
            IdentiteMedias.empreinte(IdentiteMedias.clé(signée2)),
        )
        assertNotEquals(
            IdentiteMedias.empreinte(IdentiteMedias.clé(signée1)),
            IdentiteMedias.empreinte(IdentiteMedias.clé(autre)),
        )
        assertEquals(8, IdentiteMedias.empreinte(IdentiteMedias.clé(signée1)).length)
    }

    @Test
    fun `fichierÀEmpreinte garde l'extension et le nom lisible`() {
        val nom = IdentiteMedias.fichierÀEmpreinte("Cours de maths.pdf", IdentiteMedias.clé(signée1))
        assertTrue(nom.startsWith("Cours de maths-"))
        assertTrue(nom.endsWith(".pdf"))
        assertEquals(
            nom,
            IdentiteMedias.fichierÀEmpreinte("Cours de maths.pdf", IdentiteMedias.clé(signée2)),
        )
    }
}
