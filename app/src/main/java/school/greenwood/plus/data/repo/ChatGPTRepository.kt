package school.greenwood.plus.data.repo

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Post
import school.greenwood.plus.model.PostDetail
import school.greenwood.plus.util.Completude
import school.greenwood.plus.util.Fichiers
import school.greenwood.plus.util.IdentiteMedias
import school.greenwood.plus.util.ItemPartagé
import school.greenwood.plus.util.LigneActualité
import school.greenwood.plus.util.LigneDevoir
import school.greenwood.plus.util.frenchFull
import school.greenwood.plus.util.frenchNumeric
import school.greenwood.plus.util.htmlToPlainMultiline
import school.greenwood.plus.util.médiasÀPartager
import school.greenwood.plus.util.texteChatGPT

/*
 * Issue #150 (v2) : préparation du partage vers ChatGPT. Le prompt est bâti à
 * la volée depuis les données AUXQUELLES l'élève a accès — l'élément ouvert en
 * entier, l'index COMPLET des autres devoirs et des actualités (paginé, jamais
 * deviné), et les pièces jointes TÉLÉCHARGÉES pour être partagées en flux
 * (EXTRA_STREAM), pas seulement listées.
 *
 * Honnêteté (imposée par l'issue) : un index qui n'est pas complet est marqué
 * « PARTIEL » dans le prompt — jamais un « toutes les actualités » mensonger.
 * Un média qu'on n'a pas pu joindre est annoncé « NON joints » — jamais
 * laissé croire qu'il part.
 *
 * Images (v3 de l'issue) : pour l'actualité sélectionnée, on ne s'arrête pas
 * aux pièces jointes du serveur — `<img src>` du HTML, couverture et galerie
 * entrent dans l'union dédupliquée (util/MediasPartagés.kt). L'index des
 * actualités, lui, reste titres et dates.
 */

/** Ce qui part réellement dans l'intention Android. */
data class PartageChatGPT(
    /** Le prompt pré-rempli (jamais envoyé automatiquement). */
    val texte: String,
    /** Fichiers téléchargés dans l'espace privé, à partager en URI FileProvider. */
    val fichiers: List<File> = emptyList(),
)

class ChatGPTRepository(
    private val contexte: Context,
    private val devoirs: DevoirsRepository,
    private val nouveautes: NouveautesRepository,
) {

    /** Détail d'un devoir ouvert → prompt + ses vraies pièces jointes. */
    suspend fun préparerDevoir(devoir: Devoir): PartageChatGPT {
        val (listeDevoirs, complétudeDevoirs) = chargerDevoirs()
        val (listeActualités, complétudeActualités) = chargerActualités()
        val (fichiers, nonJointes) = téléchargerMédias(devoir.attachments)
        val item = ItemPartagé(
            type = "Devoir",
            titre = devoir.title,
            métadonnées = buildList {
                if (devoir.matiere.isNotBlank()) add("Matière : ${devoir.matiere}")
                devoir.categorie?.takeIf { it.isNotBlank() }?.let { add("Catégorie : $it") }
                devoir.enseignant?.takeIf { it.isNotBlank() }?.let { add("Enseignant(e) : $it") }
                devoir.dateRemise?.let { add("Échéance : ${it.frenchNumeric()}") }
                add("État : ${étatDevoir(devoir)}")
                add("Identifiant : ${devoir.id}")
            },
            corps = devoir.description?.takeIf { it.isNotBlank() }?.htmlToPlainMultiline(),
            piècesJointes = devoir.attachments.map { it.name },
            fichiersPartagés = fichiers.map { it.name },
            piècesNonJointes = nonJointes,
        )
        return PartageChatGPT(
            texte = texteChatGPT(
                item = item,
                // L'élément sélectionné est déjà en entier plus haut : jamais
                // dupliqué dans son propre index.
                devoirs = listeDevoirs.filter { it.id != devoir.id }.map(::ligneDevoir),
                actualités = listeActualités.map(::ligneActualité),
                synchronisation = LocalDateTime.now(),
                completudeDevoirs = complétudeDevoirs,
                completudeActualités = complétudeActualités,
            ),
            fichiers = fichiers,
        )
    }

    /** Actualité ouverte → prompt (corps complet) + ses pièces jointes. */
    suspend fun préparerActualité(post: PostDetail): PartageChatGPT {
        val (listeDevoirs, complétudeDevoirs) = chargerDevoirs()
        val (listeActualités, complétudeActualités) = chargerActualités()
        // Images de l'actualité SÉLECTIONNÉE : pièces jointes explicites +
        // couverture + galerie + `<img src>` du corps HTML, dédupliquées
        // (issue #150 v3). L'index des actualités, lui, reste titres/dates.
        val médias = médiasÀPartager(
            pièces = post.files,
            html = post.descriptionHtml,
            imageCouverture = post.image,
            galerie = post.images,
        )
        val (fichiers, nonJointes) = téléchargerMédias(médias)
        val item = ItemPartagé(
            type = "Actualité",
            titre = post.title,
            métadonnées = buildList {
                post.categorie?.takeIf { it.isNotBlank() }?.let { add("Catégorie : $it") }
                post.date?.let { add("Date : ${it.frenchFull()}") }
                post.auteur?.takeIf { it.isNotBlank() }?.let { add("Auteur / autrice : $it") }
                add("Identifiant : ${post.id}")
            },
            corps = post.descriptionHtml?.takeIf { it.isNotBlank() }?.htmlToPlainMultiline(),
            // L'union dédupliquée est le catalogue complet des médias candidats.
            piècesJointes = médias.map { it.name },
            fichiersPartagés = fichiers.map { it.name },
            piècesNonJointes = nonJointes,
        )
        return PartageChatGPT(
            texte = texteChatGPT(
                item = item,
                devoirs = listeDevoirs.map(::ligneDevoir),
                // L'index des actualités couvre TOUTES celles accessibles,
                // celle ouverte comprise : c'est un catalogue.
                actualités = listeActualités.map(::ligneActualité),
                synchronisation = LocalDateTime.now(),
                completudeDevoirs = complétudeDevoirs,
                completudeActualités = complétudeActualités,
            ),
            fichiers = fichiers,
        )
    }

    /**
     * Index des devoirs : la même liste complète que l'onglet (`devoirs`,
     * `limit = 60` — le serveur n'en expose pas davantage à un parent). Le
     * réseau d'abord, le cache de session en repli : un repli est PARTIEL,
     * il n'a jamais la prétention d'être tout l'accès de l'élève.
     */
    private suspend fun chargerDevoirs(): Pair<List<Devoir>, Completude> {
        val duRéseau = runCatching { devoirs.liste() }.getOrNull()
        if (duRéseau != null) return duRéseau to Completude.COMPLÈTE
        val duCache = runCatching { devoirs.listeEnCache() }.getOrNull() ?: emptyList()
        return duCache to Completude.PARTIELLE
    }

    /**
     * Index des actualités : paginé jusqu'au bout (pagination 1-based, départ
     * = longueur cumulée + 1). Un plafond de pages borne la taille et le coût
     * réseau ; l'atteindre bascule l'index en PARTIEL — jamais « complet » à
     * moitié vrai. Hors connexion, le cache de session (première page) sert,
     * marqué PARTIEL.
     */
    private suspend fun chargerActualités(): Pair<List<Post>, Completude> {
        val cumul = mutableListOf<Post>()
        try {
            while (cumul.size < PAGES_MAX * PAR_PAGE) {
                val départ = if (cumul.isEmpty()) 0 else cumul.size + 1
                val bloc = nouveautes.liste(départ = départ, limite = PAR_PAGE)
                if (bloc.isEmpty()) return cumul to Completude.COMPLÈTE
                cumul += bloc
                if (bloc.size < PAR_PAGE) return cumul to Completude.COMPLÈTE
            }
            // Plafond atteint : on sait qu'il existe au moins une page de plus.
            return cumul to Completude.PARTIELLE
        } catch (err: Exception) {
            val duCache = runCatching { nouveautes.listeEnCache() }.getOrNull() ?: emptyList()
            return (cumul + duCache).distinctBy { it.id } to Completude.PARTIELLE
        }
    }

    /**
     * Téléchargement borné des médias à joindre. Les échecs ne sont JAMAIS
     * perdus : un fichier qu'on n'a pas pu joindre (réseau, URL, data URI
     * trop grosse, plafond atteint) est renvoyé pour être annoncé « NON joints »
     * dans le prompt — jamais laissé croire qu'il part.
     */
    private suspend fun téléchargerMédias(médias: List<Attachment>): Pair<List<File>, List<String>> {
        val fichiers = mutableListOf<File>()
        val nonJointes = mutableListOf<String>()
        for (média in médias) {
            if (fichiers.size >= PIÈCES_MAX) {
                nonJointes += média.name
                continue
            }
            val fichier = téléchargerMédia(média)
            if (fichier != null) fichiers += fichier else nonJointes += média.name
        }
        return fichiers to nonJointes
    }

    private suspend fun téléchargerMédia(média: Attachment): File? =
        if (média.url.startsWith("data:", ignoreCase = true)) {
            runCatching { décoderDataURL(média.url, média.name) }.getOrNull()
        } else {
            runCatching { Fichiers.télécharger(contexte, média.url, média.name) }.getOrNull()
        }

    /**
     * `data:image/…;base64,…` → fichier dans le dossier FileProvider. Borné :
     * au-delà de [TAILLE_DATA_MAX] de base64, l'image est refusée et annoncée.
     */
    private fun décoderDataURL(url: String, nom: String): File {
        val virgule = url.indexOf(',')
        require(virgule > 0 && url.substring(0, virgule).contains(";base64")) { "data URI non base64" }
        val brut = url.substring(virgule + 1)
        require(brut.length <= TAILLE_DATA_MAX) { "data URI trop volumineuse" }
        val octets = java.util.Base64.getDecoder().decode(brut)
        val cible = File(
            Fichiers.dossierDocuments(contexte),
            IdentiteMedias.fichierÀEmpreinte(nom, IdentiteMedias.clé(url)),
        )
        cible.outputStream().use { it.write(octets) }
        return cible
    }

    private fun ligneDevoir(d: Devoir) = LigneDevoir(
        titre = d.title,
        matière = d.matiere.takeIf { it.isNotBlank() },
        échéance = d.dateRemise?.frenchNumeric(),
        état = étatDevoir(d),
        identifiant = d.id,
    )

    private fun ligneActualité(p: Post) = LigneActualité(
        titre = p.title,
        date = p.date?.toLocalDate()?.frenchNumeric(),
        categorie = p.categorie,
        identifiant = p.id,
    )

    private fun étatDevoir(d: Devoir): String = when {
        d.fait -> "Travail fait (connu de l'école)"
        d.faitLocal -> "Fait pour moi (local, invisible à l'école)"
        else -> "À faire"
    }

    private companion object {
        /** Pages de 10 (pas de 50 : la taille réelle de page est vérifiée à 10). */
        const val PAR_PAGE = 10
        const val PAGES_MAX = 20
        const val PIÈCES_MAX = 4
        /** Data URIs acceptées : 4 Mo de base64 (~3 Mo décodés). */
        const val TAILLE_DATA_MAX = 4 * 1024 * 1024
    }
}
