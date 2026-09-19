package school.greenwood.plus.data.repo

import school.greenwood.plus.data.api.BotiClient
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
 * ne connaît que le registre.
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
) {
    private val devoirs = DevoirsRepository(client)
    private val nouveautes = NouveautesRepository(client, session)
    private val absences = AbsencesRepository(client)
    private val messagesRepo = MessagesRepository(client, session)

    suspend fun charger(aujourdhui: LocalDate = LocalDate.now()): RegistreDuJour {
        val tousLesDevoirs = runCatching { devoirs.liste() }.getOrDefault(emptyList())
        val ceSoir = CeSoir.devoirsDuSoir(tousLesDevoirs, aujourdhui)

        val posts = runCatching { nouveautes.liste() }.getOrDefault(emptyList())
        val bilans = runCatching { absences.liste() }.getOrDefault(BilanAbsences())
        val messages = runCatching { messagesRepo.conversations().conversations }.getOrDefault(emptyList())

        val entrees = buildList {
            posts.filter { it.date?.toLocalDate() == aujourdhui }
                .forEach { add(EntreeRegistre.Actualite(it)) }
            tousLesDevoirs.filter { it.publication?.toLocalDate() == aujourdhui }
                .forEach { add(EntreeRegistre.DevoirDonné(it)) }
            (bilans.justifiees + bilans.nonJustifiees)
                .filter { it.du == aujourdhui || it.au == aujourdhui }
                .forEach { add(EntreeRegistre.AbsenceNotée(it)) }
            messages.filter { m -> m.dernierDate?.toLocalDate()?.let { it == aujourdhui || it.isAfter(aujourdhui) } == true }
                .forEach { conversation -> add(EntreeRegistre.MessageReçu(conversation)) }
        }.sortedByDescending { it.horodatage ?: java.time.LocalDateTime.MAX }

        return RegistreDuJour(
            date = aujourdhui,
            ceSoir = ceSoir,
            horizonCeSoir = CeSoir.prochaineRentree(aujourdhui),
            entrees = entrees,
        )
    }
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
