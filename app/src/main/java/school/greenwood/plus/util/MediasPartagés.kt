package school.greenwood.plus.util

import school.greenwood.plus.model.Attachment

/*
 * Issue #150 (v3) : les images d'une actualité ne se limitent pas aux pièces
 * jointes explicites. Le corps HTML peut référencer des images en ligne
 * (`<img src>` : fichier distant, URL authentifiée, data URI) et le détail
 * porte en plus une image de couverture et une galerie de médias. On expose
 * l'UNION de ces sources, dédupliquée par identité STABLE de ressource
 * (issue #108 : la même photo servie avec deux jetons signés n'est jointe
 * qu'une fois).
 *
 * Périmètre : l'actualité sélectionnée uniquement. L'INDEX des actualités
 * reste des titres et des dates — jamais de corps, jamais de média.
 */

/** Base de résolution des URL relatives du HTML (origine de l'API Boti). */
private const val BASE_HTML = "https://boti.education/p/greenwood/botiapi/"

/** `<img … src="…">` — guillemets simples ou doubles, insensible à la casse. */
private val IMG_SRC = Regex("""(?i)<img\b[^>]*?\ssrc\s*=\s*["']([^"']+)["']""")

private val ORIGINE = Regex("""^https?://[^/]+""")

/**
 * URL d'une image référencée par le HTML, ou null si elle n'est pas
 * téléchargeable (référence vide, `javascript:`, fragment). Les URL
 * absolues, les URL de protocole relatif (`//host/…`), les `data:image/…`
 * et les chemins relatifs résolus contre [base] sont conservés.
 */
fun résoudreURL(src: String?, base: String = BASE_HTML): String? {
    val brut = src?.trim().orEmpty()
    if (brut.isEmpty() || brut.startsWith("#")) return null
    if (brut.startsWith("data:image/", ignoreCase = true)) return brut
    if (brut.startsWith("//")) return "https:$brut"
    val bas = brut.lowercase()
    if (bas.startsWith("http://") || bas.startsWith("https://")) return brut
    if (bas.startsWith("javascript:") || bas.startsWith("about:") || bas.startsWith("blob:")) return null
    val origine = ORIGINE.find(base)?.value ?: return null
    return when {
        brut.startsWith("/") -> origine + brut
        else -> {
            val dossier = base.substringBeforeLast('/', "")
            "$dossier/$brut"
        }
    }
}

/** Noms des images référencées par le corps HTML, dans l'ordre, sans doublon. */
fun imagesHTML(html: String?, base: String = BASE_HTML): List<String> {
    if (html.isNullOrBlank()) return emptyList()
    val identités = mutableSetOf<String>()
    return IMG_SRC.findAll(html)
        .mapNotNull { résoudreURL(it.groupValues[1], base) }
        .filter { identités.add(IdentiteMedias.clé(it)) }
        .toList()
}

/** Nom de fichier déduit d'une URL (dernier segment, sans query/fragment). */
internal fun nomDeURL(url: String): String {
    if (url.startsWith("data:", ignoreCase = true)) return "image-en-ligne.png"
    val segment = url.substringAfterLast('/')
        .substringBefore('?')
        .substringBefore('#')
    return segment.ifBlank { "image-en-ligne" }
}

/**
 * Tous les médias à joindre au partage d'une actualité : pièces jointes
 * explicites, image de couverture, galerie et images en ligne du HTML —
 * dédupliqués par [IdentiteMedias.clé], les pièces connues d'abord (leur nom
 * serveur est conservé), les découvertes ensuite (nom déduit de l'URL).
 */
fun médiasÀPartager(
    pièces: List<Attachment>,
    html: String? = null,
    imageCouverture: String? = null,
    galerie: List<String> = emptyList(),
    base: String = BASE_HTML,
): List<Attachment> {
    val liste = mutableListOf<Attachment>()
    val identités = mutableSetOf<String>()
    fun ajouter(url: String?, nom: String? = null) {
        val cible = url?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (!identités.add(IdentiteMedias.clé(cible))) return
        liste += Attachment(name = nom?.takeIf { it.isNotBlank() } ?: nomDeURL(cible), url = cible)
    }
    pièces.forEach { ajouter(it.url, it.name) }
    ajouter(résoudreURL(imageCouverture, base))
    galerie.forEach { ajouter(résoudreURL(it, base)) }
    imagesHTML(html, base).forEach { ajouter(it) }
    return liste
}
