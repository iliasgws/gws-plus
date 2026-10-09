package school.greenwood.plus.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.logic.CeSoir
import school.greenwood.plus.model.Absence
import school.greenwood.plus.model.BilanAbsences
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Post
import java.time.LocalDate

/*
 * Le registre du jour (docs/product/DESIGN.md §2) : le flux chronologique de ce qui s'est
 * passé aujourd'hui, avec la carte « Ce soir » en tête. Une seule classe
 * agrège les quatre sources (nouveautes, devoirs, absences, messages) — l'UI
 * ne connaît que le registre. Les quatre requêtes partent en parallèle
 * (issue #145) : aucune n'attend l'autre, chacune tolère son propre échec.
 */

sealed interface EntreeRegistre {
    val horodatage: java.time.LocalDateTime?
    val id: String

    data class Actualite(val post: Post) : EntreeRegistre {
        override val horodatage get() = post.date
        override val id get() = "post-${post.id}"
    }

    data class DevoirDonné(val devoir: Devoir) : EntreeRegistre {
        override val horodatage get() = devoir.publication
        override val id get() = "devoir-${devoir.id}"
    }

    data class AbsenceNotée(val absence: Absence) : EntreeRegistre {
        override val horodatage: java.time.LocalDateTime? = null
        override val id get() = "absence-${absence.id}"
    }

    data class MessageReçu(val conversation: Conversation) : EntreeRegistre {
        override val horodatage get() = conversation.dernierDate
        override val id get() = "message-${conversation.id}"
    }
}

data class RegistreDuJour(
    val date: LocalDate,
    val ceSoir: List<Devoir>,
    val horizonCeSoir: LocalDate,
    val entrees: List<EntreeRegistre>,
)

class RegistreRepository(
    private val client: BotiClient,
    private val session: SessionStore,
    private val caches: CachesSession,
) {
    private val devoirs = DevoirsRepository(client, caches, session)
    private val nouveautes = NouveautesRepository(client, session, caches)
    private val absences = AbsencesRepository(client)
    private val messagesRepo = MessagesRepository(client, session, caches)

    suspend fun charger(aujourdhui: LocalDate = LocalDate.now()): RegistreDuJour =
        chargerRegistre(
            aujourdhui = aujourdhui,
            devoirs = { devoirs.liste() },
            posts = { nouveautes.liste() },
            absences = { absences.liste() },
            messages = { messagesRepo.conversations().conversations },
        ).also { jour ->
            // Dernier registre connu (issue #21), estampillé session.
            caches.clé()?.let { clé -> caches.registre.écrire(clé, jour) }
        }

    /** Dernier registre construit, estampillé session — null si rien en cache
     *  ou si la session a changé depuis l'écriture. */
    suspend fun registreEnCache(): RegistreDuJour? {
        val clé = caches.clé() ?: return null
        return caches.registre.lire(clé)
    }
}

/*
 * Les quatre sources du registre sont indépendantes : elles partent ensemble
 * (issue #145) et chacune tolère son propre échec. Fonction pure — testable
 * sans Android, sans réseau et sans session (`RegistreParallèleTest`).
 */
internal suspend fun chargerRegistre(
    aujourdhui: LocalDate,
    devoirs: suspend () -> List<Devoir>,
    posts: suspend () -> List<Post>,
    absences: suspend () -> BilanAbsences,
    messages: suspend () -> List<Conversation>,
): RegistreDuJour = coroutineScope {
    val devoirsChargés = async { enTolérance({ devoirs() }, emptyList<Devoir>()) }
    val postsChargés = async { enTolérance({ posts() }, emptyList<Post>()) }
    val bilansChargés = async { enTolérance({ absences() }, BilanAbsences()) }
    val messagesChargés = async { enTolérance({ messages() }, emptyList<Conversation>()) }

    val tousLesDevoirs = devoirsChargés.await()
    val listePosts = postsChargés.await()
    val bilans = bilansChargés.await()
    val listeMessages = messagesChargés.await()

    val ceSoir = CeSoir.devoirsDuSoir(tousLesDevoirs, aujourdhui)
    val entrees = buildList {
        listePosts.filter { it.date?.toLocalDate() == aujourdhui }
            .forEach { add(EntreeRegistre.Actualite(it)) }
        tousLesDevoirs.filter { it.publication?.toLocalDate() == aujourdhui }
            .forEach { add(EntreeRegistre.DevoirDonné(it)) }
        (bilans.justifiees + bilans.nonJustifiees)
            .filter { it.du == aujourdhui || it.au == aujourdhui }
            .forEach { add(EntreeRegistre.AbsenceNotée(it)) }
        listeMessages.filter { m -> m.dernierDate?.toLocalDate()?.let { it == aujourdhui || it.isAfter(aujourdhui) } == true }
            .forEach { conversation -> add(EntreeRegistre.MessageReçu(conversation)) }
    }.sortedByDescending { it.horodatage ?: java.time.LocalDateTime.MAX }

    RegistreDuJour(
        date = aujourdhui,
        ceSoir = ceSoir,
        horizonCeSoir = CeSoir.prochaineRentree(aujourdhui),
        entrees = entrees,
    )
}

/**
 * Une panne serveur rend sa section vide sans masquer les autres — le
 * comportement de `runCatching`… sauf qu'une annulation (changement de
 * session, sortie d'écran) n'est pas une panne : elle reprend son chemin
 * et arrête le chargement plutôt que d'afficher un registre à moitié vide.
 */
private suspend fun <T> enTolérance(bloc: suspend () -> T, défaut: T): T = try {
    bloc()
} catch (annulation: CancellationException) {
    throw annulation
} catch (_: Exception) {
    défaut
}

class AbsencesRepository(private val client: BotiClient) {

    /** data{justifiees[], non_justifiees[]} + stats. Items jamais observés :
     *  normalisation défensive, tolérante aux champs manquants. */
    suspend fun liste(): BilanAbsences {
        val rep = client.get("absences", mapOf("page" to "1"))
        val data = rep["data"] as? kotlinx.serialization.json.JsonObject
        fun items(clef: String) = data?.let {
            Normalizers.arr(it, clef).mapNotNull { e ->
                (e as? kotlinx.serialization.json.JsonObject)?.let(Normalizers::absence)
            }
        } ?: emptyList()
        val stats = rep["stats_absences"] as? kotlinx.serialization.json.JsonObject
        return BilanAbsences(
            justifiees = items("justifiees"),
            nonJustifiees = items("non_justifiees"),
            total = Normalizers.int(stats ?: kotlinx.serialization.json.JsonObject(emptyMap()), "total") ?: 0,
            retards = (rep["stats_retards"] as? kotlinx.serialization.json.JsonObject)
                ?.let { Normalizers.int(it, "total") } ?: 0,
        )
    }
}
