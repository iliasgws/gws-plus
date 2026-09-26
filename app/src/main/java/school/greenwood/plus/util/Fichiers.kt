package school.greenwood.plus.util

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
        .build()

    /** Dossier public de l'app dans les Fichiers du téléphone. */
    const val DOSSIER_PUBLIC = "Download/gws-plus"

    fun dossierDocuments(context: Context): File =
        File(context.filesDir, "documents").apply { mkdirs() }

    /** Télécharge dans le cache documents. Ré-entrant : écrase à l'identique. */
    suspend fun télécharger(
        context: Context,
        url: String,
        nomSouhaité: String,
    ): File = withContext(Dispatchers.IO) {
        val cible = File(dossierDocuments(context), nomFichierSain(nomSouhaité, url))
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "GWSPlus/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Téléchargement impossible (${resp.code})")
            resp.body.byteStream().use { entrée ->
                cible.outputStream().use { sortie -> entrée.copyTo(sortie) }
            }
        }
        cible
    }

    /**
     * Télécharge dans le dossier public « Downloads/gws-plus » (MediaStore,
     * API 29+) et retourne l'URI du fichier enregistré — null en échec, la
     * ligne d'UI garde son état et le lot continue. En deçà d'API 29 (pas de
     * MediaStore.Downloads) : repli sur le cache privé, URI FileProvider.
     */
    suspend fun téléchargerPublic(
        context: Context,
        url: String,
        nomSouhaité: String,
    ): Uri? = withContext(Dispatchers.IO) {
        val nom = nomFichierSain(nomSouhaité, url)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext runCatching { télécharger(context, url, nom) }.getOrNull()?.let {
                FileProvider.getUriForFile(context, "${context.packageName}.files", it)
            }
        }
        val résolveur = context.contentResolver
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
            uri
        } catch (err: Exception) {
            // Rien d'entamé ne doit traîner dans les Fichiers du téléphone.
            runCatching { résolveur.delete(uri, null, null) }
            null
        }
    }

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

    suspend fun enDataURL(fichier: File): String = school.greenwood.plus.util.enDataURL(fichier)

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
