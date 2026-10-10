package school.greenwood.plus

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import school.greenwood.plus.data.session.Chiffreur
import school.greenwood.plus.data.cache.EntréeSnapshot
import school.greenwood.plus.data.cache.SnapshotRegistre
import school.greenwood.plus.data.cache.SnapshotsRegistre
import school.greenwood.plus.data.cache.VERSION_SNAPSHOT
import school.greenwood.plus.data.repo.EntreeRegistre
import school.greenwood.plus.data.repo.RegistreDuJour
import school.greenwood.plus.data.session.SessionSecrets
import school.greenwood.plus.model.Absence
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Message
import school.greenwood.plus.model.Post
import java.io.File
import java.time.LocalDate

/*
 * L'instantané disque du registre (issue #145) : le dernier écran affiché
 * revient au démarrage, chiffré et isolé par session. Le test verrouille le
 * aller-retour sans perte, l'isolation entre comptes, la tolérance aux
 * fichiers illisibles et les deux garde-fous de configuration (hors
 * sauvegardes, purge à la connexion comme à la déconnexion).
 *
 * Chiffreur factice : mêmes règles que `SessionSecrets` en production,
 * sans Keystore (le Keystore réel est couvert par `SessionSecretsKeystoreTest`,
 * instrumenté).
 */
class SnapshotRegistreTest {

    private object ChiffreurFactice : Chiffreur {
        override fun chiffrer(clair: ByteArray) = ByteArray(12) + clair
        override fun déchiffrer(payload: ByteArray): ByteArray {
            require(payload.size > 12) { "payload trop court" }
            return payload.copyOfRange(12, payload.size)
        }
    }

    private lateinit var dossier: File
    private lateinit var instantanés: SnapshotsRegistre

    @Before
    fun setUp() {
        dossier = File(
            System.getProperty("java.io.tmpdir"),
            "gws-snapshot-test-${System.nanoTime()}",
        )
        instantanés = SnapshotsRegistre(
            fichier = File(dossier, "registre-instantane.enc"),
            secrets = SessionSecrets(ChiffreurFactice),
        )
    }

    @After
    fun tearDown() {
        dossier.deleteRecursively()
    }

    private val clé = "parent-1/enfant-1"
    private val jour = LocalDate.of(2026, 10, 9)

    private fun registreComplet(): Pair<RegistreDuJour, Post> {
        val devoir = Devoir(
            id = "d1",
            title = "Dictée",
            matiere = "Français",
            categorie = "Travail écrit",
            enseignant = "Mme Dupont",
            description = "<p>Page 42</p>",
            dateRemise = jour.plusDays(1),
            publication = jour.atTime(8, 0),
            attachments = listOf(Attachment(name = "consigne.pdf", url = "https://x/consigne")),
        )
        val post = Post(
            id = "p1",
            title = "Sortie scolaire",
            categorie = "Vie scolaire",
            date = jour.atTime(9, 0),
            intro = "Départ à 8 h 30",
            image = "https://x/photo",
            auteur = "Direction",
        )
        val absence = Absence(id = "a1", motif = "Maladie", du = jour, au = jour, justifiee = true)
        val conversation = Conversation(
            id = "c1",
            sujet = "Cantine",
            theme = "scolarite",
            messages = listOf(
                Message(id = "m1", deLAdmin = true, texte = "Menu du jour", date = jour.atTime(11, 0)),
                Message(id = "m2", deLAdmin = false, texte = "Merci", date = jour.atTime(12, 30)),
            ),
        )
        val registre = RegistreDuJour(
            date = jour,
            ceSoir = listOf(devoir),
            horizonCeSoir = jour.plusDays(1),
            entrees = listOf(
                EntreeRegistre.Actualite(post),
                EntreeRegistre.DevoirDonné(devoir),
                EntreeRegistre.AbsenceNotée(absence),
                EntreeRegistre.MessageReçu(conversation),
            ),
        )
        return registre to post
    }

    @Test
    fun `aller-retour complet — quatre types d'entrées, carte ce soir, dernière actualité`() {
        val (registre, post) = registreComplet()
        val écrit = SnapshotRegistre.de(registre, clé, post, époque = 1_700_000_000_000)
        val relu = écrit.versRegistre()

        assertEquals(registre.date, relu.date)
        assertEquals(registre.horizonCeSoir, relu.horizonCeSoir)
        assertEquals(registre.ceSoir, relu.ceSoir)
        assertEquals(registre.entrees.filterIsInstance<EntreeRegistre.Actualite>(), relu.entrees.filterIsInstance<EntreeRegistre.Actualite>())
        assertEquals(registre.entrees.filterIsInstance<EntreeRegistre.DevoirDonné>(), relu.entrees.filterIsInstance<EntreeRegistre.DevoirDonné>())
        assertEquals(registre.entrees.filterIsInstance<EntreeRegistre.AbsenceNotée>(), relu.entrees.filterIsInstance<EntreeRegistre.AbsenceNotée>())
        assertEquals(registre.entrees.size, relu.entrees.size)
        assertEquals(1_700_000_000_000, écrit.époque)
        assertEquals(post, écrit.versDerniereActualite())
        // L'ordre d'affichage (tri chronologique) est conservé tel quel.
        assertEquals(
            registre.entrees.map { it.id },
            relu.entrees.map { it.id },
        )
    }

    @Test
    fun `conversation — sujet, date du dernier message et aperçu conservés`() {
        val (registre, _) = registreComplet()
        val conversationOriginale =
            (registre.entrees[3] as EntreeRegistre.MessageReçu).conversation
        val relu = SnapshotRegistre.de(registre, clé, null).versRegistre()
        val conversation = (relu.entrees[3] as EntreeRegistre.MessageReçu).conversation

        assertEquals(conversationOriginale.id, conversation.id)
        assertEquals(conversationOriginale.sujet, conversation.sujet)
        assertEquals(conversationOriginale.theme, conversation.theme)
        assertEquals(conversationOriginale.dernierDate, conversation.dernierDate)
        assertEquals("Merci", conversation.messages.last().texte)
        // Le fil entier ne dort pas sur disque : seul l'aperçu est gardé.
        assertEquals(1, conversation.messages.size)
    }

    @Test
    fun `un autre compte ou un autre enfant ne lit rien`() {
        runBlocking {
            instantanés.écrire(SnapshotRegistre.de(registreComplet().first, clé, null))
            assertNotNull(instantanés.lire(clé))
            assertNull("autre enfant du même parent", instantanés.lire("parent-1/enfant-2"))
            assertNull("autre parent", instantanés.lire("parent-2/enfant-1"))
        }
    }

    @Test
    fun `fichier corrompu ou illisible rend null, jamais une exception`() {
        runBlocking {
            File(dossier, "registre-instantane.enc").apply {
                parentFile?.mkdirs()
                writeText("ceci n'est pas du JSON chiffré")
            }
            assertNull(instantanés.lire(clé))
        }
    }

    @Test
    fun `chiffré illisible (clé Keystore perdue) rend null`() {
        runBlocking {
            instantanés.écrire(SnapshotRegistre.de(registreComplet().first, clé, null))
            // Même fichier, chiffreur incapable de déchiffrer : la lecture
            // échoue proprement au lieu de projeter l'erreur.
            val illisible = SnapshotsRegistre(
                fichier = File(dossier, "registre-instantane.enc"),
                secrets = SessionSecrets(object : Chiffreur {
                    override fun chiffrer(clair: ByteArray) = ByteArray(12) + clair
                    override fun déchiffrer(payload: ByteArray) = ByteArray(0)
                }),
            )
            assertNull(illisible.lire(clé))
        }
    }

    @Test
    fun `format inconnu (version future) rend null — l'ancien fichier est ignoré`() {
        runBlocking {
            val ancien = SnapshotRegistre(
                version = VERSION_SNAPSHOT + 1,
                clé = clé,
                époque = 0,
                date = jour.toString(),
                horizonCeSoir = jour.plusDays(1).toString(),
                ceSoir = emptyList(),
                entrees = emptyList(),
            )
            instantanés.écrire(ancien)
            assertNull(instantanés.lire(clé))
        }
    }

    @Test
    fun `vider efface l'instantané — déconnexion et changement de compte`() {
        runBlocking {
            instantanés.écrire(SnapshotRegistre.de(registreComplet().first, clé, null))
            assertNotNull(instantanés.lire(clé))
            instantanés.vider()
            assertNull(instantanés.lire(clé))
            assertTrue("fichier supprimé", !File(dossier, "registre-instantane.enc").exists())
        }
    }

    @Test
    fun `réécriture — le dernier résultat frais remplace l'ancien`() {
        runBlocking {
            val (premier, _) = registreComplet()
            instantanés.écrire(SnapshotRegistre.de(premier, clé, null))
            val second = premier.copy(
                entrees = emptyList(),
                ceSoir = emptyList(),
            )
            instantanés.écrire(SnapshotRegistre.de(second, clé, null))
            val relu = instantanés.lire(clé)
            assertNotNull(relu)
            assertTrue(relu!!.entrees.isEmpty())
            assertTrue(relu.ceSoir.isEmpty())
        }
    }

    @Test
    fun `garde-fou — fichier hors sauvegardes Android, purge à la connexion et à la déconnexion`() {
        val source = File(
            "src/main/java/school/greenwood/plus/data/cache/SnapshotRegistre.kt",
        ).readText(Charsets.UTF_8)
        assertTrue(
            "l'instantané doit vivre dans noBackupFilesDir (jamais sauvegardé ni transféré)",
            source.contains("noBackupFilesDir"),
        )
        assertFalse(
            "aucune écriture dans filesDir (inclus dans les sauvegardes)",
            source.contains("context.filesDir"),
        )

        val auth = File(
            "src/main/java/school/greenwood/plus/data/repo/AuthRepository.kt",
        ).readText(Charsets.UTF_8)
        val purges = Regex("purgeSnapshots\\(\\)").findAll(auth).count()
        assertTrue(
            "purge à la connexion ET à la déconnexion (2 occurrences), trouvées : $purges",
            purges >= 2,
        )
        assertFalse(
            "jamais de jeton ni d'identifiant de session dans l'instantané",
            source.contains("keyToken"),
        )
        // L'entrée scellée est polymorphe : chaque type d'entrée survit au JSON.
        assertTrue(source.contains("@SerialName"))
        val (registre, _) = registreComplet()
        val json = Json.encodeToString(
            SnapshotRegistre.serializer(),
            SnapshotRegistre.de(registre, clé, null),
        )
        assertTrue(json.contains(""""actualite""""))
        assertTrue(json.contains(""""devoir""""))
        assertTrue(json.contains(""""absence""""))
        assertTrue(json.contains(""""message""""))
        assertNotNull(EntréeSnapshot.de(registre.entrees.first()))
    }
}
