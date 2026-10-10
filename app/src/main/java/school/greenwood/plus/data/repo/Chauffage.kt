package school.greenwood.plus.data.repo

import android.content.Context
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Post
import school.greenwood.plus.util.CacheAudio
import school.greenwood.plus.util.Fichiers

/*
 * Chauffage des caches : à l'ouverture de l'application et à chaque
 * actualisation, toutes les sections sont rafraîchies en arrière-plan — un
 * onglet jamais ouvert s'ouvre déjà rempli (issue #21), et une actualisation
 * sur un écran rafraîchit les autres. Les images et pièces jointes visibles
 * entrent dans les caches binaires sous leur clé stable (issue #108), donc
 * réutilisées à l'ouverture quel que soit le jour.
 *
 * Règles : chaque source tolère son échec (comme `chargerRegistre`), rien
 * n'est projeté dans l'UI, une annulation (déconnexion, réglage désactivé,
 * nouvelle session) arrête tout, et le réglage des Paramètres coupe le
 * chauffage immédiatement.
 */
class ChauffageTout(
    private val context: Context,
    private val session: SessionStore,
    private val cours: CoursRepository,
    private val devoirs: DevoirsRepository,
    private val documents: DocumentsRepository,
    private val demandes: DemandesRepository,
    private val messages: MessagesRepository,
    private val nouveautes: NouveautesRepository,
) {
    private val portée = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var enCours: Job? = null

    init {
        // Purge de session (déconnexion) : plus rien à préparer pour ce compte.
        portée.launch {
            session.state.collect { état -> if (état == null) annuler() }
        }
    }

    /** Relance le chauffage — jamais deux en même temps. */
    fun chauffer() {
        enCours?.cancel()
        enCours = portée.launch {
            if (!session.chauffageToutActivé.first()) return@launch
            if (session.state.first() == null) return@launch
            val données = chaufferSections(
                semaine = { cours.semaine() },
                devoirs = { devoirs.liste() },
                documents = { documents.ressources() },
                bibliotheque = { documents.bibliotheque() },
                demandes = { demandes.liste() },
                conversations = { messages.conversations().conversations },
                posts = { nouveautes.liste() },
            )
            préparerMédias(données)
        }
    }

    fun annuler() {
        enCours?.cancel()
    }

    /** Images, pièces jointes et messages vocaux déjà visibles dans les
     *  listes — chaque échec est muet, l'annulation non. */
    private suspend fun préparerMédias(données: Chauffé) {
        val chargeur = SingletonImageLoader.get(context)
        données.imagesÀPréparer().forEach { url ->
            silencieux { chargeur.enqueue(ImageRequest.Builder(context).data(url).build()) }
        }
        données.piecesÀTélécharger().forEach { (url, nom) ->
            silencieux { Fichiers.télécharger(context, url, nom) }
        }
        données.voixÀPréparer().forEach { url ->
            silencieux { CacheAudio.préparer(context, url) }
        }
    }

    private suspend fun silencieux(bloc: suspend () -> Unit) {
        try {
            bloc()
        } catch (annulation: CancellationException) {
            throw annulation
        } catch (_: Exception) {
            // Média injoignable : le reste du chauffage continue.
        }
    }
}

/** Ce que le chauffage a récupéré — de quoi préparer les médias. */
internal class Chauffé(
    val posts: List<Post>,
    val conversations: List<Conversation>,
)

internal const val LIMITE_IMAGES = 12
internal const val LIMITE_PIÈCES = 24
internal const val LIMITE_VOIX = 12

/** Couvertures d'actualités — le cache image de Coil les garde sous leur
 *  clé stable, donc une URL signée d'aujourd'hui sert demain. */
internal fun Chauffé.imagesÀPréparer(): List<String> =
    posts.mapNotNull { it.image }.distinct().take(LIMITE_IMAGES)

/** Pièces jointes (messages, actualités) — cache privé, jamais le dossier
 *  public Download. */
internal fun Chauffé.piecesÀTélécharger(): List<Pair<String, String>> =
    buildList {
        conversations.forEach { conversation ->
            conversation.messages.forEach { message ->
                message.attachments.forEach { add(it.url to it.name) }
            }
        }
        posts.forEach { post ->
            post.attachments.forEach { add(it.url to it.name) }
        }
    }.distinctBy { it.first }.take(LIMITE_PIÈCES)

/** Messages vocaux — cache audio (LRU 48 Mo). */
internal fun Chauffé.voixÀPréparer(): List<String> =
    buildList {
        conversations.forEach { conversation ->
            conversation.messages.forEach { message ->
                message.audio?.url?.let { add(it) }
            }
        }
    }.distinct().take(LIMITE_VOIX)

/*
 * Les sept listes se rafraîchissent ensemble : une source en panne laisse sa
 * section en cache tel quel, une annulation (déconnexion, réglage coupé)
 * arrête tout — jamais de faux succès, jamais d'erreur projetée (issue #21).
 */
internal suspend fun chaufferSections(
    semaine: suspend () -> Unit,
    devoirs: suspend () -> Unit,
    documents: suspend () -> Unit,
    bibliotheque: suspend () -> Unit,
    demandes: suspend () -> Unit,
    conversations: suspend () -> List<Conversation>,
    posts: suspend () -> List<Post>,
): Chauffé = coroutineScope {
    val postsChargés = async { auSource({ posts() }, emptyList<Post>()) }
    val conversationsChargées = async { auSource({ conversations() }, emptyList<Conversation>()) }
    val autres = listOf(
        async { auSource({ semaine() }, Unit) },
        async { auSource({ devoirs() }, Unit) },
        async { auSource({ documents() }, Unit) },
        async { auSource({ bibliotheque() }, Unit) },
        async { auSource({ demandes() }, Unit) },
    )
    autres.awaitAll()
    Chauffé(
        posts = postsChargés.await().valeur,
        conversations = conversationsChargées.await().valeur,
    )
}
