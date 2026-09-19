package school.greenwood.plus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import school.greenwood.plus.data.repo.Normalizers
import school.greenwood.plus.data.repo.questionsPourEnvoi
import school.greenwood.plus.model.RéponseJouée
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Formes GET/POST `quiz` (issue #17) — GET vérifié en sonde lecture-seule le
 * 19/09/2026, POST lu dans le bundle (chunk 1140.js). Jeux d'essai
 * synthétiques, sans contenu réel de l'école.
 */
class QuizNormalizerTest {

    @Test
    fun `détail complet - questions et drapeaux corrects`() {
        val rep = Json.parseToJsonElement(
            """
            {
              "data": {
                "quiz_id": "42",
                "label": "Quiz de démonstration",
                "matiere": "Exemple",
                "niveau": "3e",
                "color": "#33a6e1",
                "image": "https://exemple.test/visuel.png",
                "minutes": "05:00",
                "can_play": true,
                "can_replay": false,
                "questions": [
                  {
                    "question": "2 + 2 ?",
                    "alias": "",
                    "answer": {"answer": "", "correct": "4", "answered": null},
                    "image": null,
                    "temps_reponse": 60,
                    "reponses": [
                      {"reponse": "3", "correct": false},
                      {"reponse": "4", "correct": true}
                    ]
                  }
                ]
              },
              "empty": false,
              "no_play": "Télécharger la pièce jointe",
              "translation": {"title": "Ressources", "quiz": "Quiz"}
            }
            """.trimIndent(),
        ).jsonObject

        val quiz = Normalizers.quiz(rep)!!
        assertEquals("42", quiz.id)
        assertEquals("Quiz de démonstration", quiz.label)
        assertEquals("Exemple", quiz.matiere)
        assertEquals("#33a6e1", quiz.couleur)
        assertEquals("https://exemple.test/visuel.png", quiz.image)
        assertEquals("05:00", quiz.minutes)
        assertTrue(quiz.peutJouer)
        assertFalse(quiz.peutRejouer)
        assertEquals(1, quiz.nbQuestions)
        val q = quiz.questions.first()
        assertEquals("2 + 2 ?", q.texte)
        assertNull(q.image)
        assertEquals(60, q.tempsReponse)
        assertEquals(2, q.reponses.size)
        assertTrue(q.reponses[1].correcte)
        assertFalse(q.reponses[0].correcte)
    }

    @Test
    fun `can_play absent - quiz jouable par défaut, réponses absentes tolérées`() {
        val rep = Json.parseToJsonElement(
            """
            {"data": {"quiz_id": "7", "label": "Minimal", "questions": [
              {"question": "Capitale du Portugal ?", "reponses": []}
            ]}}
            """.trimIndent(),
        ).jsonObject
        val quiz = Normalizers.quiz(rep)!!
        assertEquals("7", quiz.id)
        assertTrue(quiz.peutJouer)
        assertFalse(quiz.peutRejouer)
        assertEquals(1, quiz.nbQuestions)
        assertTrue(quiz.questions.first().reponses.isEmpty())
        assertNull(quiz.questions.first().tempsReponse)
    }

    @Test
    fun `sans data ni quiz_id - illisible`() {
        val rep = Json.parseToJsonElement("""{"empty": true}""").jsonObject
        assertNull(Normalizers.quiz(rep))
    }

    @Test
    fun `question sans texte - écartée, les autres passent`() {
        val rep = Json.parseToJsonElement(
            """
            {"data": {"quiz_id": "9", "label": "Mixte", "questions": [
              {"alias": "vide", "reponses": []},
              {"question": "5 × 6 ?", "temps_reponse": 30,
               "reponses": [{"reponse": "30", "correct": true}]}
            ]}}
            """.trimIndent(),
        ).jsonObject
        val quiz = Normalizers.quiz(rep)!!
        assertEquals(1, quiz.nbQuestions)
        assertEquals("5 × 6 ?", quiz.questions.first().texte)
    }

    @Test
    fun `résultat - enveloppe plate et sous data`() {
        val plat = Json.parseToJsonElement(
            """{"score": "4/5", "time": "03:12", "can_replay": true}""",
        ).jsonObject
        with(Normalizers.quizRésultat(plat)) {
            assertEquals("4/5", score)
            assertEquals("03:12", temps)
            assertTrue(peutRejouer == true)
        }
        val enveloppé = Json.parseToJsonElement(
            """{"status": 200, "data": {"score": "3/5", "time": "02:40"}}""",
        ).jsonObject
        with(Normalizers.quizRésultat(enveloppé)) {
            assertEquals("3/5", score)
            assertEquals("02:40", temps)
            assertNull(peutRejouer)
        }
    }

    @Test
    fun `questionsPourEnvoi - answer mis à jour, le reste intact`() {
        val brutes = Json.parseToJsonElement(
            """
            [
              {"question": "2 + 2 ?", "alias": "",
               "answer": {"answer": "", "correct": "4", "answered": null},
               "temps_reponse": 60,
               "reponses": [{"reponse": "3", "correct": false},
                            {"reponse": "4", "correct": true}]},
              {"question": "5 × 6 ?", "temps_reponse": 30,
               "answer": {"answer": "", "correct": "30", "answered": null},
               "reponses": [{"reponse": "30", "correct": true}]}
            ]
            """.trimIndent(),
        ).jsonArray

        val envoyé = questionsPourEnvoi(
            brutes,
            mapOf(0 to RéponseJouée("4", 12), 1 to RéponseJouée("", 30)),
        )

        val q0 = envoyé[0].jsonObject
        assertEquals("4", q0["answer"]!!.jsonObject["answer"]!!.jsonPrimitive.content)
        assertEquals(12, q0["answer"]!!.jsonObject["answered"]!!.jsonPrimitive.content.toInt())
        // Le drapeau serveur (texte de la bonne réponse) part tel quel.
        assertEquals(
            "4",
            q0["answer"]!!.jsonObject["correct"]!!.jsonPrimitive.content,
        )
        assertEquals("2 + 2 ?", q0["question"]!!.jsonPrimitive.content)

        val q1 = envoyé[1].jsonObject
        assertEquals("", q1["answer"]!!.jsonObject["answer"]!!.jsonPrimitive.content)
        assertEquals(30, q1["answer"]!!.jsonObject["answered"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `questionsPourEnvoi - question sans objet answer en reçoit un`() {
        val brutes = Json.parseToJsonElement(
            """[{"question": "1 + 1 ?", "reponses": []}]""",
        ).jsonArray
        val envoyé = questionsPourEnvoi(brutes, mapOf(0 to RéponseJouée("2", 5)))
        val answer = envoyé[0].jsonObject["answer"]!!.jsonObject
        assertEquals("2", answer["answer"]!!.jsonPrimitive.content)
        assertEquals(5, answer["answered"]!!.jsonPrimitive.content.toInt())
    }
}
