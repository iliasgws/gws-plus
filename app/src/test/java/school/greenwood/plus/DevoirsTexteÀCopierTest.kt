package school.greenwood.plus

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.util.devoirTexteÀCopier

/** Issue #85 : le texte copié par appui long sur une carte devoir. */
class DevoirsTexteÀCopierTest {

    private val aujourdhui: LocalDate = LocalDate.of(2026, 9, 26)

    @Test
    fun `devoir complet — toutes les sections présentes dans l'ordre`() {
        val devoir = Devoir(
            id = "d1",
            title = "Exercices sur les fractions",
            matiere = "Mathématiques",
            categorie = "Devoir maison",
            enseignant = "Mme Dupont",
            dateRemise = aujourdhui,
            attachments = listOf(
                Attachment("énoncé.pdf", "https://example.org/a.pdf"),
                Attachment("corrigé.odt", "https://example.org/b.odt"),
            ),
        )
        val texte = devoirTexteÀCopier(devoir, aujourdhui)
        assertEquals(
            listOf(
                "Devoir : Exercices sur les fractions",
                "Matière : Mathématiques",
                "Catégorie : Devoir maison",
                "Enseignant(e) : Mme Dupont",
                "État : À faire",
                "Pièces jointes :",
                "- énoncé.pdf",
                "- corrigé.odt",
            ),
            texte.lines(),
        )
        // NB : le corps HTML est volontairement absent de ce test —
        // `Html.fromHtml` n'est pas mockable en test unitaire JVM ; l'aplatissement
        // (`htmlToPlainMultiline`) s'exerce sur l'appareil, comme l'aperçu de la carte.
    }

    @Test
    fun `fait au niveau du serveur — état « connu de l'école »`() {
        val texte = devoirTexteÀCopier(
            Devoir(id = "d2", title = "T", matiere = "M", fait = true),
            aujourdhui,
        )
        assertTrue(texte.contains("État : Travail fait (connu de l'école)"))
        assertFalse(texte.contains("Pièces jointes"))
    }

    @Test
    fun `fait pour moi seulement — état local explicite`() {
        val texte = devoirTexteÀCopier(
            Devoir(id = "d3", title = "T", matiere = "M", faitLocal = true),
            aujourdhui,
        )
        assertTrue(texte.contains("État : Fait pour moi (local, invisible à l'école)"))
    }

    @Test
    fun `devoir minimal — pas de lignes vides inutiles`() {
        val texte = devoirTexteÀCopier(
            Devoir(id = "d4", title = "Lecture", matiere = "Français"),
            aujourdhui,
        )
        assertEquals(listOf("Devoir : Lecture", "Matière : Français", "État : À faire"), texte.lines())
    }
}