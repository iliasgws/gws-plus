package school.greenwood.plus

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.repo.EntreeRegistre
import school.greenwood.plus.model.Absence
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Message
import school.greenwood.plus.model.Post
import school.greenwood.plus.ui.screens.registre.Apercu
import school.greenwood.plus.ui.screens.registre.DétailApercu
import school.greenwood.plus.ui.screens.registre.apercuDe

/*
 * L'aperçu contextuel (issue #107) : le mapping carte → feuille reste pur
 * (aucune API Android), ne montre que des champs réels de la donnée, et
 * garde le HTML intact — l'aplatissement (`htmlToPlainMultiline`)
 * appartient à l'affichage, jamais à ce mapping.
 */
class ApercuTest {

    private fun Apercu.état(): String? = détails.firstOrNull { it.libellé == "État" }?.valeur

    @Test
    fun `actualité complète — tous les champs, corps HTML intact`() {
        val intro = "<p style=\"text-align:justify\">Rentrée <b>le 8 septembre</b> à 8 h.</p>"
        val post = Post(
            id = "p1",
            title = "Réunion des parents",
            categorie = "Vie scolaire",
            date = LocalDateTime.of(2026, 9, 1, 12, 47),
            intro = intro,
            image = "https://example.org/photo.jpg",
            auteur = "La direction",
        )
        val apercu = apercuDe(EntreeRegistre.Actualite(post))
        assertEquals("Actualité", apercu.type)
        assertEquals("Réunion des parents", apercu.titre)
        assertEquals("le 01/09/2026 à 12:47", apercu.date)
        assertEquals(intro, apercu.corps)
        assertTrue("le corps doit rester en HTML brut", apercu.corps!!.contains("<b>"))
        assertEquals("https://example.org/photo.jpg", apercu.vignette)
        assertEquals(
            listOf(
                DétailApercu("Catégorie", "Vie scolaire"),
                DétailApercu("Auteur", "La direction"),
            ),
            apercu.détails,
        )
    }

    @Test
    fun `actualité dépouillée — rien d'inventé, que des nulls`() {
        val apercu = apercuDe(EntreeRegistre.Actualite(Post(id = "p2", title = "Sans rien")))
        assertEquals("Actualité", apercu.type)
        assertEquals("Sans rien", apercu.titre)
        assertNull(apercu.date)
        assertNull(apercu.corps)
        assertNull(apercu.vignette)
        assertTrue(apercu.détails.isEmpty())
    }

    @Test
    fun `actualité sans intro — repli sur le corps complet`() {
        val description = "<p>Corps complet de l'annonce.</p>"
        val apercu = apercuDe(
            EntreeRegistre.Actualite(Post(id = "p3", title = "Annonce", intro = "   ", description = description)),
        )
        assertEquals(description, apercu.corps)
    }

    @Test
    fun `devoir complet — matière, enseignant, échéance, état et pièces jointes`() {
        val devoir = Devoir(
            id = "d1",
            title = "Exercices sur les fractions",
            matiere = "Mathématiques",
            categorie = "Devoir maison",
            enseignant = "Mme Dupont",
            description = "<p>Pages 12 à 15.</p>",
            dateRemise = LocalDate.of(2026, 9, 26),
            publication = LocalDateTime.of(2026, 9, 22, 8, 15),
            attachments = listOf(
                Attachment(name = "énoncé.pdf", url = "https://example.org/a.pdf"),
                Attachment(name = "corrigé.odt", url = "https://example.org/b.odt"),
            ),
        )
        val apercu = apercuDe(EntreeRegistre.DevoirDonné(devoir))
        assertEquals("Devoir", apercu.type)
        assertEquals("Exercices sur les fractions", apercu.titre)
        assertEquals("le 22/09/2026 à 08:15", apercu.date)
        assertEquals("<p>Pages 12 à 15.</p>", apercu.corps)
        assertNull(apercu.vignette)
        assertEquals(
            listOf(
                DétailApercu("Matière", "Mathématiques"),
                DétailApercu("Enseignant(e)", "Mme Dupont"),
                DétailApercu("Catégorie", "Devoir maison"),
                DétailApercu("Échéance", "26/09/2026"),
                DétailApercu("État", "À faire"),
                DétailApercu("Pièces jointes", "énoncé.pdf, corrigé.odt"),
            ),
            apercu.détails,
        )
    }

    @Test
    fun `devoir minimal — pas de détail inventé`() {
        val apercu = apercuDe(
            EntreeRegistre.DevoirDonné(Devoir(id = "d2", title = "Lecture", matiere = "")),
        )
        assertEquals(listOf(DétailApercu("État", "À faire")), apercu.détails)
        assertNull(apercu.date)
        assertNull(apercu.corps)
    }

    @Test
    fun `devoir fait au serveur — état « Travail fait (connu de l'école) »`() {
        val apercu = apercuDe(EntreeRegistre.DevoirDonné(Devoir(id = "d3", title = "T", matiere = "M", fait = true)))
        assertEquals("Travail fait (connu de l'école)", apercu.état())
    }

    @Test
    fun `devoir fait localement — état « Marqué fait pour moi »`() {
        val apercu = apercuDe(
            EntreeRegistre.DevoirDonné(Devoir(id = "d4", title = "T", matiere = "M", faitLocal = true)),
        )
        assertEquals("Marqué fait pour moi", apercu.état())
    }

    @Test
    fun `absence non justifiée — état et période réelles`() {
        val absence = Absence(
            id = "a1",
            motif = "Raison médicale",
            du = LocalDate.of(2026, 9, 1),
            au = LocalDate.of(2026, 9, 3),
            justifiee = false,
        )
        val apercu = apercuDe(EntreeRegistre.AbsenceNotée(absence))
        assertEquals("Absence", apercu.type)
        assertEquals("Raison médicale", apercu.titre)
        assertEquals("01/09/2026 → 03/09/2026", apercu.date)
        assertEquals("Non justifiée", apercu.état())
        assertNull(apercu.corps)
        assertNull(apercu.vignette)
    }

    @Test
    fun `absence justifiée d'une seule journée — une seule date`() {
        val jour = LocalDate.of(2026, 9, 7)
        val absence = Absence(id = "a2", motif = null, du = jour, au = jour, justifiee = true)
        val apercu = apercuDe(EntreeRegistre.AbsenceNotée(absence))
        assertEquals("Absence notée", apercu.titre)
        assertEquals("07/09/2026", apercu.date)
        assertEquals("Justifiée", apercu.état())
    }

    @Test
    fun `message - dernier texte HTML conservé brut`() {
        val conversation = Conversation(
            id = "c1",
            sujet = "Inscription",
            messages = listOf(
                Message(id = "m1", deLAdmin = false, texte = "Bonjour", date = LocalDateTime.of(2026, 9, 10, 9, 0)),
                Message(
                    id = "m2",
                    deLAdmin = true,
                    texte = "<p>Voici <b>les</b> informations.</p>",
                    date = LocalDateTime.of(2026, 9, 11, 14, 30),
                ),
            ),
        )
        val apercu = apercuDe(EntreeRegistre.MessageReçu(conversation))
        assertEquals("Message", apercu.type)
        assertEquals("Inscription", apercu.titre)
        assertEquals("le 11/09/2026 à 14:30", apercu.date)
        assertEquals("<p>Voici <b>les</b> informations.</p>", apercu.corps)
        assertNull(apercu.vignette)
        assertTrue(apercu.détails.isEmpty())
    }

    @Test
    fun `message vide — ni date ni corps`() {
        val apercu = apercuDe(
            EntreeRegistre.MessageReçu(Conversation(id = "c2", sujet = "Sans fil", messages = emptyList())),
        )
        assertEquals("Message", apercu.type)
        assertEquals("Sans fil", apercu.titre)
        assertNull(apercu.date)
        assertNull(apercu.corps)
        assertTrue(apercu.détails.isEmpty())
    }

    @Test
    fun `dernière actualité — l'aperçu du post égale celui de l'entrée`() {
        val post = Post(
            id = "p4",
            title = "Sortie scolaire",
            categorie = "Sorties",
            date = LocalDateTime.of(2026, 10, 2, 17, 5),
            intro = "<p>Direction le musée.</p>",
            image = "https://example.org/sortie.jpg",
            auteur = "Mme Faure",
        )
        assertEquals(
            apercuDe(EntreeRegistre.Actualite(post)),
            apercuDe(post),
        )
    }
}
