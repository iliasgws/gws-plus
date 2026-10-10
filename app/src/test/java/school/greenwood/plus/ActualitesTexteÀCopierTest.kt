package school.greenwood.plus

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import school.greenwood.plus.model.PostDetail
import school.greenwood.plus.util.postTexteÀCopier

/** Issue #148 : le texte copié par le bouton « Copier » d'une actualité. */
class ActualitesTexteÀCopierTest {

    @Test
    fun `actualité complète — toutes les sections présentes dans l'ordre`() {
        val post = PostDetail(
            id = "p1",
            title = "Sortie au musée",
            categorie = "Vie scolaire",
            date = LocalDateTime.of(2026, 9, 20, 12, 47),
            auteur = "Mme Dupont",
        )
        assertEquals(
            listOf(
                "Actualité : Sortie au musée",
                "Catégorie : Vie scolaire",
                "Date : le 20/09/2026 à 12:47",
                "Par : Mme Dupont",
            ),
            postTexteÀCopier(post).lines(),
        )
        // NB : le corps HTML est volontairement absent de ce test —
        // `Html.fromHtml` n'est pas mockable en test unitaire JVM ; l'aplatissement
        // (`htmlToPlainMultiline`) s'exerce sur l'appareil, comme le rendu de l'écran.
    }

    @Test
    fun `actualité minimale — seul le titre`() {
        val texte = postTexteÀCopier(PostDetail(id = "p2", title = "Réunion parents-professeurs"))
        assertEquals(listOf("Actualité : Réunion parents-professeurs"), texte.lines())
    }

    @Test
    fun `titre vide — libellé de repli, champs vides ignorés`() {
        val texte = postTexteÀCopier(
            PostDetail(id = "p3", title = "  ", categorie = "", auteur = "  "),
        )
        assertEquals(listOf("Actualité : Actualité"), texte.lines())
    }

    @Test
    fun `corps HTML vide — pas de paragraphe finale`() {
        val texte = postTexteÀCopier(
            PostDetail(id = "p4", title = "Info", descriptionHtml = "   "),
        )
        assertFalse(texte.contains("\n\n"))
        assertEquals(listOf("Actualité : Info"), texte.lines())
    }
}
