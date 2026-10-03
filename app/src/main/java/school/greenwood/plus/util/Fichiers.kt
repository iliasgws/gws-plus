package school.greenwood.plus.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import school.greenwood.plus.BuildConfig
import java.io.File

/*
 * Pièces jointes : téléchargement dans l'espace privé de l'app (aucune
 * permission stockage nécessaire), ouverture native via FileProvider —
 * le PDF s'ouvre dans le lecteur du système, jamais dans une webview.
 *
 * Les devoirs (issue #68) téléchargent autrement : dans le dossier public
 * « Downloads/gws-plus » (MediaStore, API 29+) pour rester visibles dans
 * les Fichiers du téléphone — l'ancien cache privé était introuvable pour
 * l'élève, et le lot « Tout télécharger » semblait ne rien enregistrer.
 */
object Fichiers {

    private val http = OkHttpClient()

    /** Client des téléchargements publics : délais explicites (le défaut
     *  d'OkHttp, 10 s de lecture, laisse un lot « sans fin » sur une pièce
     *  lente ou muette). */
    private val httpPublic = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(20))
        .readTimeout(java.time.Duration.ofSeconds(60))
        .writeTimeout(java.time.Duration.ofSeconds(60))
        // Limite globale : le verrou du parcours public ne doit jamais être
        // retenu indéfiniment par un serveur qui coule les octets.
        .callTimeout(java.time.Duration.ofSeconds(120))
        .build()

    /** Verrou du parcours public : la lecture du registre, le choix du nom,
     *  l'insertion, le téléchargement et l'écriture du registre forment une
     *  seule section critique — deux appels simultanés qui la partageraient
     *  perdraient l'entrée de l'autre (lecture-écriture non atomique). */
    private val verrou = Mutex()

    /** Dossier public de l'app dans les Fichiers du téléphone. */
    const val DOSSIER_PUBLIC = "Download/gws-plus"

    fun dossierDocuments(context: Context): File =
        File(context.filesDir, "documents").apply { mkdirs() }

    /**
     * Télécharge dans le cache documents, sous un nom portant l'empreinte de
     * la ressource (issue #108) : l'URL re-signée d'une même ressource
     * retombe sur le même fichier, et deux ressources différentes qui
     * partagent un libellé (« Photo.jpg ») n'y touchent jamais ensemble.
     *
     * Ré-entrant : un second accès à un fichier déjà complet s'ouvre
     * localement, sans réseau — `forcer = true` impose un nouveau
     * téléchargement. L'écriture est atomique : chaque tentative écrit dans
     * son propre temporaire (suffixe aléatoire — deux téléchargements du
     * même fichier ne se volent jamais un flux, ni ne tronquent celui de
     * l'autre) renommé sur la cible seulement à pleine réussite ; la cible
     * n'est jamais effacée au préalable (rename(2) la remplace), un échec
     * ne la remplace donc jamais par un fichier entamé, ni ne laisse de
     * reliquat.
     *
     * Les fichiers des versions antérieures de l'app portaient un nom sans
     * empreinte : après cette mise à jour ils ne correspondent plus à rien,
     * deviennent orphelins et sont effacés par PurgeMedias à la prochaine
     * connexion ou déconnexion.
     */
    suspend fun télécharger(
        context: Context,
        url: String,
        nomSouhaité: String,
        forcer: Boolean = false,
    ): File = withContext(Dispatchers.IO) {
        val cible = File(
            dossierDocuments(context),
            IdentiteMedias.fichierÀEmpreinte(nomFichierSain(nomSouhaité, url), IdentiteMedias.clé(url)),
        )
        // Réemploi : le fichier complet est déjà là, aucune raison de le
        // retélécharger (forcer passe outre).
        if (!forcer && cible.exists() && cible.length() > 0) return@withContext cible
        // Reliquat de l'ancien modèle (nom dérivé de la cible, sans suffixe) :
        // plus personne ne le crée, plus personne ne l'attend.
        File(cible.parentFile, "${cible.name}.part").delete()
        // Temporaire unique à cette tentative.
        val entier = File(
            cible.parentFile,
            cible.name + "." + java.util.UUID.randomUUID().toString().take(8) + ".part",
        )
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "GWSPlus/${BuildConfig.VERSION_NAME}")
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Téléchargement impossible (${resp.code})")
                resp.body.byteStream().use { entrée ->
                    entier.outputStream().use { sortie -> entrée.copyTo(sortie) }
                }
            }
            // rename(2) remplace la cible existante : ne jamais l'effacer
            // avant, au risque de perdre les deux si le renommage échoue.
            if (!entier.renameTo(cible)) error("Écriture impossible (renommage)")
        } finally {
            // Échec (y compris renommage refusé) : rien d'entamé ne doit
            // survivre. Après réussite le temporaire n'existe plus — renommé
            // sur la cible — et ce nettoyage ne trouve rien.
            if (entier.exists()) entier.delete()
        }
        cible
    }

    /**
     * Télécharge dans le dossier public « Downloads/gws-plus » (MediaStore,
     * API 29+) et retourne l'URI du fichier enregistré — null en échec, la
     * ligne d'UI garde son état et le lot continue. En deçà d'API 29 (pas de
     * MediaStore.Downloads) : repli sur le cache privé, URI FileProvider.
     *
     * Cohérence (issue #108) : `filesDir/telechargements-publics.json` relie
     * l'identité stable de la ressource au nom affiché retenu — le second
     * accès retrouve la ligne MediaStore existante et la rend telle quelle,
     * sans ré-insérer ni retélécharger (fin des copies « devoir (1).pdf » à
     * chaque ouverture d'écran). Le registre n'est écrit qu'après un
     * téléchargement réussi — jamais sur un échec — et il conserve le nom
     * réellement obtenu par la ligne.
     *
     * Garanties :
     *  - tout le parcours (lecture du registre → écriture) est sérialisé par
     *    un verrou : deux appels simultanés ne peuvent plus perdre l'entrée
     *    de l'autre en se renvoyant un registre périmé ;
     *  - `forcer = true` ne détruit plus l'ancien fichier : la nouvelle ligne
     *    est inscrite et remplie d'abord, l'ancienne copie complète n'est
     *    supprimée qu'après la réussite, puis la nouvelle reprend le nom
     *    souhaité — un échec laisse l'ancien fichier intact ;
     *  - un reliquat « en attente » (IS_PENDING = 1) est un reste de notre
     *    téléchargement interrompu : il est effacé et le nom redevient
     *    libre, sans disambiguation par empreinte — seule une ligne
     *    COMPLÈTE compte comme occupée.
     *
     * Un fichier déjà présent dans le dossier mais pas encore au registre
     * (ancienne version de l'app, ou registre purgé à la connexion) n'est
     * jamais réclamé : le nom est disambigué par empreinte, ce qui laisse une
     * fois une copie de l'ancien fichier — acceptable, et documenté ici.
     */
    suspend fun téléchargerPublic(
        context: Context,
        url: String,
        nomSouhaité: String,
        forcer: Boolean = false,
    ): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Repli privé : télécharger applique son propre nom empreinté —
            // on lui passe le nom souhaité d'origine, jamais pré-haché.
            return@withContext runCatching { télécharger(context, url, nomSouhaité, forcer) }
                .getOrNull()?.let {
                    FileProvider.getUriForFile(context, "${context.packageName}.files", it)
                }
        }
        verrou.withLock {
            val résolveur = context.contentResolver
            val identité = IdentiteMedias.clé(url)
            val registre = lireRegistrePublic(context)
            val nomDeBase = nomFichierSain(nomSouhaité, url)

            // Nom affiché retenu.
            val nom: String
            if (!forcer && registre.containsKey(identité)) {
                val nomEnregistre = registre.getValue(identité)
                // Un reliquat en attente sous ce nom est notre téléchargement
                // interrompu : effacé, il n'empêche ni le réemploi ni le
                // retéléchargement sous le nom déjà promis.
                effacerEnAttente(résolveur, nomEnregistre)
                // Réemploi : la ligne complète du nom enregistrée existe
                // encore → rendue telle quelle, sans insert ni GET.
                lignePublique(résolveur, nomEnregistre, enAttenteInclus = false)
                    ?.let { return@withContext it }
                // Ligne disparue : retéléchargement sous le même nom.
                nom = nomEnregistre
            } else {
                // Seule une ligne COMPLÈTE rend le nom occupé (autrui, ou
                // fichier d'origine inconnue) : un reliquat en attente est
                // le nôtre, effacé — le nom redevient libre, sans empreinte.
                effacerEnAttente(résolveur, nomDeBase)
                val occupé = lignePublique(résolveur, nomDeBase, enAttenteInclus = false) != null
                nom = RegistrePublics.choisirNom(identité, nomDeBase, registre, occupé)
                if (!forcer && nom != nomDeBase) {
                    // Nom empreinté déjà présent : c'est notre fichier (l'empreinte
                    // porte l'identité) — rendu sans retéléchargement.
                    lignePublique(résolveur, nom, enAttenteInclus = false)
                        ?.let { return@withContext it }
                }
            }
            // La nouvelle ligne doit porter le nom choisi : les reliquats en
            // attente partent (personne ne les attend), mais la copie
            // complète — s'il y en a une (forçage) — survit au
            // téléchargement et n'est supprimée qu'après la réussite. Les
            // lignes sont relevées avant l'insert : c'est elle qui obtient
            // le nom si la place se libère.
            effacerEnAttente(résolveur, nom)
            val anciennes = lignesPubliques(résolveur, nom).filter { !it.enAttente }
            val mime = mimeDeBase(nom.substringAfterLast('.', ""))
            val valeurs = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nom)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, DOSSIER_PUBLIC)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = résolveur.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valeurs)
                ?: return@withContext null
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "GWSPlus/${BuildConfig.VERSION_NAME}")
                    .build()
                httpPublic.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Téléchargement impossible (${resp.code})")
                    résolveur.openOutputStream(uri)?.use { sortie ->
                        resp.body.byteStream().use { entrée -> entrée.copyTo(sortie) }
                    } ?: error("Sortie indisponible")
                }
                val fini = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                résolveur.update(uri, fini, null, null)
            } catch (annulation: java.util.concurrent.CancellationException) {
                // L'annulation (déconnexion, écran qui disparaît) nettoie la
                // ligne en attente mais ne se déguise pas en échec.
                runCatching { résolveur.delete(uri, null, null) }
                throw annulation
            } catch (err: Exception) {
                // Rien d'entamé ne doit traîner dans les Fichiers du
                // téléphone — et l'ancienne copie complète n'a pas été
                // touchée : elle reste en place.
                runCatching { résolveur.delete(uri, null, null) }
                return@withContext null
            }
            // Réussite : l'ancienne copie cède la place seulement maintenant.
            anciennes.forEach { ligne ->
                if (ligne.uri != uri) runCatching { résolveur.delete(ligne.uri, null, null) }
            }
            // MediaStore a pu suffixer la nouvelle ligne « nom (1).pdf » tant
            // que l'ancienne vivait : on la rebaptise, sans jamais faire
            // échouer un téléchargement pour autant.
            runCatching {
                résolveur.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, nom) },
                    null,
                    null,
                )
            }
            // Le registre promet le nom réellement porté par la ligne (le
            // renommage a pu échouer) : sans quoi le second accès ne
            // retrouverait rien et réinscrirait une copie.
            val nomFinal = nomAffiché(résolveur, uri) ?: nom
            // Succès : le nom est promis à cette ressource, à vie (ou jusqu'à la
            // prochaine purge de session). Un registre illisible reste tel quel.
            runCatching { écrireRegistrePublic(context, registre + (identité to nomFinal)) }
            uri
        }
    }

    /** Nom du registre des téléchargements publics, dans filesDir. */
    private const val REGISTRE_PUBLIC = "telechargements-publics.json"

    private fun fichierRegistrePublic(context: Context): File =
        File(context.filesDir, REGISTRE_PUBLIC)

    /** Registre présent (identité → nom affiché) ; absent ou illisible → vide. */
    private fun lireRegistrePublic(context: Context): Map<String, String> =
        RegistrePublics.lire(
            runCatching {
                fichierRegistrePublic(context).takeIf { it.exists() }?.readText()
            }.getOrNull(),
        )

    /** Écriture atomique : le JSON part dans `…-publics.json.tmp` puis prend
     *  la place du fichier en un seul renommage — une interruption en cours
     *  d'écriture laisse soit l'ancien registre, soit le nouveau, jamais un
     *  fichier à moitié écrit (illisible, donc perdu comme s'il était vide). */
    private fun écrireRegistrePublic(context: Context, registre: Map<String, String>) {
        val cible = fichierRegistrePublic(context)
        val temp = File(cible.parentFile, "${cible.name}.tmp")
        temp.delete() // reliquat d'une écriture interrompue
        temp.writeText(RegistrePublics.écrire(registre))
        if (!temp.renameTo(cible)) {
            temp.delete()
            error("Écriture du registre impossible (renommage)")
        }
    }

    /** Oubli du registre (connexion/déconnexion, PurgeMedias) : les noms
     *  enregistrés ne valent plus rien pour la session suivante. Les fichiers
     *  de « Downloads/gws-plus », eux, appartiennent à l'élève — on n'y
     *  touche pas. Sous le verrou : une purge ne doit pas être annulée par le
     *  téléchargement en cours qui réécrirait son registre. */
    suspend fun oublierRegistrePublic(context: Context) {
        verrou.withLock {
            val fichier = fichierRegistrePublic(context)
            fichier.delete()
            File(fichier.parentFile, "${fichier.name}.tmp").delete()
        }
    }

    /** Ligne MediaStore du fichier `nom` dans le dossier de l'app — null si
     *  absente. `enAttenteInclus = false` ne retient que les lignes
     *  COMPLÈTES : un reliquat de téléchargement interrompu n'est pas un
     *  fichier. */
    private fun lignePublique(
        résolveur: ContentResolver,
        nom: String,
        enAttenteInclus: Boolean,
    ): Uri? = lignesPubliques(résolveur, nom)
        .firstOrNull { enAttenteInclus || !it.enAttente }
        ?.uri

    /** Toutes les lignes MediaStore du fichier `nom` dans le dossier de
     *  l'app, avec leur état « en attente » (IS_PENDING). Le chemin est
     *  comparé sans le « / » final que certains Android ajoutent à
     *  RELATIVE_PATH. */
    private fun lignesPubliques(résolveur: ContentResolver, nom: String): List<LignePublique> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_PENDING,
        )
        val trouvées = mutableListOf<LignePublique>()
        résolveur.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(nom),
            null,
        )?.use { curseur ->
            val colId = curseur.getColumnIndex(MediaStore.MediaColumns._ID)
            val colChemin = curseur.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val colAttente = curseur.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
            while (curseur.moveToNext()) {
                val chemin = curseur.getString(colChemin)?.trimEnd('/')
                if (chemin != DOSSIER_PUBLIC) continue
                val enAttente = colAttente >= 0 && curseur.getInt(colAttente) == 1
                trouvées += LignePublique(
                    Uri.withAppendedPath(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        curseur.getString(colId),
                    ),
                    enAttente,
                )
            }
        }
        return trouvées
    }

    /** Efface les reliquats « en attente » sous `nom` : un téléchargement
     *  interrompu n'attend personne et, s'il survivait, forcerait le nom à
     *  jamais (MediaStore le suffixerait, tout nouvel insert deviendrait
     *  « nom (1).pdf »). Aucune copie complète n'est touchée. */
    private fun effacerEnAttente(résolveur: ContentResolver, nom: String) {
        lignesPubliques(résolveur, nom)
            .filter { it.enAttente }
            .forEach { ligne -> runCatching { résolveur.delete(ligne.uri, null, null) } }
    }

    /** Nom réellement porté par la ligne `uri` — MediaStore le suffixe
     *  « (1) » tant qu'une homonyme vit encore dans le dossier. */
    private fun nomAffiché(résolveur: ContentResolver, uri: Uri): String? = runCatching {
        résolveur.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { curseur ->
                val colNom = curseur.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (curseur.moveToFirst() && colNom >= 0) curseur.getString(colNom) else null
            }
    }.getOrNull()

    /** Ligne MediaStore du dossier de l'app : URI + état « en attente ». */
    private data class LignePublique(val uri: Uri, val enAttente: Boolean)

    /** Ouvre un fichier déjà enregistré (URI publique ou FileProvider). */
    fun intentionOuvrirUri(context: Context, uri: Uri): Intent? {
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) intent else intent
    }

    /** Nom de fichier sûr : la pièce jointe peut n'avoir qu'un libellé générique. */
    fun nomFichierSain(nomSouhaité: String, url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('/')
            .substringAfterLast(".")
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
            ?.let { ".$it" } ?: ""
        val base = nomSouhaité
            .replace(Regex("""[/\\?%*:|"<>\p{Cntrl}]"""), " ")
            .trim()
            .take(60)
            .ifEmpty { "document" }
        return if (base.contains('.') && ext.isEmpty()) base else base + ext
    }

    /** Espace d'attente des pièces à envoyer (composeur, issue #10). */
    fun dossierEnvoi(context: Context): File =
        File(context.cacheDir, "envoi").apply { mkdirs() }

    /** Fichier → data URL base64 (`data:<mime>;base64,…`) — le format que le
     *  bundle officiel embarque dans le champ `devoir` de la soumission
     *  (issue #68, `base64File` du FileReader.readAsDataURL d'origine). */
    suspend fun enDataURL(fichier: File): String = withContext(Dispatchers.IO) {
        val mime = mimeDeBase(fichier.extension)
        val base64 = java.util.Base64.getEncoder().encodeToString(fichier.readBytes())
        "data:$mime;base64,$base64"
    }

    /** Mime déduit de l'extension — le sous-ensemble suffisant aux copies. */
    internal fun mimeDeBase(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt" -> "text/plain"
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    /** Copie un document choisi via le sélecteur système (SAF) dans l'espace
     *  d'attente. Retourne null si la lecture échoue. */
    suspend fun copierDepuisSaf(context: Context, uri: Uri): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val résolu = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val colNom = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && colNom >= 0) c.getString(colNom) else null
                }
                val nom = nomFichierSain(résolu ?: "piece-jointe", uri.toString())
                val cible = File(dossierEnvoi(context), nom.uniqueNom())
                context.contentResolver.openInputStream(uri)?.use { entrée ->
                    cible.outputStream().use { sortie -> entrée.copyTo(sortie) }
                } ?: return@runCatching null
                cible
            }.getOrNull()
        }

    /** Limite officielle du composeur « nouveau message » : 1 Mo par pièce
     *  (toast du bundle : « S'il vous plait choisi un fichier moins ou egale 1MB »). */
    fun dépasseLimite1Mo(fichier: File): Boolean = fichier.length() > 1_048_576L

    private fun String.uniqueNom(): String {
        val point = lastIndexOf('.')
        val base = if (point > 0) substring(0, point) else this
        val ext = if (point > 0) substring(point) else ""
        return "$base${System.currentTimeMillis()}$ext"
    }

    /** Ouvre dans le lecteur du système. Retourne null si rien ne sait le lire. */
    fun intentionOuvrir(context: Context, fichier: File): Intent? {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            fichier,
        )
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) intent else intent
    }

    /** Lance l'installateur du système pour un APK téléchargé (issue #46).
     *  Nécessite l'autorisation « apps inconnues » pour GWS+ ; l'écran de
     *  mise à jour propose le réglage quand elle manque. */
    fun intentionInstaller(context: Context, fichier: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            fichier,
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
