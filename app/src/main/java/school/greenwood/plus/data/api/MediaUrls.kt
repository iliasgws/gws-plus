package school.greenwood.plus.data.api

import java.net.URLDecoder

/*
 * Les liens média arrivent enrobés dans un Google Docs viewer et DOUBLE
 * encodés (docs/api/BOTI-API.md). Décodage : exactement UNE fois, en gardant
 * %2B / %2F / %3D littéraux — la signature du média porte sur la forme
 * encodée du chemin. Certains liens sont déjà directs : on les rend tels quels.
 */
object MediaUrls {

    fun lienRéel(link: String?): String? {
        if (link.isNullOrBlank()) return null
        if (!link.contains("docs.google.com/viewer")) return link
        val valeur = Regex("""[?&]url=([^&]+)""").find(link)?.groupValues?.get(1)
            ?: return link
        return URLDecoder.decode(valeur, "UTF-8")
    }

    /**
     * Éclate un lien en pièces jointes. L'éditeur colle parfois plusieurs
     * fichiers dans un même chemin, séparés par des virgules
     * (« /…/a.pdf,/…/b.pdf ») — un lien, plusieurs pièces.
     */
    fun piècesJointes(link: String?, nomDeSecours: String? = null): List<Pair<String, String>> {
        val réel = lienRéel(link) ?: return emptyList()

        // head = scheme://host — tout ce qui précède le chemin.
        val débutChemin = réel.indexOf('/', réel.indexOf("//") + 2)
        val head = if (débutChemin < 0) réel else réel.substring(0, débutChemin)
        val chemin = if (débutChemin < 0) "" else réel.substring(débutChemin)

        val segments = chemin.split(",").filter { seg ->
            seg.substringBefore('?').substringAfterLast('/').contains('.')
        }
        if (segments.size <= 1) {
            val nom = nomDeSecours?.takeIf { it.isNotBlank() }
                ?: chemin.substringBefore('?').substringAfterLast('/').ifEmpty { "fichier" }
            return listOf(nom to réel)
        }
        return segments.mapIndexed { i, seg ->
            val brut = seg.substringBefore('?').trimStart('/').substringAfterLast('/')
            val nom = runCatching { URLDecoder.decode(brut, "UTF-8") }
                .getOrDefault(brut)
                .ifBlank { "${nomDeSecours ?: "fichier"}-${i + 1}" }
            nom to (head + seg)
        }
    }
}
