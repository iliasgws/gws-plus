package school.greenwood.plus.util

import java.security.MessageDigest

/*
 * Identité stable d'un média signé (issue #108).
 *
 * `media.boti.education` signe chaque URL par un jeton jetable collé en tête
 * de chemin (`/view/<jeton>.<timestamp>/<taille>/<chemin>`, docs/api/
 * ENDPOINT-MAP.md) : la même ressource arrive avec une URL différente à chaque
 * réponse du serveur (ré-signature ~15–20 min), donc une clé de cache prise sur
 * l'URL brute manque le cache à chaque écran. La clé porte sur la ressource
 * (hôte + chemin réel, la signature retirée) — jamais sur le jeton, qu'on ne
 * journalise pas et qui expire. Toute URL qui ne suit pas la forme connue est
 * rendue telle quelle : comportement conservateur, identique à aujourd'hui.
 */
object IdentiteMedias {

    // /view/ suivi du jeton (base64, peut contenir + / = et donc plusieurs
    // segments), d'un point, d'un timestamp numérique, puis la vraie voie.
    private val signée = Regex("""^(https?://[^/]+/view/)(.+?)\.(\d+)/(.*)$""")

    /** Clé de cache : URL sans la signature. Rend l'URL intacte si la forme
     *  n'est pas celle d'un média signé connu. */
    fun clé(url: String): String {
        val m = signée.find(url) ?: return url
        return m.groupValues[1] + m.groupValues[4]
    }

    /** Empreinte courte (8 hex de SHA-256) d'une identité — pour suffixer un
     *  nom de fichier sans jamais y glisser le jeton signé. */
    fun empreinte(identité: String): String {
        val d = MessageDigest.getInstance("SHA-256")
            .digest(identité.toByteArray(Charsets.UTF_8))
        return buildString { for (b in d.take(4)) append("%02x".format(b)) }
    }

    /** Nom de fichier sûr + empreinte : deux pièces homonymes de contenus
     *  différents (« Photo.jpg ») ne partagent jamais le même fichier local. */
    fun fichierÀEmpreinte(nom: String, identité: String): String {
        val point = nom.lastIndexOf('.')
        val (base, ext) = if (point > 0) nom.substring(0, point) to nom.substring(point)
        else nom to ""
        return "$base-${empreinte(identité)}$ext"
    }
}
