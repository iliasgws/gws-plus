package school.greenwood.plus

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.api.BotiErreur
import school.greenwood.plus.data.repo.EntreeRegistre
import school.greenwood.plus.data.repo.RegistreDuJour
import school.greenwood.plus.data.repo.chargerRegistre
import school.greenwood.plus.model.Absence
import school.greenwood.plus.model.BilanAbsences
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Message
import school.greenwood.plus.model.Post
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/*
 * Le registre charge ses quatre sources ensemble (issue #145) : le test prouve
 * le chevauchement, mesure le gain contre la boucle séquentielle qu'il remplace,
 * et verrouille les deux garanties comportementales — un échec isole sa
 * section, une annulation n'est jamais dévorée par la tolérance d'échec.
 */
class RegistreParallèleTest {

    private val aujourdhui: LocalDate = LocalDate.of(2026, 10, 7)
    private val latenceMs = 120L

    /** Nombre maximal de requêtes en vol — 1 = séquentiel, 4 = simultané. */
    private inner class Sondes {
        private val enCours = AtomicInteger(0)
        val maximum = AtomicInteger(0)

        suspend fun <T> mesurer(bloc: suspend () -> T): T {
            val actuel = enCours.incrementAndGet()
            maximum.updateAndGet { maxOf(it, actuel) }
            return try {
                delay(latenceMs)
                bloc()
            } finally {
                enCours.decrementAndGet()
            }
        }
    }

    private fun devoirDuJour() = Devoir(
        id = "d1",
        title = "Dictée",
        matiere = "Français",
        dateRemise = aujourdhui.plusDays(1),
        publication = aujourdhui.atTime(8, 0),
    )

    private fun postDuJour() = Post(
        id = "p1",
        title = "Sortie scolaire",
        date = aujourdhui.atTime(9, 0),
    )

    @Test
    fun `les quatre sources tournent en même temps`() {
        val sondes = Sondes()
        runBlocking {
            chargerRegistre(
                aujourdhui = aujourdhui,
                devoirs = { sondes.mesurer { listOf(devoirDuJour()) } },
                posts = { sondes.mesurer { listOf(postDuJour()) } },
                absences = { sondes.mesurer { BilanAbsences() } },
                messages = { sondes.mesurer { emptyList() } },
            )
        }
        assertEquals("quatre requêtes en vol, pas une de moins", 4, sondes.maximum.get())
    }

    @Test
    fun `la boucle séquentielle qu'elle remplace n'en mène qu'une à la fois`() {
        val sondes = Sondes()
        val début = System.nanoTime()
        runBlocking {
            // Le chargement d'avant l'issue #145, tel quel : quatre appels
            // l'un après l'autre. Référence mesurée, même machine, même latence.
            sondes.mesurer { listOf(devoirDuJour()) }
            sondes.mesurer { listOf(postDuJour()) }
            sondes.mesurer { BilanAbsences() }
            sondes.mesurer { emptyList<Conversation>() }
        }
        val écouléMs = (System.nanoTime() - début) / 1_000_000
        assertEquals("une seule requête à la fois", 1, sondes.maximum.get())
        assertTrue(
            "quatre latences de $latenceMs ms en série, obtenu $écouléMs ms",
            écouléMs >= 4 * latenceMs - 40,
        )
    }

    @Test
    fun `le chargement parallèle dure le temps d'une seule source, pas de quatre`() {
        val sondes = Sondes()
        val début = System.nanoTime()
        val jour = runBlocking {
            chargerRegistre(
                aujourdhui = aujourdhui,
                devoirs = { sondes.mesurer { listOf(devoirDuJour()) } },
                posts = { sondes.mesurer { listOf(postDuJour()) } },
                absences = { sondes.mesurer { BilanAbsences() } },
                messages = { sondes.mesurer { emptyList() } },
            )
        }
        val parallèleMs = (System.nanoTime() - début) / 1_000_000

        // Référence séquentielle, même machine, mêmes sources.
        val séquentiel = Sondes()
        val débutSeq = System.nanoTime()
        runBlocking {
            séquentiel.mesurer { listOf(devoirDuJour()) }
            séquentiel.mesurer { listOf(postDuJour()) }
            séquentiel.mesurer { BilanAbsences() }
            séquentiel.mesurer { emptyList<Conversation>() }
        }
        val séquentielMs = (System.nanoTime() - débutSeq) / 1_000_000

        println(
            "registre : séquentiel $séquentielMs ms — parallèle $parallèleMs ms " +
                "(latence simulée $latenceMs ms par source)",
        )
        assertEquals("quatre requêtes en vol", 4, sondes.maximum.get())
        assertTrue(
            "parallèle ($parallèleMs ms) doit rester sous la somme séquentielle ($séquentielMs ms)",
            parallèleMs < séquentielMs,
        )
        assertTrue(
            "parallèle ($parallèleMs ms) ≈ une latence ($latenceMs ms), marge large",
            parallèleMs <= 4 * latenceMs,
        )
        assertEquals(listOf("post-p1", "devoir-d1"), jour.entrees.map { it.id })
    }

    @Test
    fun `un endpoint qui tombe laisse les autres sections s'afficher`() {
        val jour = runBlocking {
            chargerRegistre(
                aujourdhui = aujourdhui,
                devoirs = { listOf(devoirDuJour()) },
                posts = { error("panne serveur sur nouveautes") },
                absences = {
                    BilanAbsences(justifiees = listOf(Absence("a1", "Maladie", aujourdhui, aujourdhui, true)))
                },
                messages = {
                    listOf(
                        Conversation(
                            id = "c1",
                            sujet = "Cantine",
                            messages = listOf(
                                Message(
                                    id = "m1",
                                    deLAdmin = true,
                                    texte = "Menu du 7 octobre",
                                    date = aujourdhui.atTime(12, 0),
                                ),
                            ),
                        ),
                    )
                },
            )
        }
        // La section en échec est vide ; les trois autres sont là.
        assertTrue(jour.entrees.none { it.id == "post-p1" })
        assertEquals(
            setOf("devoir-d1", "absence-a1", "message-c1"),
            jour.entrees.map { it.id }.toSet(),
        )
        assertEquals(listOf("d1"), jour.ceSoir.map { it.id })
        assertEquals(aujourdhui, jour.date)
    }

    @Test
    fun `une annulation traverse la tolérance d'échec`() {
        val job = runBlocking {
            val lancement = launch(Dispatchers.Default) {
                chargerRegistre(
                    aujourdhui = aujourdhui,
                    devoirs = { awaitCancellation() },
                    posts = { listOf(postDuJour()) },
                    absences = { BilanAbsences() },
                    messages = { emptyList() },
                )
            }
            delay(200)
            lancement.cancel()
            lancement.join()
            lancement
        }
        assertTrue("l'annulation du chargement n'est pas convertie en registre vide", job.isCancelled)
    }

    @Test
    fun `une source annulée fait échouer le chargement, pas un registre à moitié vide`() {
        val issue = runCatching {
            runBlocking {
                chargerRegistre(
                    aujourdhui = aujourdhui,
                    devoirs = { throw CancellationException("session remplacée") },
                    posts = { listOf(postDuJour()) },
                    absences = { BilanAbsences() },
                    messages = { emptyList() },
                )
            }
        }
        assertTrue("une annulation remonte, jamais avalée", issue.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `les quatre sources qui tombent ensemble annoncent la panne, pas un registre vide`() {
        // Hors ligne : les quatre GET échouent ensemble. Avant le correctif,
        // chacune rendait son défaut, le registre « réussissait » vide et
        // l'écran n'affichait aucun avertissement.
        val issue = runCatching {
            runBlocking {
                chargerRegistre(
                    aujourdhui = aujourdhui,
                    devoirs = { error("hors ligne") },
                    posts = { error("hors ligne") },
                    absences = { error("hors ligne") },
                    messages = { error("hors ligne") },
                )
            }
        }
        val erreur = issue.exceptionOrNull()
        assertTrue("l'échec total remonte, jamais un registre vide", erreur is BotiErreur)
        assertTrue(
            "le message annonce la connexion, obtenu : ${(erreur as BotiErreur).messageUtilisateur}",
            erreur.messageUtilisateur.contains("Connexion impossible"),
        )
    }

    @Test
    fun `une section en échec retrouve le contenu déjà connu au lieu de se vider`() {
        val connu = RegistreDuJour(
            date = aujourdhui,
            ceSoir = listOf(devoirDuJour()),
            horizonCeSoir = aujourdhui.plusDays(5),
            entrees = listOf(
                EntreeRegistre.Actualite(postDuJour()),
                EntreeRegistre.DevoirDonné(devoirDuJour()),
            ),
        )
        var signalés = emptyList<String>()
        val jour = runBlocking {
            chargerRegistre(
                aujourdhui = aujourdhui,
                devoirs = { error("panne serveur sur devoirs") },
                posts = { listOf(postDuJour()) },
                absences = { BilanAbsences() },
                messages = { emptyList() },
                connu = connu,
                surÉchecs = { signalés = it },
            )
        }
        assertEquals(listOf("devoirs"), signalés)
        assertEquals("carte « Ce soir » héritée", listOf("d1"), jour.ceSoir.map { it.id })
        assertTrue("devoir connu conservé", jour.entrees.any { it.id == "devoir-d1" })
        assertTrue("actualité fraîche, pas en double", jour.entrees.count { it.id == "post-p1" } == 1)
    }

    @Test
    fun `tout réussit, rien n'est signalé à l'écran`() {
        var appelé = false
        runBlocking {
            chargerRegistre(
                aujourdhui = aujourdhui,
                devoirs = { listOf(devoirDuJour()) },
                posts = { listOf(postDuJour()) },
                absences = { BilanAbsences() },
                messages = { emptyList() },
                surÉchecs = { appelé = true },
            )
        }
        assertTrue("aucun échec, aucun avertissement", !appelé)
    }
}
