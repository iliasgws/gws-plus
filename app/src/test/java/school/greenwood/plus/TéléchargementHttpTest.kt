package school.greenwood.plus

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import school.greenwood.plus.util.Fichiers
import java.util.concurrent.TimeUnit

/*
 * Téléchargement privé de bout en bout (issue #141) : serveur local
 * MockWebServer, espace de fichiers factice. Vérifie le plafond précoce
 * (Content-Length), le rejet en flux, la survie du fichier en cache après
 * un remplacement raté, le nettoyage des temporaires et l'annulation.
 */
class TéléchargementHttpTest {

    private lateinit var serveur: MockWebServer
    private lateinit var répertoire: File

    /** Contexte minimal : seul filesDir est touché par Fichiers.télécharger. */
    private val contexte: Context = object : ContextWrapper(null) {
        override fun getFilesDir(): File = répertoire
    }

    @Before
    fun avant() {
        serveur = MockWebServer()
        serveur.start()
        répertoire = kotlin.io.path.createTempDirectory("gws-test-").toFile()
    }

    @After
    fun après() {
        serveur.shutdown()
        répertoire.deleteRecursively()
    }

    private fun documents(): File = File(répertoire, "documents")

    /** Aucun reliquat de temporaire (« .part ») ne doit survivre. */
    private fun assertSansTemporaire() {
        val reliquats = documents().listFiles()?.filter { it.name.endsWith(".part") } ?: emptyList()
        assertTrue("temporaires laissés : ${reliquats.joinToString { it.name }}", reliquats.isEmpty())
    }

    @Test
    fun `telechargement reussi - fichier complet et aucun temporaire`() = runBlocking {
        val corps = "Bonjour l'école".repeat(100)
        serveur.enqueue(MockResponse().setBody(corps))

        val fichier = Fichiers.télécharger(contexte, serveur.url("/doc.pdf").toString(), "devoir.pdf")

        assertTrue(fichier.exists())
        assertEquals(corps, fichier.readText())
        assertSansTemporaire()
        assertEquals(1, serveur.requestCount)
    }

    @Test
    fun `deuxieme acces - reutilise le cache sans reseau`() = runBlocking {
        serveur.enqueue(MockResponse().setBody("contenu"))
        val url = serveur.url("/doc.pdf").toString()

        val premier = Fichiers.télécharger(contexte, url, "devoir.pdf")
        val second = Fichiers.télécharger(contexte, url, "devoir.pdf")

        assertEquals(premier, second)
        assertEquals(1, serveur.requestCount)
    }

    @Test
    fun `content-length superieur au plafond - rejet precoce sans ecrire`() = runBlocking {
        // setBody fixe Content-Length à la taille réelle ; setHeader ensuite
        // le remplace : le serveur ment, comme un serveur compromis le ferait.
        serveur.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("x")
                .setHeader("Content-Length", Fichiers.LIMITE_DOCUMENT + 1),
        )

        val échec = runCatching {
            Fichiers.télécharger(contexte, serveur.url("/gros.pdf").toString(), "gros.pdf")
        }
        assertTrue(échec.isFailure)
        assertTrue(échec.exceptionOrNull()!!.message!!.contains("volumineux"))
        assertFalse(File(documents(), "gros.pdf").exists())
        assertSansTemporaire()
    }

    @Test
    fun `remplacement force trop gros - l ancien fichier complet survit`() = runBlocking {
        // Un premier téléchargement réussi met un fichier valide en cache.
        serveur.enqueue(MockResponse().setBody("ancien contenu"))
        val url = serveur.url("/doc.pdf").toString()
        val fichier = Fichiers.télécharger(contexte, url, "devoir.pdf")
        assertEquals("ancien contenu", fichier.readText())

        // Le serveur annonce désormais un corps au-delà du plafond.
        serveur.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("x")
                .setHeader("Content-Length", Fichiers.LIMITE_DOCUMENT + 1),
        )
        val échec = runCatching { Fichiers.télécharger(contexte, url, "devoir.pdf", forcer = true) }
        assertTrue(échec.isFailure)
        // Le cache valide n'a pas été entamé ni remplacé.
        assertEquals("ancien contenu", fichier.readText())
        assertSansTemporaire()
    }

    @Test
    fun `erreur http - aucun fichier publie ni temporaire`() = runBlocking {
        serveur.enqueue(MockResponse().setResponseCode(404).setBody("pas là"))

        val échec = runCatching {
            Fichiers.télécharger(contexte, serveur.url("/absent.pdf").toString(), "absent.pdf")
        }
        assertTrue(échec.isFailure)
        assertTrue(échec.exceptionOrNull()!!.message!!.contains("404"))
        assertEquals(0, documents().listFiles()?.size ?: 0)
        assertSansTemporaire()
    }

    @Test
    fun `connexion coupee en plein flux - aucun fichier publie`() = runBlocking {
        // Le serveur envoie des en-têtes puis coupe : ni Content-Length fiable
        // ni fin de corps — le lecteur lève une erreur IO, le temporaire part.
        serveur.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                .setBody(Buffer().write(ByteArray(64 * 1024))),
        )

        val échec = runCatching {
            Fichiers.télécharger(contexte, serveur.url("/coupé.pdf").toString(), "coupé.pdf")
        }
        assertTrue(échec.isFailure)
        assertFalse(File(documents(), "coupé.pdf").exists())
        assertSansTemporaire()
    }

    @Test
    fun `annulation en cours - le temporaire est nettoye`() = runBlocking {
        // Corps lent : le téléchargement est en cours quand on annule.
        val corps = Buffer().write(ByteArray(256 * 1024))
        serveur.enqueue(
            MockResponse()
                .setBody(corps)
                .throttleBody(1024, 50, TimeUnit.MILLISECONDS),
        )

        val url = serveur.url("/lent.pdf").toString()
        val job = launch { Fichiers.télécharger(contexte, url, "lent.pdf") }

        // Attends que le temporaire apparaisse (le flux a démarré).
        val dossier = documents()
        val attendu = System.currentTimeMillis() + 5_000
        while (dossier.listFiles()?.none { it.name.endsWith(".part") } != false &&
            System.currentTimeMillis() < attendu
        ) {
            delay(20)
        }
        job.cancel()
        job.join()

        assertSansTemporaire()
        assertFalse(File(dossier, "lent.pdf").exists())
    }

    @Test
    fun `un apk accepte le plafond large - un document non`() {
        assertEquals(Fichiers.LIMITE_APK, Fichiers.limitePourNom("maj.apk"))
        assertEquals(Fichiers.LIMITE_DOCUMENT, Fichiers.limitePourNom("bulletin.pdf"))
        assertNotEquals(Fichiers.LIMITE_APK, Fichiers.LIMITE_DOCUMENT)
    }
}
