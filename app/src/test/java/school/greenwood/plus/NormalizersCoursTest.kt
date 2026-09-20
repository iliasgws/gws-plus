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
import java.time.LocalDate

/*
 * Jeux d'essai synthétiques, sans contenu réel de l'école. La forme de tête
 * de `cours_v2` est celle du sondage du 2026-09-20 (ENDPOINT-MAP) ; la forme
 * des créneaux intérieurs est INCONNUE — les fixtures couvrent des champs
 * plausibles et des formes méconnaissables, le normaliseur doit tout tolérer.
 */
class NormalizersCoursTest {

    /** Semaine de sondage synthétique : lundi vide, vendredi portant les créneaux passés. */
    private fun semaine(créneauxVendredi: kotlinx.serialization.json.JsonArrayBuilder.() -> Unit = {}): kotlinx.serialization.json.JsonObject {
        val jours = buildJsonArray {
            add(buildJsonObject {
                put("label", "L")
                put("day", 1)
                put("date", "Le 14 Sep 2026")
            })
            add(buildJsonObject {
                put("label", "V")
                put("day", 5)
                put("date", "Le 18 Sep 2026")
                put("seances", buildJsonArray(créneauxVendredi))
            })
        }
        return buildJsonObject {
            putJsonObject("translation") {
                put("title", "Emploi du temps")
                put("aucun_cours", "Pas de cours ce jour")
            }
            put("next_week", "2026-09-21")
            put("last_week", "2026-09-07")
            put("selected_day", 5)
            put("label", "Du  2026/09/14 Au  2026/09/20")
            put("seances", jours)
        }
    }

    @Test
    fun `semaineCours lit la forme de tête vérifiée`() {
        val s = Normalizers.semaineCours(semaine())!!
        assertEquals("Du  2026/09/14 Au  2026/09/20", s.label)
        assertEquals(LocalDate.of(2026, 9, 14), s.lundi)
        assertEquals(5, s.jourSélectionné)
        assertEquals(LocalDate.of(2026, 9, 21), s.semaineSuivante)
        assertEquals(LocalDate.of(2026, 9, 7), s.semainePrécédente)
        assertEquals("Pas de cours ce jour", s.aucunCours)
        assertEquals(2, s.journées.size)
        // Les dates des jours sont dérivées du lundi, pas du libellé anglais.
        val vendredi = s.journées.first { it.jour == 5 }
        assertEquals(LocalDate.of(2026, 9, 18), vendredi.date)
        assertEquals("V", vendredi.label)
        assertTrue(vendredi.créneaux.isEmpty())
        assertFalse(s.restreint)
    }

    @Test
    fun `semaineCours extrait les créneaux aux noms de champs plausibles`() {
        val s = Normalizers.semaineCours(
            semaine { add(buildJsonObject {
                put("matiere", "Mathématiques")
                put("heure_debut", "08:00")
                put("heure_fin", "09:00")
                put("salle", "B12")
                put("prof", "Mme Martin")
            }) },
        )!!
        val créneau = s.journées.first { it.jour == 5 }.créneaux.single()
        assertEquals("Mathématiques", créneau.matière)
        assertEquals("08:00", créneau.début)
        assertEquals("09:00", créneau.fin)
        assertEquals("B12", créneau.salle)
        assertEquals("Mme Martin", créneau.enseignant)
    }

    @Test
    fun `semaineCours extrait l'heure depuis un texte verbeux`() {
        val s = Normalizers.semaineCours(
            semaine { add(buildJsonObject {
                put("title", "Histoire")
                put("hdebut", "de 10h30")
                put("hfin", "à 11h25")
            }) },
        )!!
        val créneau = s.journées.first { it.jour == 5 }.créneaux.single()
        assertEquals("10:30", créneau.début)
        assertEquals("11:25", créneau.fin)
    }

    @Test
    fun `semaineCours tolère une forme de créneau méconnaissable`() {
        val s = Normalizers.semaineCours(
            semaine { add(buildJsonObject {
                put("id", 77)
                put("visible", true)
                put("texte_libre", "Quelque chose")
            }) },
        )!!
        val créneau = s.journées.first { it.jour == 5 }.créneaux.single()
        assertEquals("Quelque chose", créneau.matière)
        assertNull(créneau.début)
    }

    @Test
    fun `semaineCours accepte un créneau en simple chaîne`() {
        val s = Normalizers.semaineCours(
            semaine { add(kotlinx.serialization.json.JsonPrimitive("Sport 14h")) },
        )!!
        val créneau = s.journées.first { it.jour == 5 }.créneaux.single()
        assertEquals("Sport 14h", créneau.matière)
    }

    @Test
    fun `semaineCours classe les jours et accepte un day manquant`() {
        val rep = buildJsonObject {
            put("label", "Du  2026/09/14 Au  2026/09/20")
            putJsonArray("seances") {
                add(buildJsonObject {
                    put("label", "M")
                    put("day", 2)
                })
                add(buildJsonObject {
                    put("label", "L")
                    put("day", 1)
                })
            }
        }
        val s = Normalizers.semaineCours(rep)!!
        assertEquals(listOf(1, 2), s.journées.map { it.jour })
    }

    @Test
    fun `semaineCours rend null sur une réponse vide`() {
        assertNull(Normalizers.semaineCours(buildJsonObject { }))
        assertNull(Normalizers.semaineCours(buildJsonObject { put("label", "Du 2026/09/14") }))
    }

    @Test
    fun `semaineCours lit la restriction du serveur`() {
        val rep = buildJsonObject {
            put("label", "Du  2026/09/14 Au  2026/09/20")
            putJsonArray("seances") {
                add(buildJsonObject { put("day", 1) })
            }
            putJsonObject("restricted") {
                put("restricted", "1")
                put("label", "<p>Voir l'administration.</p>")
            }
        }
        val s = Normalizers.semaineCours(rep)!!
        assertTrue(s.restreint)
        // HTML conservé brut — aplati à l'écran, pas dans le normaliseur.
        assertEquals("<p>Voir l'administration.</p>", s.messageRestriction)
    }

    @Test
    fun `semaineCours laisse selected_day passer tel quel`() {
        val rep = buildJsonObject {
            put("label", "Du  2026/09/14 Au  2026/09/20")
            put("selected_day", 9)
            putJsonArray("seances") {
                add(buildJsonObject { put("day", 1) })
            }
        }
        val s = Normalizers.semaineCours(rep)
        assertNotNull(s)
        assertEquals(9, s!!.jourSélectionné)
    }
}
