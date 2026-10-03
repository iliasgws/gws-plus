package school.greenwood.plus.util

import kotlinx.serialization.json.Json

/*
 * Registre des téléchargements publics (issue #108).
 *
 * « Downloads/gws-plus » doit contenir une copie par ressource : le registre
 * relie l'identité stable (IdentiteMedias.clé — jamais le jeton signé) au nom
 * affiché retenu, pour retrouver la ligne MediaStore existante au lieu d'en
 * insérer une nouvelle à chaque appel (« devoir (1).pdf »).
 *
 * Le fichier JSON vit dans filesDir/telechargements-publics.json et n'est
 * écrit qu'après un téléchargement réussi ; PurgeMedias l'efface à la connexion
 * et à la déconnexion, alors que les fichiers de « Downloads/gws-plus »
 * appartiennent à l'élève et ne sont pas purgés.
 *
 * Tout ici est pur (ni Context, ni I/O) : sélection du nom et JSON se
 * testent en unitaire.
 */
internal object RegistrePublics {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Nom affiché à donner à la ressource :
     *  1. l'identité est déjà au registre → le nom qu'elle porte, quel que
     *     soit le reste (réemploi ou retéléchargement sous le même nom) ;
     *  2. sinon le nom souhaité, sauf s'il est pris — déjà déclaré à une
     *     autre identité, ou fichier présent dans le dossier sans être le
     *     nôtre (`nomOccupé`, origine inconnue) : on disambigue par
     *     empreinte. On ne réclame ni n'écrase jamais le fichier d'autrui ;
     *     le prix est une copie de plus, une fois, pour l'ancien fichier.
     */
    fun choisirNom(
        identité: String,
        nomSouhaité: String,
        registre: Map<String, String>,
        nomOccupé: Boolean,
    ): String {
        registre[identité]?.let { return it }
        val pris = nomOccupé || registre.containsValue(nomSouhaité)
        return if (pris) IdentiteMedias.fichierÀEmpreinte(nomSouhaité, identité) else nomSouhaité
    }

    /** Lecture tolérante : JSON absent, vide ou illisible → registre vide. */
    fun lire(brut: String?): Map<String, String> {
        val texte = brut ?: return emptyMap()
        if (texte.isBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(texte) }
            .getOrDefault(emptyMap())
    }

    /** Sérialisation du registre complet (identité → nom affiché). */
    fun écrire(registre: Map<String, String>): String = json.encodeToString(registre)
}
