package school.greenwood.plus.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/*
 * Issue #150 : « Ouvrir dans ChatGPT » à côté du bouton « Copier ». Le prompt
 * (élément sélectionné en entier + index, cf. util/TexteChatGPT.kt) part en
 * PRÉ-REMPLISSAGE dans l'app ChatGPT Android — ACTION_SEND `text/plain` +
 * `EXTRA_TEXT`, paquet `com.openai.chatgpt` (commande ADB testée sur
 * l'appareil). Rien n'est envoyé sans l'utilisateur.
 *
 * Pièces jointes (v2 de l'issue) : un fichier → `ACTION_SEND` + `EXTRA_STREAM`,
 * plusieurs → `ACTION_SEND_MULTIPLE`, URI FileProvider avec accord de lecture
 * (`FLAG_GRANT_READ_URI_PERMISSION`) et type MIME déduit de l'extension.
 *
 * Repli : ChatGPT absent → feuille de partage système avec le même texte et
 * les mêmes fichiers. L'appelant n'affiche un message que si même ça échoue —
 * aucun Context de widget ici, les toasts restent dans l'UI.
 */

/** Paquet de l'app ChatGPT (Android) — testé en ADB, cf. issue #150. */
private const val PAQUET_CHATGPT = "com.openai.chatgpt"

/** Issue #150 : où le partage a fini. */
enum class RésultatOuvertureChatGPT {
    /** Ouvert directement dans ChatGPT. */
    CHATGPT,

    /** ChatGPT absent : ouvert dans la feuille de partage système. */
    PARTAGE,

    /** Rien n'a pu s'ouvrir — l'appelant annonce l'échec. */
    ÉCHEC,
}

/** Type MIME d'un fichier localisé par FileProvider, déduit de son nom. */
private fun mimeDe(context: Context, uri: Uri, fichier: File): String {
    context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }?.let { return it }
    val extension = fichier.extension.lowercase()
    return MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(extension)
        ?: "*/*"
}

/**
 * Intention de partage : texte pré-rempli + fichiers. Un seul fichier →
 * `ACTION_SEND` + `EXTRA_STREAM`, plusieurs → `ACTION_SEND_MULTIPLE`, aucun →
 * simple `text/plain`. La lecture des URI est accordée à l'application cible.
 */
fun intentionPartage(texte: String, fichiers: List<Pair<Uri, String>>): Intent {
    val uris = fichiers.map { it.first }
    val intention = Intent(
        if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND,
    ).apply {
        type = when {
            uris.isEmpty() -> "text/plain"
            uris.size == 1 -> fichiers.first().second.ifBlank { "*/*" }
            else -> "*/*"
        }
        putExtra(Intent.EXTRA_TEXT, texte)
        when {
            uris.size == 1 -> putExtra(Intent.EXTRA_STREAM, uris.first())
            uris.size > 1 -> putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return intention
}

/** URI FileProvider d'un fichier de l'espace privé, ou null si illisible. */
fun uriFileProvider(context: Context, fichier: File): Uri? =
    runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.files", fichier)
    }.getOrNull()

/**
 * Ouvre ChatGPT avec le prompt [texte] pré-rempli, jamais envoyé, et les
 * [fichiers] en pièce jointe réelle. Repli sur la feuille de partage système
 * quand ChatGPT est absent.
 */
fun ouvrirChatGPT(
    context: Context,
    texte: String,
    fichiers: List<File> = emptyList(),
): RésultatOuvertureChatGPT {
    val pièces: List<Pair<Uri, String>> = fichiers.mapNotNull { fichier ->
        uriFileProvider(context, fichier)?.let { it to mimeDe(context, it, fichier) }
    }
    val versChatGPT = intentionPartage(texte, pièces).apply { setPackage(PAQUET_CHATGPT) }
    val sansPaquet = intentionPartage(texte, pièces)
    return try {
        context.startActivity(versChatGPT)
        RésultatOuvertureChatGPT.CHATGPT
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent.createChooser(sansPaquet, null))
            RésultatOuvertureChatGPT.PARTAGE
        } catch (e2: ActivityNotFoundException) {
            RésultatOuvertureChatGPT.ÉCHEC
        }
    }
}
