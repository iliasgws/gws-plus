package school.greenwood.plus.util

import school.greenwood.plus.model.PostDetail

/*
 * Issue #148 : bouton « Copier » dans le détail d'une actualité (note
 * d'information). Le texte est construit ici, hors Compose, pour rester
 * testable ; l'écran se contente de le copier et d'afficher un retour visuel.
 */

/** Texte multi-lignes lisible d'une actualité : titre, catégorie, date,
 *  auteur et corps aplati (HTML retiré). */
fun postTexteÀCopier(post: PostDetail): String {
    val entête = buildList {
        add("Actualité : ${post.title.ifBlank { "Actualité" }}")
        post.categorie?.takeIf { it.isNotBlank() }?.let { add("Catégorie : $it") }
        post.date?.let { add("Date : ${it.frenchFull()}") }
        post.auteur?.takeIf { it.isNotBlank() }?.let { add("Par : $it") }
    }.joinToString("\n")
    val corps = post.descriptionHtml?.takeIf { it.isNotBlank() }
        ?.let { it.htmlToPlainMultiline() }
        ?.takeIf { it.isNotBlank() }
    return if (corps != null) "$entête\n\n$corps" else entête
}
