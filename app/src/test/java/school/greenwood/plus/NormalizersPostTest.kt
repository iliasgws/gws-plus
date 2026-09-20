package school.greenwood.plus

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.repo.Normalizers
import school.greenwood.plus.model.Post
import java.time.LocalDateTime

class NormalizersPostTest {

    @Test
    fun `post extrait bookmark et auteur objet`() {
        val raw = buildJsonObject {
            put("id", "101")
            put("title", "Sortie scolaire")
            put("bookmark", "bookmark")
            put("permit_comments", "1")
            put("permit_new_comments", "1")
            put("permit_quiz", "0")
            putJsonObject("user") {
                put("nom", "Mme Martin")
            }
        }

        val p = Normalizers.post(raw)
        assertNotNull(p)
        assertEquals("101", p!!.id)
        assertEquals("Sortie scolaire", p.title)
        assertTrue(p.bookmark)
        assertEquals("Mme Martin", p.auteur)
        assertTrue(p.permitComments)
        assertTrue(p.permitNewComments)
        assertFalse(p.permitQuiz)
    }

    @Test
    fun `post extrait auteur chaine simple et gere bookmark absent`() {
        val raw = buildJsonObject {
            put("id", "102")
            put("title", "Rentrée")
            put("user", "Direction Greenwood")
        }

        val p = Normalizers.post(raw)
        assertNotNull(p)
        assertEquals("102", p!!.id)
        assertFalse(p.bookmark)
        assertEquals("Direction Greenwood", p.auteur)
        assertFalse(p.permitComments)
        assertFalse(p.permitNewComments)
        assertFalse(p.permitQuiz)
    }

    @Test
    fun `post extrait toutes les pieces jointes separees par virgule sans tronquer`() {
        val raw = buildJsonObject {
            put("id", "103")
            put("title", "Circulaire")
            // Simulate comma-separated files from Boti
            put("file", "https://ecole.ma/f1.pdf, https://ecole.ma/f2.pdf")
        }

        val p = Normalizers.post(raw)
        assertNotNull(p)
        assertEquals(2, p!!.attachments.size)
        assertEquals("f1.pdf", p.attachments[0].name)
        assertEquals("f2.pdf", p.attachments[1].name)
    }

    @Test
    fun `postDetail parse completement post_view`() {
        val raw = buildJsonObject {
            putJsonObject("post") {
                put("id", "200")
                put("title", "Voyage de fin d'année")
                put("bookmark", "bookmark")
                put("date", "2026-06-15 10:00:00")
                put("cat_name", "Sorties")
                put("user_nom", "M. Dupont")
                put("image", "https://ecole.ma/cover.jpg")
                put("permit_comments", "1")
                put("permit_new_comments", "1")
                put("permit_quiz", "1")
                put("can_send_comment", "1")
                put("desc", "<p>Programme détaillé du voyage.</p>")
            }
            putJsonArray("images") {
                add(buildJsonObject { put("image", "https://ecole.ma/img1.jpg") })
                add(buildJsonObject { put("image", "https://ecole.ma/img2.jpg") })
            }
            putJsonArray("comments") {
                add(buildJsonObject {
                    put("id", "c1")
                    put("auteur", "Parent A")
                    put("texte", "Très belle initiative !")
                    put("date", "2026-06-15 11:30:00")
                    putJsonArray("sousComment") {
                        add(buildJsonObject {
                            put("id", "c1_1")
                            put("auteur", "M. Dupont")
                            put("texte", "Merci !")
                        })
                    }
                })
            }
            putJsonArray("quiz") {
                add(buildJsonObject {
                    put("alias", "q1")
                    put("label", "Votre enfant participe-t-il ?")
                    putJsonArray("reponses") {
                        add(kotlinx.serialization.json.JsonPrimitive("Oui"))
                        add(kotlinx.serialization.json.JsonPrimitive("Non"))
                    }
                    put("res", "Oui")
                })
            }
        }

        val d = Normalizers.postDetail(raw)
        assertNotNull(d)
        assertEquals("200", d!!.id)
        assertEquals("Voyage de fin d'année", d.title)
        assertTrue(d.bookmark)
        assertEquals("Sorties", d.categorie)
        assertEquals("M. Dupont", d.auteur)
        assertEquals("https://ecole.ma/cover.jpg", d.image)
        assertTrue(d.peutCommenter)
        assertTrue(d.peutNouveauCommentaire)
        assertTrue(d.peutRépondre)
        assertTrue(d.peutQuiz)
        assertEquals("<p>Programme détaillé du voyage.</p>", d.descriptionHtml)

        // Images gallery
        assertEquals(2, d.images.size)
        assertEquals("https://ecole.ma/img1.jpg", d.images[0])
        assertEquals("https://ecole.ma/img2.jpg", d.images[1])

        // Comments
        assertEquals(1, d.commentaires.size)
        val com = d.commentaires[0]
        assertEquals("Parent A", com.auteur)
        assertEquals("Très belle initiative !", com.texte)
        assertEquals(1, com.sousCommentaires.size)
        assertEquals("M. Dupont", com.sousCommentaires[0].auteur)

        // Quiz
        assertEquals(1, d.questions.size)
        val q = d.questions[0]
        assertEquals("q1", q.alias)
        assertEquals("Votre enfant participe-t-il ?", q.label)
        assertEquals(listOf("Oui", "Non"), q.réponses)
        assertEquals("Oui", q.réponseChoisie)
    }

    @Test
    fun `postDetail tolerant aux donnees manquantes`() {
        val raw = buildJsonObject {
            putJsonObject("post") {
                put("id", "201")
            }
        }

        val d = Normalizers.postDetail(raw)
        assertNotNull(d)
        assertEquals("201", d!!.id)
        assertEquals("", d.title)
        assertNull(d.image)
        assertTrue(d.images.isEmpty())
        assertTrue(d.commentaires.isEmpty())
        assertTrue(d.questions.isEmpty())
        assertFalse(d.peutCommenter)
    }

    @Test
    fun `fusionner remplace tout si depart est 0`() {
        val p1 = Post(id = "1", title = "Titre 1")
        val p2 = Post(id = "2", title = "Titre 2")
        val p3 = Post(id = "3", title = "Titre 3")

        val anciens = listOf(p1, p2)
        val nouveaux = listOf(p3)

        val resultat = Normalizers.fusionner(anciens, nouveaux, départ = 0)
        assertEquals(listOf(p3), resultat)
    }

    @Test
    fun `fusionner ajoute sans doublons si depart superieur a 0`() {
        val p1 = Post(id = "1", title = "Titre 1")
        val p2 = Post(id = "2", title = "Titre 2")
        val p3 = Post(id = "3", title = "Titre 3")

        val anciens = listOf(p1, p2)
        val nouveaux = listOf(p2, p3) // p2 est un doublon

        val resultat = Normalizers.fusionner(anciens, nouveaux, départ = 2)
        assertEquals(listOf(p1, p2, p3), resultat)
    }

    @Test
    fun `derniere selectionne le post le plus recent par date`() {
        val p1 = Post(id = "1", title = "Ancien", date = LocalDateTime.of(2026, 9, 1, 10, 0))
        val p2 = Post(id = "2", title = "Récent", date = LocalDateTime.of(2026, 9, 10, 10, 0))
        val p3 = Post(id = "3", title = "Sans date", date = null)

        val derniere = Normalizers.dernière(listOf(p1, p2, p3))
        assertEquals("2", derniere?.id)
        assertEquals("Récent", derniere?.title)
    }

    @Test
    fun `derniere retourne null sur liste vide`() {
        assertNull(Normalizers.dernière(emptyList()))
    }
}
