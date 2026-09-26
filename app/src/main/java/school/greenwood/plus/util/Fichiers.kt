package school.greenwood.plus.util

import android.content.Context
import android.content.Intent
import android.net.Uri
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
 */
object Fichiers {

    private val http = OkHttpClient()

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
    private fun mimeDeBase(ext: String): String = when (ext.lowercase()) {
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
