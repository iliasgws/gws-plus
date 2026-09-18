package school.greenwood.plus.util

import android.text.Html
import android.text.Spanned

/*
 * Les annonces arrivent en HTML riche (emojis, <p style="text-align:justify">,
 * sauts de ligne). On le rend avec le moteur texte Android, puis on sait aussi
 * l'aplatir en texte brut pour les aperçus du flux.
 */

fun String.asHtml(): Spanned =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
    } else {
        @Suppress("DEPRECATION")
        Html.fromHtml(this)
    }

/** HTML → texte brut sur une ligne, pour les aperçus compacts du registre. */
fun String.htmlToPlainSingleLine(): String =
    asHtml().toString()
        .replace(Regex("""\s*\n+\s*"""), " ")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()

/** HTML → texte lisible avec paragraphes, pour les corps d'annonce. */
fun String.htmlToPlainMultiline(): String =
    asHtml().toString()
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
