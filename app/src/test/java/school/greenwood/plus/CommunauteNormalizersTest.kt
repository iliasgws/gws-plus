package school.greenwood.plus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.repo.CommunNormalizers
import school.greenwood.plus.model.CibleSignalement
import java.time.LocalDate

/*
 * Les formes du serveur communautaire (APP.md §3) : clés accentuées,
 * champs facultatifs réellement absents (pas à null), lignes illisibles
 * écartées sans casser la page.
 */
class CommunauteNormalizersTest {

    private fun json(brut: String) = Json.parseToJsonElement(brut)

    @Test
    fun `devoir complet - clés accentuées lues`() {
        val devoir = CommunNormalizers.devoirSuggéré(
            json(
                """
                {"id":7,"auteurId":"50d1967c942dc6d2","matière":"Maths",
                 "contenu":"Exos 1 à 5","dateRemise":"2026-10-02",
                 "votes":3,"crééÀ":1759000000000}
                """,
            ) as JsonObject,
        )!!
        assertEquals(7L, devoir.id)
        assertEquals("Maths", devoir.matière)
        assertEquals("Exos 1 à 5", devoir.contenu)
        assertEquals(LocalDate.of(2026, 10, 2), devoir.dateRemise)
        assertEquals(3, devoir.votes)
        assertEquals(1_759_000_000_000L, devoir.crééÀ)
    }

    @Test
    fun `devoir sans date de remise - clé absente tolérée`() {
        val devoir = CommunNormalizers.devoirSuggéré(
            json("""{"id":1,"auteurId":"a","matière":"M","contenu":"c","votes":0,"crééÀ":0}""")
                as JsonObject,
        )!!
        assertNull(devoir.dateRemise)
    }

    @Test
    fun `problème - état repris tel quel, date nulle tolérée`() {
        val problème = CommunNormalizers.problèmeHoraire(
            json("""{"id":2,"auteurId":"b","description":"Salle manquante","état":"résolu","crééÀ":12}""")
                as JsonObject,
        )!!
        assertEquals("résolu", problème.état)
        assertNull(problème.date)
        assertEquals(12L, problème.crééÀ)
    }

    @Test
    fun `correction - problèmeId facultatif`() {
        val correction = CommunNormalizers.correctionHoraire(
            json("""{"id":9,"auteurId":"c","description":"En B12","date":"2026-09-30","problèmeId":4}""")
                as JsonObject,
        )!!
        assertEquals(4L, correction.problèmeId)

        val orpheline = CommunNormalizers.correctionHoraire(
            json("""{"id":10,"auteurId":"c","description":"Seule","date":"2026-09-30"}""")
                as JsonObject,
        )!!
        assertNull(orpheline.problèmeId)
    }

    @Test
    fun `signalement - cible sans accent`() {
        val signalement = CommunNormalizers.signalementAbus(
            json("""{"id":5,"cible":"probleme","cibleId":42,"raison":"Faux","crééÀ":3}""")
                as JsonObject,
        )!!
        assertEquals("probleme", signalement.cible)
        assertEquals(42L, signalement.cibleId)
        assertEquals(
            CibleSignalement.Problème.param,
            signalement.cible,
        )
    }

    @Test
    fun `lignes sans id écartées, les autres conservées`() {
        val liste = CommunNormalizers.liste(
            json("""[{"matière":"sans id"},{"id":1,"auteurId":"a","matière":"M","contenu":"c","votes":1,"crééÀ":0}]"""),
        ) { CommunNormalizers.devoirSuggéré(it) }
        assertEquals(1, liste.size)
        assertEquals(1L, liste.first().id)
    }

    @Test
    fun `réponse qui n'est pas un tableau - liste vide, pas de crash`() {
        assertTrue(CommunNormalizers.liste(json("""{"erreur":"x"}""")) { null }.isEmpty())
    }

    @Test
    fun `notice - version et cinq sections`() {
        val mentions = CommunNormalizers.mentions(
            json(
                """
                {"version":"2026-09-28","sections":[
                  {"id":"objet","titre":"Objet","texte":"Un espace entre familles."},
                  {"id":"moderation","titre":"Modération","texte":"Signale ce qui cloche."},
                  {"id":"compte","titre":"Compte","texte":"Un jeton anonyme."},
                  {"id":"votes","titre":"Votes","texte":"Un vote par contenu."},
                  {"id":"donnees","titre":"Données","texte":"Rien de personnel."}
                ]}
                """,
            ),
        )!!
        assertEquals("2026-09-28", mentions.version)
        assertEquals(5, mentions.sections.size)
        assertEquals("objet", mentions.sections.first().id)
        assertEquals("Modération", mentions.sections[1].titre)
    }

    @Test
    fun `notice sans version - illisible`() {
        assertNull(CommunNormalizers.mentions(json("""{"sections":[]}""")))
        assertNull(CommunNormalizers.mentions(json("""[1,2]""")))
    }
}
