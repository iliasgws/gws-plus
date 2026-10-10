package school.greenwood.plus

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.util.Completude
import school.greenwood.plus.util.ItemPartagé
import school.greenwood.plus.util.LIMITE_TEXTE_CHATGPT
import school.greenwood.plus.util.LigneActualité
import school.greenwood.plus.util.LigneDevoir
import school.greenwood.plus.util.ligneActualitéTexte
import school.greenwood.plus.util.ligneDevoirTexte
import school.greenwood.plus.util.texteChatGPT

/** Issue #150 (v2) : le prompt pré-rempli envoyé à ChatGPT. */
class ChatGPTTexteTest {

    private val synchronisation = LocalDateTime.of(2026, 10, 10, 16, 30)

    private val item = ItemPartagé(
        type = "Devoir",
        titre = "Exercices sur les fractions",
        métadonnées = listOf("Matière : Mathématiques", "Échéance : 12/10/2026"),
        corps = "Faire les exercices 1 à 5 page 42.",
        piècesJointes = listOf("énoncé.pdf"),
        fichiersPartagés = listOf("énoncé.pdf"),
    )

    @Test
    fun `les cinq sections de l'issue sont présentes, dans l'ordre`() {
        val texte = texteChatGPT(
            item = item,
            devoirs = listOf(LigneDevoir(titre = "Autre devoir", matière = "Français")),
            actualités = listOf(LigneActualité(titre = "Sortie au musée", date = "20/09/2026")),
            synchronisation = synchronisation,
        )
        val sections = listOf(
            "PROFIL DE L'ÉCOLE",
            "ÉLÉMENT SÉLECTIONNÉ — CONTENU COMPLET",
            "AUTRES DEVOIRS DISPONIBLES — INDEX COMPLET (métadonnées seulement)",
            "ACTUALITÉS DE L'ÉCOLE — INDEX COMPLET (titres seulement)",
            "INSTRUCTIONS",
        )
        val positions = sections.map { texte.indexOf(it) }
        assertTrue("sections manquantes : $sections", positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun `l'élément sélectionné est en entier, avec ses métadonnées et ses fichiers`() {
        val texte = texteChatGPT(item, emptyList(), emptyList(), synchronisation)
        assertTrue(texte.contains("Type : Devoir"))
        assertTrue(texte.contains("Titre : Exercices sur les fractions"))
        assertTrue(texte.contains("Matière : Mathématiques"))
        assertTrue(texte.contains("Échéance : 12/10/2026"))
        assertTrue(texte.contains("Faire les exercices 1 à 5 page 42."))
        assertTrue(texte.contains("Pièces jointes connues : énoncé.pdf"))
        assertTrue(texte.contains("Fichiers joints à ce message : énoncé.pdf"))
    }

    @Test
    fun `aucun fichier joint — le prompt le dit au lieu de faire croire le contraire`() {
        val texte = texteChatGPT(
            item.copy(fichiersPartagés = emptyList()),
            emptyList(),
            emptyList(),
            synchronisation,
        )
        assertTrue(texte.contains("Fichiers joints à ce message : aucun"))
    }

    @Test
    fun `index partiel — jamais annoncé complet à moitié vrai`() {
        val texte = texteChatGPT(
            item = item,
            devoirs = emptyList(),
            actualités = emptyList(),
            synchronisation = synchronisation,
            completudeDevoirs = Completude.PARTIELLE,
            completudeActualités = Completude.PARTIELLE,
        )
        assertTrue(texte.contains("PARTIEL (pas tous les éléments accessibles sont listés)"))
    }

    @Test
    fun `index trop gros — troncature annoncée et élément sélectionné intact`() {
        val gros = (1..400).map { n ->
            LigneDevoir(
                titre = "Devoir $n " + "x".repeat(80),
                matière = "Mathématiques",
                échéance = "12/10/2026",
                état = "À faire",
                identifiant = "id$n",
            )
        }
        val texte = texteChatGPT(item, gros, emptyList(), synchronisation)
        assertTrue(texte.length <= LIMITE_TEXTE_CHATGPT)
        assertTrue(texte.contains("TRONQUÉ"))
        assertTrue(texte.contains("PARTIEL"))
        assertTrue(texte.contains("Titre : Exercices sur les fractions"))
        assertTrue(texte.contains("Faire les exercices 1 à 5 page 42."))
    }

    @Test
    fun `corps gigantesque — tronqué explicitement, jamais silencieusement`() {
        val énorme = item.copy(corps = "mot ".repeat(80_000))
        val texte = texteChatGPT(énorme, emptyList(), emptyList(), synchronisation)
        assertTrue(texte.length <= LIMITE_TEXTE_CHATGPT)
        assertTrue(texte.contains("CORPS TRONQUÉ"))
        assertTrue(texte.contains("Titre : Exercices sur les fractions"))
    }

    @Test
    fun `lignes d'index — format compact et aucun corps`() {
        assertEquals(
            listOf("Titre | Matière | 12/10/2026 | À faire | id d1"),
            listOf(
                ligneDevoirTexte(
                    LigneDevoir(
                        titre = "Titre",
                        matière = "Matière",
                        échéance = "12/10/2026",
                        état = "À faire",
                        identifiant = "d1",
                    )
                ),
            ),
        )
        assertTrue(ligneActualitéTexte(LigneActualité(titre = "Nouvelle", date = "20/09/2026")).contains("Nouvelle"))
    }

    @Test
    fun `profil école — uniquement des faits vérifiés, plus le rôle de l'app`() {
        val texte = texteChatGPT(item, emptyList(), emptyList(), synchronisation)
        assertTrue(texte.contains("Bouskoura"))
        assertTrue(texte.contains("https://greenwoodschool.ma/"))
        assertTrue(texte.contains("PET"))
        assertTrue(texte.contains("GWS Plus est l'application Android officielle de l'école"))
    }
}
