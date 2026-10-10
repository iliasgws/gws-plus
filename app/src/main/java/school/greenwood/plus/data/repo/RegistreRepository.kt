package school.greenwood.plus.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.api.BotiErreur
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
 * (issue #145) : aucune n'attend l'autre, chacune tolère son propre échec —
 * sans qu'un échec total ou partiel passe pour un contenu à jour.
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

    suspend fun charger(
        aujourdhui: LocalDate = LocalDate.now(),
        // Dernier registre affiché : une section qui échoue retrouve son
        // contenu connu au lieu de se vider sous les yeux de l'utilisateur.
        connu: RegistreDuJour? = null,
        // Sections qui n'ont pas répondu (« devoirs », « actualités »…) —
        // vide quand tout a réussi. L'écran en a besoin pour prévenir.
        surÉchecs: (List<String>) -> Unit = {},
    ): RegistreDuJour =
        chargerRegistre(
            aujourdhui = aujourdhui,
            devoirs = { devoirs.liste() },
            posts = { nouveautes.liste() },
            absences = { absences.liste() },
            messages = { messagesRepo.conversations().conversations },
            connu = connu,
            surÉchecs = surÉchecs,
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
 *
 * Deux règles d'honnêteté (issue #21) encadrent cette tolérance :
 * - les quatre tombent ensemble (hors ligne, serveur injoignable) → c'est une
 *   panne, pas un registre vide : [BotiErreur] remonte et l'écran garde ce
 *   qu'il affichait déjà ;
 * - une seule tombe → sa section retrouve le contenu déjà connu du même jour
 *   ([connu]) et [surÉchecs] les liste, pour que l'écran prévienne.
 * Une annulation (changement de session, sortie d'écran) n'est pas une panne :
 * elle reprend son chemin et arrête le chargement.
 */
internal suspend fun chargerRegistre(
    aujourdhui: LocalDate,
    devoirs: suspend () -> List<Devoir>,
    posts: suspend () -> List<Post>,
    absences: suspend () -> BilanAbsences,
    messages: suspend () -> List<Conversation>,
    connu: RegistreDuJour? = null,
    surÉchecs: (List<String>) -> Unit = {},
): RegistreDuJour = coroutineScope {
    val devoirsChargés = async { auSource({ devoirs() }, emptyList<Devoir>()) }
    val postsChargés = async { auSource({ posts() }, emptyList<Post>()) }
    val bilansChargés = async { auSource({ absences() }, BilanAbsences()) }
    val messagesChargés = async { auSource({ messages() }, emptyList<Conversation>()) }

    val sourceDevoirs = devoirsChargés.await()
    val sourcePosts = postsChargés.await()
    val sourceBilans = bilansChargés.await()
    val sourceMessages = messagesChargés.await()

    val enÉchec = buildList {
        if (sourceDevoirs.enÉchec) add("devoirs")
        if (sourcePosts.enÉchec) add("actualités")
        if (sourceBilans.enÉchec) add("absences")
        if (sourceMessages.enÉchec) add("messages")
    }
    if (enÉchec.size == 4) {
        throw BotiErreur("Connexion impossible — vérifie ta connexion internet")
    }
    if (enÉchec.isNotEmpty()) surÉchecs(enÉchec)

    val tousLesDevoirs = sourceDevoirs.valeur
    val listePosts = sourcePosts.valeur
    val bilans = sourceBilans.valeur
    val listeMessages = sourceMessages.valeur

    // Section en échec : le contenu déjà affiché du même jour tient lieu de
    // résultat — rien ne disparaît de l'écran sous prétexte que le réseau a
    // lâché sur une seule source.
    val héritage = connu?.takeIf { it.date == aujourdhui && enÉchec.isNotEmpty() }
    val ceSoir = if (héritage != null && "devoirs" in enÉchec) {
        héritage.ceSoir
    } else {
        CeSoir.devoirsDuSoir(tousLesDevoirs, aujourdhui)
    }
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
        héritage?.entrees?.forEach { entrée ->
            if (entrée.section() in enÉchec) add(entrée)
        }
    }.sortedByDescending { it.horodatage ?: java.time.LocalDateTime.MAX }

    RegistreDuJour(
        date = aujourdhui,
        ceSoir = ceSoir,
        horizonCeSoir = CeSoir.prochaineRentree(aujourdhui),
        entrees = entrees,
    )
}

/** La section dont une entrée provient — le nom rapporté à l'écran. */
private fun EntreeRegistre.section(): String = when (this) {
    is EntreeRegistre.Actualite -> "actualités"
    is EntreeRegistre.DevoirDonné -> "devoirs"
    is EntreeRegistre.AbsenceNotée -> "absences"
    is EntreeRegistre.MessageReçu -> "messages"
}

/** Une source : sa valeur, et si elle a dû se rabattre sur son défaut. */
internal class Source<T>(val valeur: T, val enÉchec: Boolean)

/**
 * Le comportement de `runCatching`, mais honnête : l'échec est retenu au lieu
 * d'être confondu avec « la section est vide », et une annulation (changement
 * de session, sortie d'écran) n'est pas une panne — elle reprend son chemin et
 * arrête le chargement plutôt que d'afficher un registre à moitié vide.
 */
internal suspend fun <T> auSource(bloc: suspend () -> T, défaut: T): Source<T> = try {
    Source(bloc(), enÉchec = false)
} catch (annulation: CancellationException) {
    throw annulation
} catch (_: Exception) {
    Source(défaut, enÉchec = true)
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
