package school.greenwood.plus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import school.greenwood.plus.data.repo.Normalizers
import school.greenwood.plus.ui.FiltreDocuments
import school.greenwood.plus.ui.filtrerFiches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Bibliothèque (issue #43) — formes vérifiées en sonde lecture-seule le
 * 22/09/2026 : GET `bibliotheque` (unites), GET `bibliotheque?unite=<id>`
 * (fiches), GET `ressource_details?ressource=<id>` (détail avec liens signés).
 * Jeux d'essai synthétiques, sans contenu réel de l'école.
 */
class BibliothequeTest {

    @Test
    fun `unité complète - compte en chaîne et drapeau neuf`() {
        val raw = Json.parseToJsonElement(
            """
            {
              "id": "2",
              "label": "Mathématiques",
              "icon": "https://exemple.test/unites/maths.png",
              "color": "#33a6e1",
              "count_resources": "1",
              "has_new": false
            }
            """.trimIndent(),
        ).jsonObject

        val unite = Normalizers.uniteBibliotheque(raw)!!
        assertEquals("2", unite.id)
        assertEquals("Mathématiques", unite.label)
        assertEquals("https://exemple.test/unites/maths.png", unite.image)
        assertEquals(1, unite.nombreRessources)
        assertFalse(unite.aDuNeuf)
    }

    @Test
    fun `unité - drapeau neuf en chaîne et compte manquant`() {
        val raw = Json.parseToJsonElement(
            """{"id": "7", "label": "Histoire Géographie", "count_resources": "3", "has_new": "true"}""",
        ).jsonObject

        val unite = Normalizers.uniteBibliotheque(raw)!!
        assertEquals(3, unite.nombreRessources)
        assertTrue(unite.aDuNeuf)
    }

    @Test
    fun `fiche complète - matière portée par l'unité`() {
        val raw = Json.parseToJsonElement(
            """
            {
              "id": "25835",
              "color": "558c4c",
              "title": "Cours et exercices de démonstration",
              "categorie": "Ressource",
              "date": "22 Septembre 2026",
              "file": {"text": "Télécharger", "link": "cours-exemple.pdf"},
              "image": "https://exemple.test/unites/maths.png",
              "intro": null,
              "description": null,
              "by": "Prof Exemple"
            }
            """.trimIndent(),
        ).jsonObject

        val fiche = Normalizers.ficheBibliotheque(raw, uniteId = "2", matiere = "Mathématiques")!!
        assertEquals("25835", fiche.id)
        assertEquals("2", fiche.uniteId)
        assertEquals("Mathématiques", fiche.matiere)
        assertEquals("Cours et exercices de démonstration", fiche.titre)
        assertEquals("Ressource", fiche.categorie)
        assertEquals("22 Septembre 2026", fiche.date)
        assertEquals("Prof Exemple", fiche.par)
        assertEquals("558c4c", fiche.couleur)
    }

    @Test
    fun `fiche sans id - rejetée`() {
        val raw = Json.parseToJsonElement("""{"title": "sans identifiant"}""").jsonObject
        assertNull(Normalizers.ficheBibliotheque(raw, uniteId = "2", matiere = "Mathématiques"))
    }

    @Test
    fun `détail - pièces jointes signées conservées`() {
        val rep = Json.parseToJsonElement(
            """
            {
              "empty_date": {"img": "", "label": "Aucune donnée pour le moment."},
              "groupe": "Toutes les Classes",
              "title": "ressources",
              "translation": [],
              "data": {
                "id": "25835",
                "title": "Cours et exercices de démonstration - Mathématiques",
                "categorie": "Ressource",
                "date": null,
                "description": "",
                "files": [
                  {
                    "text": "Télécharger la pièce jointe1.pdf",
                    "link": "https://media.exemple.test/view/TOKEN.1790104007/original/docs/cours-exemple.pdf",
                    "path": "https://media.exemple.test/view/TOKEN.1790104007/original/docs/cours-exemple.pdf",
                    "type": "file",
                    "mime": {"type": "application/pdf", "ext": "file"},
                    "filename": "cours-exemple.pdf"
                  }
                ],
                "matiere": "Mathématiques",
                "matiereId": "2",
                "image": null
              }
            }
            """.trimIndent(),
        ).jsonObject

        val détail = Normalizers.détailBibliotheque(rep)!!
        assertEquals("25835", détail.id)
        assertEquals("Mathématiques", détail.matiere)
        assertEquals(1, détail.fichiers.size)
        assertEquals("cours-exemple.pdf", détail.fichiers.first().name)
        assertTrue(détail.fichiers.first().url.startsWith("https://media.exemple.test/"))
        // description vide = null (pas de bloc vide à l'écran)
        assertNull(détail.description)
    }

    @Test
    fun `fichier sans URL réelle - rejeté du détail`() {
        // Le `file.link` de la liste n'est qu'un nom de fichier : il ne doit
        // jamais devenir une pièce jointe téléchargeable.
        val raw = Json.parseToJsonElement(
            """{"text": "Télécharger", "link": "cours-exemple.pdf", "filename": "cours-exemple.pdf"}""",
        ).jsonObject
        assertNull(Normalizers.fichierRessource(raw))
    }

    @Test
    fun `filtrer fiches - recherche par titre ou matière, jamais sous Quiz`() {
        val fiches = listOf(
            fiche("A", "Cours et exercices de démonstration", "Mathématiques"),
            fiche("B", "Support de cours", "Français"),
        )

        assertEquals(2, filtrerFiches(fiches, "", FiltreDocuments.Tout).size)
        assertEquals(2, filtrerFiches(fiches, "", FiltreDocuments.Documents).size)
        assertTrue(filtrerFiches(fiches, "", FiltreDocuments.Quiz).isEmpty())

        assertEquals(1, filtrerFiches(fiches, "démonstration", FiltreDocuments.Tout).size)
        assertEquals(1, filtrerFiches(fiches, "français", FiltreDocuments.Tout).size)
        assertEquals(0, filtrerFiches(fiches, "inexistant", FiltreDocuments.Tout).size)
    }

    private fun fiche(id: String, titre: String, matiere: String) =
        school.greenwood.plus.model.FicheBibliotheque(
            id = id,
            uniteId = "1",
            matiere = matiere,
            titre = titre,
        )
}
