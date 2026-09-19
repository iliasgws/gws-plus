package school.greenwood.plus

import school.greenwood.plus.model.Ressource
import school.greenwood.plus.ui.FiltreDocuments
import school.greenwood.plus.ui.estQuiz
import school.greenwood.plus.ui.filtrerRessources
import school.greenwood.plus.ui.screens.documents.duréeQuiz
import school.greenwood.plus.ui.screens.documents.horloge
import school.greenwood.plus.model.QuizDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Filtre par nature de l'espace documents et petits calculs du quiz
 * (issue #17) — fonctions pures.
 */
class DocumentsFiltreTest {

    private fun ressource(
        id: String,
        type: String?,
        label: String = "R",
        matiere: String = "Mathématiques",
    ) = Ressource(id = id, matiere = matiere, label = label, type = type)

    @Test
    fun `type quiz reconnu, casse ignorée - type absent reste un document`() {
        assertTrue(estQuiz(ressource("1", "quiz")))
        assertTrue(estQuiz(ressource("2", "Quiz")))
        assertFalse(estQuiz(ressource("3", "pdf")))
        assertFalse(estQuiz(ressource("4", null)))
    }

    @Test
    fun `filtre Quiz vs Documents vs Tout`() {
        val liste = listOf(
            ressource("1", "quiz", label = "Probabilités"),
            ressource("2", "pdf", label = "Polycopié"),
            ressource("3", null, label = "Support"),
        )
        assertEquals(
            listOf("1"),
            filtrerRessources(liste, "", FiltreDocuments.Quiz).map { it.id },
        )
        assertEquals(
            listOf("2", "3"),
            filtrerRessources(liste, "", FiltreDocuments.Documents).map { it.id },
        )
        assertEquals(
            listOf("1", "2", "3"),
            filtrerRessources(liste, "", FiltreDocuments.Tout).map { it.id },
        )
    }

    @Test
    fun `recherche et filtre se cumulent`() {
        val liste = listOf(
            ressource("1", "quiz", label = "Probabilités"),
            ressource("2", "pdf", label = "Probabilités — polycopié"),
            ressource("3", "quiz", label = "Dérivées"),
        )
        assertEquals(
            listOf("1"),
            filtrerRessources(liste, "probab", FiltreDocuments.Quiz).map { it.id },
        )
        assertEquals(
            listOf("2"),
            filtrerRessources(liste, "probab", FiltreDocuments.Documents).map { it.id },
        )
    }

    @Test
    fun `durée du quiz - minutes parsées, textuel sinon`() {
        assertEquals(
            "5 questions · 5 min",
            duréeQuiz(QuizDetail(id = "1", label = "Q", minutes = "05:00", questions = List(5) {
                school.greenwood.plus.model.QuizQuestion(texte = "q")
            })),
        )
        assertEquals(
            "2 questions",
            duréeQuiz(QuizDetail(id = "1", label = "Q", questions = List(2) {
                school.greenwood.plus.model.QuizQuestion(texte = "q")
            })),
        )
        assertEquals(
            "1 question · 12 min",
            duréeQuiz(
                QuizDetail(
                    id = "1",
                    label = "Q",
                    minutes = "12:30",
                    questions = listOf(school.greenwood.plus.model.QuizQuestion(texte = "q")),
                ),
            ),
        )
    }

    @Test
    fun `horloge mm et ss, jamais négative`() {
        assertEquals("00:05", horloge(5))
        assertEquals("01:00", horloge(60))
        assertEquals("00:00", horloge(-3))
    }
}
