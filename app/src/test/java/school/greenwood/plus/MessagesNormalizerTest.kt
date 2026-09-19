package school.greenwood.plus

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import school.greenwood.plus.data.repo.Normalizers
import java.time.LocalDateTime

/*
 * Normalisation des messages : accusés de lecture (`vu_le`), message vocal
 * (`audio`, forme jamais observée non-nulle) et dédoublonnage des doubles
 * envois du serveur (même texte, `message_id` distincts, une seconde
 * d'écart — ENDPOINT-MAP quirk 7).
 */
class MessagesNormalizerTest {

    private fun messageJson(
        texte: String,
        datetime: String,
        isSelf: Boolean = true,
        vuLe: String? = null,
        audio: JsonElement? = null,
        messageId: String = "m1",
    ) = buildJsonObject {
        put("message", texte)
        put("datetime", datetime)
        put("is_self", isSelf)
        put("message_id", messageId)
        vuLe?.let { put("vu_le", it) }
        audio?.let { put("audio", it) }
    }

    @Test
    fun `double envoi du serveur dédoublonné`() {
        val a = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:05", messageId = "m1"))!!
        val b = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:06", messageId = "m2"))!!
        assertEquals(1, Normalizers.sansDoublonsConsécutifs(listOf(a, b)).size)
    }

    @Test
    fun `rappel légitime quelques secondes plus tard conservé`() {
        val a = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:05", messageId = "m1"))!!
        val b = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:11", messageId = "m2"))!!
        assertEquals(2, Normalizers.sansDoublonsConsécutifs(listOf(a, b)).size)
    }

    @Test
    fun `textes différents jamais dédoublonnés`() {
        val a = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:05", messageId = "m1"))!!
        val b = Normalizers.message(messageJson("Merci", "2026-09-09 09:32:06", messageId = "m2"))!!
        assertEquals(2, Normalizers.sansDoublonsConsécutifs(listOf(a, b)).size)
    }

    @Test
    fun `directions différentes jamais dédoublonnées`() {
        val parent = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:05", isSelf = true, messageId = "m1"))!!
        val admin = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:06", isSelf = false, messageId = "m2"))!!
        assertEquals(2, Normalizers.sansDoublonsConsécutifs(listOf(parent, admin)).size)
    }

    @Test
    fun `accusé de lecture parsé`() {
        val m = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:05", vuLe = "2026-09-10 09:51:41"))!!
        assertEquals(java.time.LocalDateTime.of(2026, 9, 10, 9, 51, 41), m.vuLe)
    }

    @Test
    fun `sans accusé de lecture reste nul`() {
        val m = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:05", vuLe = null))!!
        assertNull(m.vuLe)
    }

    @Test
    fun `audio nul dans le sondage reste nul`() {
        val m = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:05", audio = null))!!
        assertNull(m.audio)
    }

    @Test
    fun `audio lien direct accepté`() {
        val url = "https://media.boti.education/view/exemple/audio.mp3"
        val m = Normalizers.message(messageJson("Bonjour", "2026-09-09 09:32:05", audio = JsonPrimitive(url)))!!
        assertEquals(url, m.audio?.url)
        assertEquals("audio.mp3", m.audio?.name)
    }

    @Test
    fun `audio objet accepté`() {
        val url = "https://media.boti.education/view/exemple/audio.mp3"
        val m = Normalizers.message(
            messageJson(
                "Bonjour",
                "2026-09-09 09:32:05",
                audio = buildJsonObject {
                    put("link", url)
                    put("text", "Enregistrement audio")
                },
            ),
        )!!
        assertEquals(url, m.audio?.url)
        assertEquals("Enregistrement audio", m.audio?.name)
    }

    @Test
    fun `conversation dédoublonnée à la normalisation`() {
        val conversation = buildJsonObject {
            put("id", "c1")
            put("sujet", "Administration")
            put("conversation", kotlinx.serialization.json.buildJsonArray {
                add(
                    buildJsonObject {
                        put("message", "Bonjour")
                        put("datetime", "2026-09-09 09:32:05")
                        put("is_self", true)
                        put("message_id", "m1")
                    },
                )
                add(
                    buildJsonObject {
                        put("message", "Bonjour")
                        put("datetime", "2026-09-09 09:32:06")
                        put("is_self", true)
                        put("message_id", "m2")
                    },
                )
            })
        }
        val c = Normalizers.conversation(conversation)!!
        assertEquals(1, c.messages.size)
    }
}