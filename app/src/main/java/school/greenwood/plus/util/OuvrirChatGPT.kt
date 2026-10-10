package school.greenwood.plus.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/*
 * Issue #150 : « Ouvrir dans ChatGPT » à côté du bouton « Copier ». Le même
 * texte que la copie part en prompt PRÉ-REMPLI dans l'app ChatGPT Android —
 * ACTION_SEND `text/plain` + `EXTRA_TEXT`, paquet `com.openai.chatgpt`
 * (commande ADB testée sur l'appareil). Rien n'est envoyé sans l'utilisateur.
 *
 * Repli : si ChatGPT n'est pas installé, la feuille de partage système
 * ouvre avec le même texte — le bouton n'est jamais mort. L'appelant n'affiche
 * un message que si même ça échoue (aucun Context de widget ici : les toasts
 * restent dans l'UI).
 */

/** Paquet de l'app ChatGPT (Android) — testé en ADB, cf. issue #150. */
private const val PAQUET_CHATGPT = "com.openai.chatgpt"

/** Issue #150 : où le texte a fini. */
enum class RésultatOuvertureChatGPT {
    /** Ouvert directement dans ChatGPT. */
    CHATGPT,

    /** ChatGPT absent : ouvert dans la feuille de partage système. */
    PARTAGE,

    /** Rien n'a pu s'ouvrir — l'appelant annonce l'échec. */
    ÉCHEC,
}

/** Intention `ACTION_SEND` qui pré-remplit le composeur ChatGPT avec [texte]. */
fun intentionChatGPT(texte: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        setPackage(PAQUET_CHATGPT)
        putExtra(Intent.EXTRA_TEXT, texte)
    }

/** Intention de repli, sans paquet : n'importe quelle app de partage. */
fun intentionPartage(texte: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texte)
    }

/**
 * Ouvre ChatGPT avec [texte] pré-rempli, jamais envoyé. Repli sur la feuille
 * de partage système quand ChatGPT est absent.
 */
fun ouvrirChatGPT(context: Context, texte: String): RésultatOuvertureChatGPT =
    try {
        context.startActivity(intentionChatGPT(texte))
        RésultatOuvertureChatGPT.CHATGPT
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent.createChooser(intentionPartage(texte), null))
            RésultatOuvertureChatGPT.PARTAGE
        } catch (e2: ActivityNotFoundException) {
            RésultatOuvertureChatGPT.ÉCHEC
        }
    }
