package school.greenwood.plus

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.api.CommunApi
import school.greenwood.plus.data.api.JetonRévoqué
import school.greenwood.plus.data.api.Lecture
import school.greenwood.plus.data.repo.CommunauteRepository
import school.greenwood.plus.data.repo.NoticeRequise
import school.greenwood.plus.data.session.CompteCommunautaire
import kotlin.reflect.KClass

/*
 * Le cycle de vie du compte (APP.md §2) : un seul `POST /compte` par
 * installation et par écriture, la notice AVANT le contenu, réaffichée quand
 * la version du serveur change ou après un 401 — et jamais de boucle de
 * création de compte.
 */

/** Compte en mémoire, mêmes règles que SessionStore. */
private class CompteMémoire : CompteCommunautaire {
    var jetonValue: String? = null
        private set
    var demandée: String? = null
        private set
    var acceptée: String? = null
        private set

    override suspend fun jeton(): String? = jetonValue

    override suspend fun enregistrerJeton(jeton: String, mentionsVersion: String?) {
        jetonValue = jeton
        demandée = mentionsVersion
        acceptée = null // compte neuf : la notice doit être revalidée
    }

    override suspend fun mentionsDemandée(): String? = demandée

    override suspend fun mentionsAcceptée(): String? = acceptée

    override suspend fun noterVersionServie(version: String) {
        demandée = version
    }

    override suspend fun accepterNotices(version: String) {
        acceptée = version
    }

    override suspend fun oublierJeton() {
        jetonValue = null
        acceptée = null
    }

    override suspend fun oublierTout() {
        jetonValue = null
        demandée = null
        acceptée = null
    }
}

/** API factice : crée les jetons, refuse tous ceux qu'on lui déclare morts. */
private class ApiDeTest : CommunApi(base = { "https://serveur.test" }) {
    var comptesCréés = 0
        private set
    var révocations = 0
        private set
    var écrituresAcceptées = 0
        private set
    /** Version servie par `POST /compte` et `GET /mentions` (null = absente). */
    var versionNotice: String? = "2026-09-28"
    /** true = tous les jetons renvoyés par le serveur sont révoqués. */
    var tousRévoqués = false

    override suspend fun lecture(chemin: String, params: Map<String, String>): Lecture =
        if (chemin == "mentions") {
            Lecture(
                corps = buildJsonObject {
                    versionNotice?.let { put("version", JsonPrimitive(it)) }
                    put(
                        "sections",
                        kotlinx.serialization.json.JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("id", JsonPrimitive("objet"))
                                    put("titre", JsonPrimitive("Objet"))
                                    put("texte", JsonPrimitive("Un espace entre familles."))
                                },
                            ),
                        ),
                    )
                },
                total = null,
            )
        } else {
            Lecture(corps = kotlinx.serialization.json.JsonArray(emptyList()), total = 0)
        }

    override suspend fun écriture(
        méthode: String,
        chemin: String,
        corps: JsonObject?,
        jeton: String?,
    ): JsonElement? {
        if (méthode == "POST" && chemin == "compte") {
            comptesCréés++
            return buildJsonObject {
                put("jeton", JsonPrimitive("jeton-$comptesCréés"))
                versionNotice?.let { put("mentionsVersion", JsonPrimitive(it)) }
            }
        }
        // Toute autre écriture exige le jeton courant, non révoqué.
        if (jeton.isNullOrBlank() || tousRévoqués || jeton != "jeton-$comptesCréés") {
            throw JetonRévoqué()
        }
        if (méthode == "DELETE" && chemin == "compte") {
            révocations++
            return null
        }
        écrituresAcceptées++
        return buildJsonObject {
            put("id", JsonPrimitive(1))
            put("auteurId", JsonPrimitive("empreinte"))
            put("matière", JsonPrimitive("Maths"))
            put("contenu", JsonPrimitive("Exercices 1 à 5"))
            put("votes", JsonPrimitive(2))
            put("crééÀ", JsonPrimitive(0))
        }
    }
}

/** Lève [type] et la renvoie ; null si l'action est passée sans erreur. */
private suspend fun <T : Throwable> échecAttendu(type: KClass<T>, action: suspend () -> Unit): T? {
    try {
        action()
    } catch (t: Throwable) {
        if (type.isInstance(t)) return t as T
        throw t
    }
    return null
}

class CompteCommunautaireTest {

    private fun nouveauDépôt(): Triple<ApiDeTest, CompteMémoire, CommunauteRepository> {
        val api = ApiDeTest()
        val compte = CompteMémoire()
        return Triple(api, compte, CommunauteRepository(api, compte))
    }

    @Test
    fun `première écriture - compte créé puis notice exigée avant le contenu`() = runBlocking {
        val (api, compte, dépôt) = nouveauDépôt()

        assertNotNull(échecAttendu(NoticeRequise::class) { dépôt.voter(1, 1) })
        assertEquals(1, api.comptesCréés)
        assertEquals(0, api.écrituresAcceptées)
        assertEquals("jeton-1", compte.jeton())

        // Notice acceptée : la même écriture passe, sans second compte.
        dépôt.accepterNotice()
        val devoir = dépôt.voter(1, 1)
        assertEquals(1, api.comptesCréés)
        assertEquals(1, api.écrituresAcceptées)
        assertEquals(2, devoir.votes)
    }

    @Test
    fun `refus de la notice - révocation serveur et oubli total`() = runBlocking {
        val (api, compte, dépôt) = nouveauDépôt()

        assertNotNull(échecAttendu(NoticeRequise::class) { dépôt.voter(1, 1) })
        dépôt.révoquerCompte()

        assertEquals(1, api.révocations)
        assertNull(compte.jeton())
        assertNull(compte.demandée)
        assertNull(compte.acceptée)
        // La prochaine écriture repart d'un compte neuf.
        assertNotNull(échecAttendu(NoticeRequise::class) { dépôt.voter(1, 1) })
        assertEquals(2, api.comptesCréés)
    }

    @Test
    fun `version de notice changée - réaffichée avant la prochaine écriture`() = runBlocking {
        val (api, _, dépôt) = nouveauDépôt()

        assertNotNull(échecAttendu(NoticeRequise::class) { dépôt.voter(1, 1) })
        dépôt.accepterNotice()
        dépôt.voter(1, 1)

        // Le serveur publie une nouvelle version de la notice.
        api.versionNotice = "2026-10-05"
        val mentions = dépôt.mentions()
        assertEquals("2026-10-05", mentions.version)
        assertTrue(dépôt.noticeÀAfficher())

        assertNotNull(échecAttendu(NoticeRequise::class) { dépôt.voter(2, -1) })
        assertEquals(1, api.écrituresAcceptées) // la nouvelle écriture n'est pas passée
    }

    @Test
    fun `401 - un seul recomplément de compte par écriture`() = runBlocking {
        val (api, compte, dépôt) = nouveauDépôt()

        assertNotNull(échecAttendu(NoticeRequise::class) { dépôt.voter(1, 1) })
        dépôt.accepterNotice()

        // Le jeton local est révoqué côté serveur : 401 à la première tentative.
        api.tousRévoqués = true
        assertNotNull(échecAttendu(NoticeRequise::class) { dépôt.voter(1, 1) })
        assertEquals(2, api.comptesCréés) // UN seul POST /compte, jamais deux
        assertEquals("jeton-2", compte.jeton())
        assertNull(compte.mentionsAcceptée()) // le compte neuf revoit la notice
    }

    @Test
    fun `401 répété - une seule tentative de recomplément, puis l'erreur remonte`() = runBlocking {
        // Serveur sans version de notice : rien à afficher, l'écriture va
        // jusqu'au bout — et se cogne deux fois contre des jetons morts.
        val api = ApiDeTest().also { it.versionNotice = null; it.tousRévoqués = true }
        val compte = CompteMémoire()
        val dépôt = CommunauteRepository(api, compte)

        assertNotNull(échecAttendu(JetonRévoqué::class) { dépôt.voter(1, 1) })
        // UN seul POST /compte de relance pour toute l'écriture — puis stop.
        assertEquals(2, api.comptesCréés)
        // Le jeton fraîchement créé reste stocké — c'est un compte valide.
        assertEquals("jeton-2", compte.jeton())
    }
}
