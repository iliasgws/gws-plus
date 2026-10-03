package school.greenwood.plus.util

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import school.greenwood.plus.BuildConfig
import java.io.File
import java.time.Duration
import java.util.UUID

/**
 * Cache local des messages vocaux (issue #108).
 *
 * Le lecteur jouait l'URL signée du serveur en flux direct : le média est
 * servi sous `media.boti.education` avec une signature collée en tête de
 * chemin qui expire au bout de 15–20 minutes, donc reprendre un fil plus
 * tard — ou faire défiler la bulle hors puis dedans — retombait sur une URL
 * morte et la préparation du lecteur échouait en silence. On télécharge
 * donc la voix dans `cache/audio/`, sous un nom dérivé de l'identité stable
 * de la ressource ([IdentiteMedias], jeton de signature retiré) : la même
 * pièce retrouve son fichier quelle que soit la ré-signature.
 *
 * Limite : le dossier est plafonné à 48 Mio ; au-delà, les pièces les moins
 * récemment utilisées (`lastModified`, repoussée à chaque réemploi) partent
 * d'abord, jamais la fraîchement ajoutée. Purge : `PurgeMedias` vide
 * `cache/audio/` à la connexion et à la déconnexion — un compte ne hérite
 * jamais de la voix du précédent, rien à brancher ici. Un échec (réseau,
 * réponse non 2xx, écriture, renommage) ne remonte jamais : [préparer] rend
 * null et le lecteur retombe sur le flux de l'URL, comme avant l'issue —
 * seule l'annulation du coroutine se propage, pour ne pas laisser un
 * téléchargement orphelin derrière un fil défilé loin.
 */
object CacheAudio {

    /** Plafond du dossier `cache/audio` : 48 Mio — au-delà, les pièces moins
     *  récemment utilisées partent d'abord, jamais la fraîchement ajoutée. */
    private const val LIMITE = 48L * 1024 * 1024

    /** Client dédié : délais explicites — le défaut d'OkHttp (10 s de
     *  lecture) laisse une voix lente attendre indéfiniment. */
    private val http = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofSeconds(60))
        .writeTimeout(Duration.ofSeconds(60))
        .build()

    /** Dossier du cache voix — vidé par `PurgeMedias` aux changements de
     *  session (connexion, déconnexion). */
    fun dossier(context: Context): File = File(context.cacheDir, "audio")

    /** Nom local de la voix : nom sûr + empreinte de l'identité signée.
     *  Le jeton signé n'entre jamais dans le nom (il expire, et on ne le
     *  journalise pas) ; deux URL ré-signées du même vocal tombent sur le
     *  même fichier, deux ressources différentes jamais. */
    fun nomEnCache(url: String): String = IdentiteMedias.fichierÀEmpreinte(
        Fichiers.nomFichierSain("voix", url),
        IdentiteMedias.clé(url),
    )

    /** Fichier déjà en cache, sans aucun réseau — null si absent ou vide.
     *  Un réemploi repousse sa date : l'évacuation « le plus ancien d'abord »
     *  devient vraiment « le moins récemment utilisé ». */
    fun enCache(context: Context, url: String): File? =
        File(dossier(context), nomEnCache(url))
            .takeIf { it.isFile && it.length() > 0 }
            ?.also { fichier -> fichier.setLastModified(System.currentTimeMillis()) }

    /**
     * Prépare la voix en local : renvoie le fichier s'il existe déjà (aucun
     * réseau — le réemploi en profite pour repousser sa date d'usage), le
     * télécharge sinon — ou null si quoi que ce soit échoue, l'appelant
     * retombant alors sur le flux de l'URL. Jamais d'exception hormis
     * l'annulation du coroutine, qui se propage (la copie cède à chaque
     * morceau, sans jamais laisser un téléchargement orphelin). Un
     * demi-fichier n'est jamais conservé : chaque tentative écrit dans son
     * propre `.part` unique, supprimé à toute sortie — succès déjà renommé,
     * échec ou annulation — et le fichier n'apparaît dans le cache qu'après
     * un téléchargement complet. Chaque ajout réussi déclenche l'élagage du
     * dossier au-delà de [LIMITE].
     */
    suspend fun préparer(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            val répertoire = dossier(context)
            répertoire.mkdirs()
            val cible = File(répertoire, nomEnCache(url))
            if (cible.isFile && cible.length() > 0) {
                // Réemploi : même rôle qu'`enCache`, pour un vrai LRU.
                cible.setLastModified(System.currentTimeMillis())
                return@withContext cible
            }
            // Nom unique par tentative : deux appuis simultanés sur la même
            // pièce ne partagent jamais le même flux, donc jamais la moitié
            // écrite par l'un n'est renommée puis resservie par l'autre.
            val partielle = File(
                répertoire,
                cible.name + "." + UUID.randomUUID().toString().take(8) + ".part",
            )
            try {
                val requête = Request.Builder()
                    .url(url)
                    .header("User-Agent", "GWSPlus/${BuildConfig.VERSION_NAME}")
                    .build()
                http.newCall(requête).execute().use { réponse ->
                    if (!réponse.isSuccessful) error("Téléchargement impossible (${réponse.code})")
                    réponse.body.byteStream().use { entrée ->
                        partielle.outputStream().use { sortie ->
                            val tampon = ByteArray(16 * 1024)
                            while (true) {
                                // Copie morceau par morceau : `copyTo` reste
                                // bloqué dans OkHttp, alors que cet appel rend
                                // l'annulation effective dès le prochain tour.
                                currentCoroutineContext().ensureActive()
                                val lus = entrée.read(tampon)
                                if (lus < 0) break
                                sortie.write(tampon, 0, lus)
                            }
                        }
                    }
                }
                if (partielle.length() == 0L) error("Pièce vide")
                // Renommage atomique : le fichier n'existe dans le cache
                // qu'une fois le téléchargement mené à bien.
                if (!partielle.renameTo(cible)) error("Renommage impossible")
            } finally {
                // Toute sortie laisse le dossier net : le `.part` survivant
                // d'un échec ou d'une annulation part avec elle (après un
                // renommage réussi, il n'existe plus de toute façon).
                runCatching { if (partielle.exists()) partielle.delete() }
            }
            // L'élagage ne doit jamais faire échouer un ajout réussi.
            runCatching { élaguer(répertoire, cible) }
            cible
        } catch (annulation: CancellationException) {
            // Une annulation n'est pas un échec : elle remonte, le fichier
            // partiel étant déjà supprimé par le `finally`.
            throw annulation
        } catch (échec: Throwable) {
            null
        }
    }

    /** Une pièce du cache : contenu, poids et date de dernier usage. */
    data class Entrée(val fichier: File, val taille: Long, val modifiéLe: Long)

    /**
     * Quelles pièces évacuer quand le total dépasse la limite — logique
     * pure, sans Context (exercée par `CacheAudioTest`). Les plus anciennes
     * (`modifiéLe`) partent d'abord ; [àGarder], la pièce fraîchement
     * ajoutée, est protégée même si elle seule dépasse déjà la limite.
     * Renvoie les fichiers à supprimer, du plus ancien au plus récent.
     */
    fun fichiersÀÉvacuer(entrées: List<Entrée>, limite: Long, àGarder: File?): List<File> {
        var total = entrées.sumOf { it.taille }
        if (total <= limite) return emptyList()
        val sacrifiées = mutableListOf<File>()
        for (entrée in entrées.filter { it.fichier != àGarder }.sortedBy { it.modifiéLe }) {
            if (total <= limite) break
            total -= entrée.taille
            sacrifiées += entrée.fichier
        }
        return sacrifiées
    }

    /** Élagage après un ajout réussi : dépassement de [LIMITE] → suppression
     *  des pièces les moins récemment utilisées, la nouvelle intacte.
     *  Repart aussi des `.part` orphelins : le nom unique interdit à la
     *  tentative suivante de les effacer, donc un reliquat sans écriture
     *  depuis plus d'une heure (aucun téléchargement actif ne reste muet
     *  aussi longtemps, le délai de lecture OkHttp vaut 60 s) part d'ici. */
    private fun élaguer(répertoire: File, ajouté: File) {
        val fichiers = répertoire.listFiles() ?: return
        val abandonnéAvant = System.currentTimeMillis() - 60L * 60 * 1000
        for (fichier in fichiers) {
            if (fichier.isFile && fichier.name.endsWith(".part") &&
                fichier.lastModified() < abandonnéAvant
            ) {
                runCatching { fichier.delete() }
            }
        }
        val entrées = fichiers
            .filter { it.isFile && !it.name.endsWith(".part") }
            .map { Entrée(it, it.length(), it.lastModified()) }
        fichiersÀÉvacuer(entrées, LIMITE, ajouté).forEach { perdue ->
            runCatching { perdue.delete() }
        }
    }
}
