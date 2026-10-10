package school.greenwood.plus

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import school.greenwood.plus.data.cache.CacheDisque
import school.greenwood.plus.data.session.Chiffreur
import school.greenwood.plus.data.session.SessionSecrets
import java.io.File

/*
 * Le cache de données sur disque (hors-ligne) : aller-retour sans perte,
 * isolation par session, tolérance aux fichiers illisibles, purge, et les
 * deux garde-fous de configuration (hors sauvegardes, rien de lisible en
 * clair). Chiffreur factice : mêmes règles que `SessionSecrets` en
 * production, sans Keystore.
 */
class CacheDisqueTest {

    private object ChiffreurFactice : Chiffreur {
        override fun chiffrer(clair: ByteArray) = ByteArray(12) + clair
        override fun déchiffrer(payload: ByteArray): ByteArray {
            require(payload.size > 12) { "payload trop court" }
            return payload.copyOfRange(12, payload.size)
        }
    }

    private lateinit var dossier: File
    private lateinit var cache: CacheDisque

    @Before
    fun setUp() {
        dossier = File(
            System.getProperty("java.io.tmpdir"),
            "gws-cache-disque-test-${System.nanoTime()}",
        )
        cache = CacheDisque(dossier, SessionSecrets(ChiffreurFactice))
    }

    @After
    fun tearDown() {
        dossier.deleteRecursively()
    }

    private fun échantillon() = buildJsonObject {
        put("title", JsonPrimitive("Cahier de liaison — séance du 10 octobre"))
        put("count", JsonPrimitive(3))
    }

    @Test
    fun `aller-retour - le JSON revient identique`() = runBlocking {
        val données = échantillon()
        cache.écrire("cours", "u1/e1", données)
        assertEquals(données, cache.lire("cours", "u1/e1"))
    }

    @Test
    fun `nom absent - rien a été écrit`() = runBlocking {
        cache.écrire("cours", "u1/e1", échantillon())
        assertNull(cache.lire("devoirs", "u1/e1"))
    }

    @Test
    fun `autre session - la clé du payload ne correspond pas`() = runBlocking {
        cache.écrire("cours", "u1/e1", échantillon())
        assertNull(cache.lire("cours", "u2/e2"))
    }

    @Test
    fun `redémarrage - une nouvelle instance relit le même dossier`() = runBlocking {
        cache.écrire("messages", "u1/e1", échantillon())
        val relue = CacheDisque(dossier, SessionSecrets(ChiffreurFactice))
        assertEquals(échantillon(), relue.lire("messages", "u1/e1"))
    }

    @Test
    fun `fichier corrompu - rend null sans exception`() = runBlocking {
        cache.écrire("cours", "u1/e1", échantillon())
        val cible = dossier.listFiles()?.singleOrNull { it.name.endsWith(".enc") }
        assertNotNull(cible)
        cible!!.writeText("pas-un-secret-@#%")
        assertNull(cache.lire("cours", "u1/e1"))
    }

    @Test
    fun `videur - plus rien à relire`() = runBlocking {
        cache.écrire("cours", "u1/e1", échantillon())
        cache.vider()
        assertNull(cache.lire("cours", "u1/e1"))
        assertTrue(dossier.listFiles().isNullOrEmpty())
    }

    @Test
    fun `clair - jamais lisible dans le fichier`() = runBlocking {
        cache.écrire("cours", "u1/e1", échantillon())
        val octets = dossier.listFiles()!!.single().readText()
        assertFalse(octets.contains("Cahier de liaison"))
        assertFalse(octets.contains("u1/e1"))
        assertNotEquals(échantillon().toString(), octets)
    }

    @Test
    fun `nom avec caractères de paramètres - écrit et relu quand même`() = runBlocking {
        val nom = "shop-rubrique d'essai/été #2"
        cache.écrire(nom, "u1/e1", échantillon())
        assertEquals(échantillon(), cache.lire(nom, "u1/e1"))
    }

    @Test
    fun `collision de nom de fichier - le payload tranche`() = runBlocking {
        // Deux noms qui se sanitizent pareil (même suffixe de nom de fichier) :
        // l'enveloppe porte le nom logique, la mauvaise entrée rend null.
        val a = "shop-rubrique 1"
        val b = "shop-rubrique/1"
        cache.écrire(a, "u1/e1", JsonPrimitive("pour-a"))
        cache.écrire(b, "u1/e1", JsonPrimitive("pour-b"))
        assertEquals(JsonPrimitive("pour-b"), cache.lire(b, "u1/e1"))
        // a est peut-être écrasé par b (collision acceptée) : jamais de
        // réponse fausse — soit a, soit null, jamais la donnée de b.
        val reluA = cache.lire(a, "u1/e1")
        assertTrue(reluA == null || reluA == JsonPrimitive("pour-a"))
    }

    @Test
    fun `garde-fou - noBackupFilesDir hors des sauvegardes Android`() {
        val source = File(
            "src/main/java/school/greenwood/plus/data/cache/CacheDisque.kt",
        ).readText(Charsets.UTF_8)
        assertTrue(source.contains("noBackupFilesDir"))
    }
}
