package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.util.imagesHTML
import school.greenwood.plus.util.médiasÀPartager
import school.greenwood.plus.util.résoudreURL

/**
 * Issue #150 (v3) : les images d'une actualité ne sont pas que les pièces
 * jointes explicites — fixtures (a) à (e) de l'issue.
 */
class MediasPartagésTest {

    private val signée = "https://media.boti.education/view/jetonA.1728560000/800/photo.jpg"
    /** Même ressource, autre jeton signé — même identité, une seule copie. */
    private val signéeAutreJeton = "https://media.boti.education/view/jetonB.1728570000/800/photo.jpg"

    @Test
    fun `(a) image HTML seule — aucune pièce jointe du serveur — elle est découverte`() {
        val html = """<p>Voir</p><img src="$signée" alt="photo">"""
        val médias = médiasÀPartager(
            pièces = emptyList(),
            html = html,
        )
        assertEquals(listOf("photo.jpg"), médias.map { it.name })
        assertEquals(listOf(signée), médias.map { it.url })
    }

    @Test
    fun `(b) image dans la liste des pièces jointes seulement — conservée, URL intacte`() {
        val médias = médiasÀPartager(
            pièces = listOf(Attachment(name = "plan.pdf", url = "https://media.boti.education/view/x.1/10/plan.pdf")),
        )
        assertEquals(listOf("plan.pdf"), médias.map { it.name })
    }

    @Test
    fun `(c) même image en ligne ET jointe — une seule copie part`() {
        val médias = médiasÀPartager(
            pièces = listOf(Attachment(name = "photo.jpg", url = signée)),
            html = """<img src="$signéeAutreJeton">""",
        )
        assertEquals(1, médias.size)
        assertEquals("photo.jpg", médias.first().name)
    }

    @Test
    fun `(d) plusieurs images en ligne plus un PDF — tout est joint, dans l'ordre`() {
        val png = "https://media.boti.education/view/t.2/300/dessin.png"
        val gif = "https://media.boti.education/view/t.3/300/animation.gif"
        val médias = médiasÀPartager(
            pièces = listOf(Attachment(name = "consignes.pdf", url = "https://media.boti.education/view/p.1/900/consignes.pdf")),
            html = """<img src="$png"><img src="$gif">""",
        )
        assertEquals(listOf("consignes.pdf", "dessin.png", "animation.gif"), médias.map { it.name })
    }

    @Test
    fun `(e) URL distante authentifiée (jeton) — telle quelle, jamais réécrite`() {
        assertEquals(signée, résoudreURL(signée))
        // URL de protocole relatif : même hôte, HTTPS imposé.
        assertEquals(
            "https://media.boti.education/view/t.9/10/a.jpg",
            résoudreURL("//media.boti.education/view/t.9/10/a.jpg"),
        )
    }

    @Test
    fun `galerie et couverture entrent dans l'union — dédupliquées par identité`() {
        val médias = médiasÀPartager(
            pièces = listOf(Attachment(name = "photo.jpg", url = signée)),
            imageCouverture = signéeAutreJeton,
            galerie = listOf("https://media.boti.education/view/g.1/200/galerie.jpg"),
        )
        assertEquals(listOf("photo.jpg", "galerie.jpg"), médias.map { it.name })
    }

    @Test
    fun `URL relatives résolues — jamais une URL nu au EXTRA_STREAM`() {
        assertEquals("https://boti.education/a.jpg", résoudreURL("/a.jpg"))
        assertTrue(résoudreURL("relative/photo.jpg")!!.startsWith("https://boti.education/"))
        // data URI conservée (décodée plus tard par le dépôt).
        assertTrue(résoudreURL("data:image/png;base64,iVBORw0KGgo=")!!.startsWith("data:image/"))
        assertNull(résoudreURL("javascript:void(0)"))
        assertNull(résoudreURL(""))
        assertNull(résoudreURL(null))
    }

    @Test
    fun `imagesHTML — dédoublonnée, vide pour un corps sans image`() {
        val html = """<img src="$signée"><img src="$signéeAutreJeton"><img src="a.jpg">"""
        assertEquals(2, imagesHTML(html).size)
        assertEquals(emptyList<String>(), imagesHTML("<p>texte seul</p>"))
        assertEquals(emptyList<String>(), imagesHTML(null))
    }
}
