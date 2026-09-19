package school.greenwood.plus

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.repo.Normalizers
import school.greenwood.plus.data.repo.mimeDe
import school.greenwood.plus.model.MessageEnvoi

/*
 * Chemin d'écriture du composeur (issue #10, seconde partie) : parsing des
 * catégories serveur `themes[]`, thème porté par le fil (repris tel quel dans
 * la réponse), normalisation du `.message` renvoyé par le POST (l'élément qui
 * remplace l'envoi optimiste) et mimes des pièces jointes.
 */
class ComposeurDonnéesTest {

    @Test
    fun `themes du serveur parsés avec leurs labels`() {
        val rep = buildJsonObject {
            putJsonArray("themes") {
                add(buildJsonObject { put("id", "8"); put("label", "Scolarité") })
                add(buildJsonObject { put("id", "9"); put("label", "Suivi pédagogique") })
                add(buildJsonObject { put("id", "13"); put("label", "Service Transport") })
            }
        }
        val themes = Normalizers.themes(rep)
        assertEquals(3, themes.size)
        assertEquals("8", themes[0].id)
        assertEquals("Scolarité", themes[0].label)
        assertEquals("Service Transport", themes[2].label)
    }

    @Test
    fun `themes absents ou mal formés — liste vide sans planter`() {
        assertTrue(Normalizers.themes(buildJsonObject { }).isEmpty())
        val cassé = buildJsonObject {
            putJsonArray("themes") {
                add(buildJsonObject { put("label", "sans id") })
                add(buildJsonObject { put("id", "9") })
            }
        }
        assertEquals(1, Normalizers.themes(cassé).size)
        assertEquals("9", Normalizers.themes(cassé)[0].id)
    }

    @Test
    fun `le fil porte son thème pour la réponse`() {
        val fil = buildJsonObject {
            put("id", "c1")
            put("sujet", "Transport")
            put("theme", "13")
            putJsonArray("conversation") {
                add(buildJsonObject {
                    put("message", "Bonjour")
                    put("datetime", "2026-09-09 09:32:05")
                    put("is_self", true)
                    put("message_id", "m1")
                })
            }
        }
        val conversation = Normalizers.conversation(fil)!!
        assertEquals("13", conversation.theme)
        assertEquals("Transport", conversation.sujet)
    }

    @Test
    fun `le message renvoyé par le POST se normalise comme un message`() {
        // La réponse du bundle : conversation[index] = _.message — même forme
        // qu'un item de conversation[].
        val réponse = buildJsonObject {
            putJsonObject("message") {
                put("message", "Merci beaucoup")
                put("datetime", "2026-09-19 14:02:11")
                put("is_self", true)
                put("message_id", "n1")
                put("vu_le", "")
            }
        }
        val servi = (réponse["message"] as? kotlinx.serialization.json.JsonObject)?.let(Normalizers::message)
        assertNotNull(servi)
        assertEquals("Merci beaucoup", servi!!.texte)
        // is_self = true : envoyé par le parent, pas par l'administration.
        org.junit.Assert.assertFalse(servi.deLAdmin)
        assertNull(servi.vuLe)
    }

    @Test
    fun `envoi optimiste change de statut sans muter le message initial`() {
        val envoi = MessageEnvoi(texte = "Bonjour")
        assertEquals(MessageEnvoi.Statut.EnCours, envoi.statut)
        val échoué = envoi.copy(statut = MessageEnvoi.Statut.Échec)
        assertEquals(MessageEnvoi.Statut.Échec, échoué.statut)
        assertEquals(MessageEnvoi.Statut.EnCours, envoi.statut)
    }

    @Test
    fun `mimes usuels des pièces jointes`() {
        assertEquals("application/pdf", mimeDe("pdf"))
        assertEquals("image/jpeg", mimeDe("JPG"))
        assertEquals("audio/mp4", mimeDe("m4a"))
        assertEquals("application/octet-stream", mimeDe("xyz"))
    }
}
