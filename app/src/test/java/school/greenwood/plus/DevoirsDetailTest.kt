package school.greenwood.plus

import school.greenwood.plus.data.repo.Normalizers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Le détail d'un devoir (issue #68) : formes réelles de la sonde du
 * 26/09/2026 (GET `devoirs&devoir=<id>` et POST `devoirs_date_v2`).
 */
class DevoirsDetailTest {

    // — détail (sonde devoirs_single_25981.json) ----------------------------

    @Test
    fun `détail - pièces jointes, droits et état de soumission`() {
        val rep = Json.parseToJsonElement(
            """
            {
              "id": "25981",
              "title": "planning du projet artstique - Arts-Plastiques ",
              "categorie": "Cahier de liaison",
              "matiere": "Arts-Plastiques ",
              "enseignant": "Belhadj  amine",
              "description": "<p>A remplir</p>",
              "files": [
                {
                  "link": "https://media.boti.education/view/abc/original/a.docx",
                  "filename": "fiche-technique.docx",
                  "text": "Télécharger la pièce jointe1.docx"
                },
                {
                  "link": "http://docs.google.com/viewer?url=https%3A%2F%2Fmedia.boti.education%2Fview%2Fdef%2Foriginal%2Fb.docx",
                  "filename": "planning.docx",
                  "text": "Télécharger la pièce jointe2.docx"
                }
              ],
              "txt_btn_fait": "Oui, j'ai bien fait mon devoir!",
              "devoir_fait": { "files": null, "file_sent": false, "fait": false },
              "can_set_done": true,
              "can_add_files": true,
              "show_files": true
            }
            """.trimIndent(),
        ).jsonObject

        val détail = Normalizers.devoirDétail(rep)
        assertTrue(détail.peutMarquerFait)
        assertTrue(détail.peutAjouterFichiers)
        assertTrue(détail.montrerFichiers)
        assertFalse(détail.devoir.fait)
        assertEquals(2, détail.devoir.attachments.size)
        // Le lien viewer est décodé une fois ; le lien direct passe tel quel.
        assertEquals("https://media.boti.education/view/def/original/b.docx", détail.devoir.attachments[1].url)
        assertEquals("planning.docx", détail.devoir.attachments[1].name)
    }

    @Test
    fun `détail - devoir ancien sans droit de marquage`() {
        val rep = Json.parseToJsonElement(
            """
            {
              "id": "25058", "title": "Examens", "matiere": "Mathématiques",
              "devoir_fait": { "files": null, "file_sent": false, "fait": false },
              "can_set_done": false, "can_add_files": false, "show_files": true
            }
            """.trimIndent(),
        ).jsonObject

        val détail = Normalizers.devoirDétail(rep)
        assertFalse(détail.peutMarquerFait)
        assertFalse(détail.peutAjouterFichiers)
    }

    // — soumission (réponse du POST, bundle 3537.js / 154.js) ---------------

    @Test
    fun `soumission - devoir simplement marqué fait`() {
        val rep = Json.parseToJsonElement(
            """{"status": 202, "title": "Travail envoyé", "message": "Bravo"}""",
        ).jsonObject
        val résultat = Normalizers.soumission(rep)
        assertTrue(résultat.fait)
        assertFalse(résultat.envoyées)
        assertEquals("Travail envoyé", résultat.titre)
        assertEquals("Bravo", résultat.message)
    }

    @Test
    fun `soumission - copies envoyées, liens serveur en retour`() {
        val rep = Json.parseToJsonElement(
            """
            {
              "file_sent": true,
              "title": "Travail envoyé",
              "message": "Bravo, Travail envoyé...",
              "files": [
                { "name": "copie.pdf", "link": "https://media.boti.education/view/x/original/copie.pdf" }
              ]
            }
            """.trimIndent(),
        ).jsonObject
        val résultat = Normalizers.soumission(rep)
        assertTrue(résultat.envoyées)
        assertTrue(résultat.fait)
        assertEquals(1, résultat.copies.size)
        assertEquals("copie.pdf", résultat.copies[0].name)
    }
}
