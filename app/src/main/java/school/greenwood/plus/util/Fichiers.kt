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
}
