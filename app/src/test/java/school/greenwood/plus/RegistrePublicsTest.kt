package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.util.IdentiteMedias
import school.greenwood.plus.util.RegistrePublics

/*
 * Registre des téléchargements publics (issue #108) : il relie l'identité
 * stable d'une ressource au nom affiché retenu dans « Downloads/gws-plus ».
 * La sélection du nom ne doit jamais réclamer ni écraser le fichier d'une
 * autre ressource, et le JSON doit aller-retour sans perte.
 */
class RegistrePublicsTest {

    // Deux ressources distinctes qui partagent le même libellé côté élève.
    private val identitéA =
        "https://media.boti.education/view/original/assets/schools/greenwood/docs/posts/devoir.pdf"
    private val identitéB =
        "https://media.boti.education/view/original/assets/schools/greenwood/docs/posts/absence.pdf"

    @Test
    fun `premier téléchargement garde le nom souhaité`() {
        assertEquals(
            "devoir.pdf",
            RegistrePublics.choisirNom(identitéA, "devoir.pdf", emptyMap(), nomOccupé = false),
        )
    }

    @Test
    fun `même identité réutilise son nom enregistré`() {
        val registre = mapOf(identitéA to "devoir-9f3a1c2d.pdf")
        // Même si un autre fichier porte le nom souhaité, le nom promis à
        // cette identité lui revient.
        assertEquals(
            "devoir-9f3a1c2d.pdf",
            RegistrePublics.choisirNom(identitéA, "devoir.pdf", registre, nomOccupé = true),
        )
    }

    @Test
    fun `nom déjà déclaré à une autre identité disambigué par empreinte`() {
        val registre = mapOf(identitéB to "devoir.pdf")
        val nom = RegistrePublics.choisirNom(identitéA, "devoir.pdf", registre, nomOccupé = false)
        assertEquals(IdentiteMedias.fichierÀEmpreinte("devoir.pdf", identitéA), nom)
        assertTrue(nom != "devoir.pdf")
    }

    @Test
    fun `fichier d'origine inconnue dans le dossier disambigué par empreinte`() {
        // Présent dans « Downloads/gws-plus » mais pas au registre : on ne
        // sait pas de qui il est, on ne le prend pas.
        val nom = RegistrePublics.choisirNom(identitéA, "devoir.pdf", emptyMap(), nomOccupé = true)
        assertEquals(IdentiteMedias.fichierÀEmpreinte("devoir.pdf", identitéA), nom)
        assertTrue(nom.startsWith("devoir-"))
        assertTrue(nom.endsWith(".pdf"))
    }

    @Test
    fun `deux ressources homonymes reçoivent deux noms distincts`() {
        val registre = mapOf(identitéB to "devoir.pdf")
        assertNotEquals(
            RegistrePublics.choisirNom(identitéA, "devoir.pdf", registre, nomOccupé = false),
            RegistrePublics.choisirNom(identitéB, "devoir.pdf", registre, nomOccupé = false),
        )
    }

    @Test
    fun `aller-retour sérialisation`() {
        val registre = mapOf(
            identitéA to "devoir.pdf",
            identitéB to "Cahier d'été-1a2b3c4d.pdf",
        )
        assertEquals(registre, RegistrePublics.lire(RegistrePublics.écrire(registre)))
    }

    @Test
    fun `registre vide`() {
        assertEquals(emptyMap<String, String>(), RegistrePublics.lire(null))
        assertEquals(emptyMap<String, String>(), RegistrePublics.lire(""))
        assertEquals(emptyMap<String, String>(), RegistrePublics.lire(RegistrePublics.écrire(emptyMap())))
    }

    @Test
    fun `JSON illisible rend un registre vide`() {
        assertEquals(emptyMap<String, String>(), RegistrePublics.lire("{pas du json"))
        assertEquals(emptyMap<String, String>(), RegistrePublics.lire("[1, 2]"))
    }
}
