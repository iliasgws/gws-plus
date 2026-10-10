package school.greenwood.plus.data.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import school.greenwood.plus.data.repo.EntreeRegistre
import school.greenwood.plus.data.repo.RegistreDuJour
import school.greenwood.plus.data.session.SessionSecrets
import school.greenwood.plus.model.Absence
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Message
import school.greenwood.plus.model.Post
import school.greenwood.plus.util.extractDate
import school.greenwood.plus.util.extractDateTime
import java.io.File
import java.time.LocalDate

/*
 * Instantané disque du registre (issue #145) : la dernière image affichée
 * survit au redémarrage du processus, pour ouvrir l'accueil sans attendre le
 * réseau — le rafraîchissement part immédiatement en tâche de fond.
 *
 * Ce qui est stocké, et ce qui ne l'est pas :
 * - le contenu EXACT de l'écran (carte « Ce soir », entrées du jour, dernière
 *   actualité) — jamais un jeton, un identifiant de session ni une URL de
 *   connexion ; les URLs signées des images expirent comme d'habitude ;
 * - un seul fichier, dans `noBackupFilesDir` : hors des sauvegardes Android
 *   et des transferts appareil à appareil par construction (issue #137) ;
 * - chiffré au repos avec la même clé Keystore AES-GCM que les credentials
 *   (issue #138) — la clé ne quitte jamais le Keystore ;
 * - la clé de session (userId/eleveId) est PORTÉE DANS le payload : une
 *   lecture depuis un autre compte ou un autre enfant rend null, exactement
 *   comme le cache mémoire (issue #21) ;
 * - purgé à la connexion et à la déconnexion (AuthRepository), au même moment
 *   que les caches mémoire et les médias ;
 * - jamais écrit quand « Rester connecté » est décoché (issue #140) : la
 *   session ne touche alors rien du disque.
 *
 * Tout échec (clé Keystore perdue, fichier corrompu, format inconnu, IO)
 * rend null / ignore : le squelette reprend la place, jamais un crash et
 * jamais de donnée brute dans une exception.
 */

/** Format du fichier — un changement d'structure invalide l'ancien. */
internal const val VERSION_SNAPSHOT = 1

private val jsonSnapshot = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
internal data class PièceSnapshot(val name: String, val url: String) {
    fun versModèle() = Attachment(name = name, url = url)

    companion object {
        fun de(p: Attachment) = PièceSnapshot(name = p.name, url = p.url)
    }
}

@Serializable
internal data class DevoirSnapshot(
    val id: String,
    val title: String,
    val matiere: String,
    val categorie: String? = null,
    val enseignant: String? = null,
    val description: String? = null,
    val dateRemise: String? = null,
    val publication: String? = null,
    val fait: Boolean = false,
    val filesSent: Boolean = false,
    val faitLocal: Boolean = false,
    val attachments: List<PièceSnapshot> = emptyList(),
) {
    fun versModèle() = Devoir(
        id = id,
        title = title,
        matiere = matiere,
        categorie = categorie,
        enseignant = enseignant,
        description = description,
        dateRemise = dateRemise?.let(::extractDate),
        publication = publication?.let(::extractDateTime),
        fait = fait,
        filesSent = filesSent,
        attachments = attachments.map { it.versModèle() },
        faitLocal = faitLocal,
    )

    companion object {
        fun de(d: Devoir) = DevoirSnapshot(
            id = d.id,
            title = d.title,
            matiere = d.matiere,
            categorie = d.categorie,
            enseignant = d.enseignant,
            description = d.description,
            dateRemise = d.dateRemise?.toString(),
            publication = d.publication?.toString(),
            fait = d.fait,
            filesSent = d.filesSent,
            faitLocal = d.faitLocal,
            attachments = d.attachments.map { PièceSnapshot.de(it) },
        )
    }
}

@Serializable
internal data class PostSnapshot(
    val id: String,
    val title: String,
    val categorie: String? = null,
    val date: String? = null,
    val intro: String? = null,
    val description: String? = null,
    val image: String? = null,
    val bookmark: Boolean = false,
    val auteur: String? = null,
    val permitComments: Boolean = false,
    val permitNewComments: Boolean = false,
    val permitQuiz: Boolean = false,
    val attachments: List<PièceSnapshot> = emptyList(),
) {
    fun versModèle() = Post(
        id = id,
        title = title,
        categorie = categorie,
        date = date?.let(::extractDateTime),
        intro = intro,
        description = description,
        image = image,
        attachments = attachments.map { it.versModèle() },
        bookmark = bookmark,
        auteur = auteur,
        permitComments = permitComments,
        permitNewComments = permitNewComments,
        permitQuiz = permitQuiz,
    )

    companion object {
        fun de(p: Post) = PostSnapshot(
            id = p.id,
            title = p.title,
            categorie = p.categorie,
            date = p.date?.toString(),
            intro = p.intro,
            description = p.description,
            image = p.image,
            bookmark = p.bookmark,
            auteur = p.auteur,
            permitComments = p.permitComments,
            permitNewComments = p.permitNewComments,
            permitQuiz = p.permitQuiz,
            attachments = p.attachments.map { PièceSnapshot.de(it) },
        )
    }
}

@Serializable
internal data class AbsenceSnapshot(
    val id: String,
    val motif: String? = null,
    val du: String? = null,
    val au: String? = null,
    val justifiee: Boolean = false,
) {
    fun versModèle() = Absence(
        id = id,
        motif = motif,
        du = du?.let(::extractDate),
        au = au?.let(::extractDate),
        justifiee = justifiee,
    )

    companion object {
        fun de(a: Absence) = AbsenceSnapshot(
            id = a.id,
            motif = a.motif,
            du = a.du?.toString(),
            au = a.au?.toString(),
            justifiee = a.justifiee,
        )
    }
}

@Serializable
internal data class MessageSnapshot(
    val id: String,
    val deLAdmin: Boolean = false,
    val texte: String,
    val date: String? = null,
) {
    fun versModèle() = Message(id = id, deLAdmin = deLAdmin, texte = texte, date = date?.let(::extractDateTime))

    companion object {
        fun de(m: Message) = MessageSnapshot(
            id = m.id,
            deLAdmin = m.deLAdmin,
            texte = m.texte,
            date = m.date?.toString(),
        )
    }
}

/**
 * Une conversation telle qu'elle apparaît sur la carte du registre : sujet,
 * thème, date du dernier message et CE dernier message (aperçu deux lignes).
 * L'historique complet reste sur le serveur — le snapshot ne garde pas les
 * fils entiers sur disque.
 */
@Serializable
internal data class ConversationSnapshot(
    val id: String,
    val sujet: String,
    val theme: String? = null,
    val dernierDate: String? = null,
    val dernierMessage: MessageSnapshot? = null,
) {
    fun versModèle(): Conversation {
        val message = dernierMessage?.versModèle()
        // `dernierDate` est la date MAX du fil : on la porte sur le message
        // conservé pour que la carte affiche la bonne date.
        val daté = if (message != null) {
            message.copy(date = extractDateTime(dernierDate) ?: message.date)
        } else {
            null
        }
        return Conversation(
            id = id,
            sujet = sujet,
            theme = theme,
            messages = listOfNotNull(daté),
        )
    }

    companion object {
        fun de(c: Conversation) = ConversationSnapshot(
            id = c.id,
            sujet = c.sujet,
            theme = c.theme,
            dernierDate = c.dernierDate?.toString(),
            dernierMessage = c.messages.lastOrNull()?.let(MessageSnapshot::de),
        )
    }
}

/** Entrée du jour, dans son ordre d'affichage (tri chronologique descendant). */
@Serializable
internal sealed interface EntréeSnapshot {
    @Serializable
    @SerialName("actualite")
    data class Actualite(val post: PostSnapshot) : EntréeSnapshot

    @Serializable
    @SerialName("devoir")
    data class DevoirDonné(val devoir: DevoirSnapshot) : EntréeSnapshot

    @Serializable
    @SerialName("absence")
    data class AbsenceNotée(val absence: AbsenceSnapshot) : EntréeSnapshot

    @Serializable
    @SerialName("message")
    data class MessageReçu(val conversation: ConversationSnapshot) : EntréeSnapshot

    fun versEntrée(): EntreeRegistre = when (this) {
        is Actualite -> EntreeRegistre.Actualite(post.versModèle())
        is DevoirDonné -> EntreeRegistre.DevoirDonné(devoir.versModèle())
        is AbsenceNotée -> EntreeRegistre.AbsenceNotée(absence.versModèle())
        is MessageReçu -> EntreeRegistre.MessageReçu(conversation.versModèle())
    }

    companion object {
        fun de(e: EntreeRegistre): EntréeSnapshot = when (e) {
            is EntreeRegistre.Actualite -> Actualite(PostSnapshot.de(e.post))
            is EntreeRegistre.DevoirDonné -> DevoirDonné(DevoirSnapshot.de(e.devoir))
            is EntreeRegistre.AbsenceNotée -> AbsenceNotée(AbsenceSnapshot.de(e.absence))
            is EntreeRegistre.MessageReçu -> MessageReçu(ConversationSnapshot.de(e.conversation))
        }
    }
}

/** L'écran du registre, prêt à être rejoué tel quel. */
@Serializable
internal data class SnapshotRegistre(
    val version: Int = VERSION_SNAPSHOT,
    /** Clé de session (userId/eleveId) — jamais servie à une autre. */
    val clé: String,
    /** Écriture (System.currentTimeMillis) — réservé au diagnostic. */
    val époque: Long,
    val date: String,
    val horizonCeSoir: String,
    val ceSoir: List<DevoirSnapshot>,
    val entrees: List<EntréeSnapshot>,
    val derniereActualite: PostSnapshot? = null,
) {
    fun versRegistre(): RegistreDuJour = RegistreDuJour(
        date = extractDate(date) ?: LocalDate.now(),
        ceSoir = ceSoir.map { it.versModèle() },
        horizonCeSoir = extractDate(horizonCeSoir) ?: LocalDate.now(),
        entrees = entrees.map { it.versEntrée() },
    )

    fun versDerniereActualite(): Post? = derniereActualite?.versModèle()

    companion object {
        fun de(
            registre: RegistreDuJour,
            clé: String,
            derniereActualite: Post?,
            époque: Long = System.currentTimeMillis(),
        ) = SnapshotRegistre(
            clé = clé,
            époque = époque,
            date = registre.date.toString(),
            horizonCeSoir = registre.horizonCeSoir.toString(),
            ceSoir = registre.ceSoir.map { DevoirSnapshot.de(it) },
            entrees = registre.entrees.map { EntréeSnapshot.de(it) },
            derniereActualite = derniereActualite?.let { PostSnapshot.de(it) },
        )
    }
}

/**
 * Le fichier lui-même. `fichier` et `secrets` sont injectables : les tests
 * JVM tournent sur un répertoire temporaire avec un chiffreur factice, la
 * production utilise `noBackupFilesDir` et le Keystore.
 */
internal class SnapshotsRegistre(
    private val fichier: File,
    private val secrets: SessionSecrets = SessionSecrets(),
) {

    constructor(context: Context) : this(File(context.noBackupFilesDir, FICHIER))

    /** Dernier instantané de CETTE session ; null partout ailleurs. */
    suspend fun lire(clé: String): SnapshotRegistre? = withContext(Dispatchers.IO) {
        val brut = runCatching {
            if (fichier.isFile) fichier.readText(Charsets.UTF_8) else null
        }.getOrNull() ?: return@withContext null
        if (brut.isEmpty()) return@withContext null
        val ouvert = secrets.open(brut)
        if (ouvert.isEmpty()) return@withContext null
        runCatching { jsonSnapshot.decodeFromString(SnapshotRegistre.serializer(), ouvert) }
            .getOrNull()
            ?.takeIf { it.version == VERSION_SNAPSHOT && it.clé == clé }
    }

    /** Écriture atomique (fichier temporaire puis remplacement). */
    suspend fun écrire(snapshot: SnapshotRegistre) = withContext(Dispatchers.IO) {
        runCatching {
            val scellé = secrets.seal(
                jsonSnapshot.encodeToString(SnapshotRegistre.serializer(), snapshot),
            )
            fichier.parentFile?.mkdirs()
            val temporaire = File(fichier.parentFile, fichier.name + ".tmp")
            temporaire.writeText(scellé, Charsets.UTF_8)
            if (!temporaire.renameTo(fichier)) {
                fichier.delete()
                temporaire.renameTo(fichier)
            }
        }
        Unit
    }

    suspend fun vider() = withContext(Dispatchers.IO) {
        runCatching {
            fichier.delete()
            File(fichier.parentFile, fichier.name + ".tmp").delete()
        }
        Unit
    }

    private companion object {
        const val FICHIER = "registre-instantane.enc"
    }
}
