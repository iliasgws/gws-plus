package school.greenwood.plus.data.repo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import school.greenwood.plus.data.api.CommunApi
import school.greenwood.plus.data.api.CommunErreur
import school.greenwood.plus.data.api.CommunParams
import school.greenwood.plus.data.api.JetonRévoqué
import school.greenwood.plus.data.api.Lecture
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.data.session.CompteCommunautaire
import school.greenwood.plus.model.CibleSignalement
import school.greenwood.plus.model.CorrectionHoraire
import school.greenwood.plus.model.DevoirSuggéré
import school.greenwood.plus.model.PièceJointeCommunautaire
import java.io.File
import school.greenwood.plus.model.FiltreHoraire
import school.greenwood.plus.model.Mentions
import school.greenwood.plus.model.PageCommunautaire
import school.greenwood.plus.model.ProblèmeHoraire
import school.greenwood.plus.model.SignalementAbus
import school.greenwood.plus.model.TriDevoirs

/*
 * Le dépôt du serveur communautaire (issue #88 — APP.md du dépôt
 * gws-community-server) : lectures publiques, écritures à jeton, et tout le
 * cycle de vie du compte.
 *
 * Cycle de vie (APP.md §2) :
 *  - première écriture → POST /compte (un seul compte par installation),
 *    puis la notice AVANT le contenu — tant qu'elle n'est pas acceptée,
 *    l'écriture lève `NoticeRequise` ;
 *  - refus → DELETE /compte (révocation) et on oublie tout ;
 *  - 401 → un seul recomplément de compte (jamais en boucle — 10/min et
 *    100/24h par IP), puis la notice réapparaît avant de réessayer.
 */

/** La notice doit être relue et acceptée avant la prochaine écriture. */
class NoticeRequise(val version: String) : CommunErreur(499, "Notice à relire")

/** Limite de texte par champ : le corps complet reste très sous les 10 Ko du serveur. */
private const val LIMITE_TEXTE = 2_000
private const val LIMITE_MATIÈRE = 60

class CommunauteRepository(
    private val api: CommunApi,
    private val compte: CompteCommunautaire,
    /** Hors-ligne (optionnel) : les listes publiques déjà vues. */
    private val caches: CachesSession? = null,
) {

    // — Notices légales ----------------------------------------------------

    /** `GET /health` — test de disponibilité silencieux (Paramètres). */
    suspend fun disponible(): Boolean = api.santé()

    /** `GET /mentions` — mémorise la version servie ; à comparer à l'acceptée. */
    suspend fun mentions(): Mentions {
        val réponse = api.lecture("mentions")
        val mentions = CommunNormalizers.mentions(réponse.corps)
            ?: throw CommunErreur(500, "Notice illisible")
        compte.noterVersionServie(mentions.version)
        return mentions
    }

    /** Vrai si la notice doit s'afficher avant la prochaine écriture :
     *  version jamais acceptée, ou version du serveur changée. */
    suspend fun noticeÀAfficher(): Boolean {
        val demandée = compte.mentionsDemandée() ?: return false
        return compte.mentionsAcceptée() != demandée
    }

    suspend fun accepterNotice() {
        val version = compte.mentionsDemandée() ?: return
        compte.accepterNotices(version)
    }

    /** Refus de la notice OU révocation depuis les Paramètres : le jeton est
     *  révoqué côté serveur et tout est oublié localement (APP.md §2/§4). */
    suspend fun révoquerCompte() {
        runCatching {
            compte.jeton()?.let { api.écriture("DELETE", "compte", corps = null, jeton = it) }
        }
        compte.oublierTout()
    }

    /** Notre empreinte d'auteur — null tant qu'aucun compte n'existe. */
    suspend fun monAuteurId(): String? =
        compte.jeton()?.let { school.greenwood.plus.data.api.auteurIdDuJeton(it) }

    // — Écritures (cycle de vie du jeton) ----------------------------------

    /**
     * Toute écriture passe par là : notice acceptée, jeton présent (créé si
     * besoin), et UN seul recomplément sur 401. [action] reçoit le jeton à
     * mettre dans l'en-tête `Authorization`.
     */
    suspend fun <T> écrire(action: suspend (jeton: String) -> T): T {
        val jeton = compte.jeton() ?: créerCompte()
        if (noticeÀAfficher()) throw NoticeRequise(compte.mentionsDemandée() ?: "")
        return try {
            action(jeton)
        } catch (err: JetonRévoqué) {
            // 401 : révoqué ou inconnu → nouveau compte, puis notice de nouveau.
            compte.oublierJeton()
            val neuf = créerCompte()
            if (noticeÀAfficher()) throw NoticeRequise(compte.mentionsDemandée() ?: "")
            action(neuf)
        }
    }

    private suspend fun créerCompte(): String {
        val réponse = api.écriture("POST", "compte", corps = null, jeton = null)
        val objet = réponse as? JsonObject
            ?: throw CommunErreur(500, "Réponse incompréhensible")
        val jeton = CommunNormalizers.str(objet, "jeton")
            ?: throw CommunErreur(500, "Réponse incompréhensible")
        val version = CommunNormalizers.str(objet, "mentionsVersion")
        compte.enregistrerJeton(jeton, version)
        return jeton
    }

    // — Lectures publiques (sans jeton) ------------------------------------

    suspend fun devoirs(
        tri: TriDevoirs = TriDevoirs.Votes,
        matière: String? = null,
        offset: Int = 0,
        limite: Int = CommunParams.LIMITE_DÉFAUT,
    ): PageCommunautaire<DevoirSuggéré> {
        val réponse = api.lecture(
            "devoirs",
            CommunParams.listes(tri = tri.param, matière = matière, offset = offset, limite = limite),
        )
        if (offset == 0 && matière == null) {
            caches?.écrireDisque("commu-devoirs-$tri", enveloppe(réponse))
        }
        return pageDevoirs(réponse.corps, réponse.total)
    }

    private suspend fun pageDevoirs(corps: kotlinx.serialization.json.JsonElement, total: Int?) =
        PageCommunautaire(
            éléments = CommunNormalizers.liste(corps, CommunNormalizers::devoirSuggéré)
                .map { devoir -> devoir.copy(piècesJointes = urlsComplètes(devoir.piècesJointes)) },
            total = total,
        )

    /** Première page déjà vue, relu hors connexion. */
    suspend fun devoirsEnCache(tri: TriDevoirs = TriDevoirs.Votes): PageCommunautaire<DevoirSuggéré>? =
        deEnvelope("commu-devoirs-$tri")?.let { (corps, total) -> pageDevoirs(corps, total) }

    suspend fun problèmes(
        filtre: FiltreHoraire = FiltreHoraire.Tous,
        offset: Int = 0,
        limite: Int = CommunParams.LIMITE_DÉFAUT,
    ): PageCommunautaire<ProblèmeHoraire> {
        val réponse = api.lecture(
            "edt/problemes",
            CommunParams.listes(état = filtre.param, offset = offset, limite = limite),
        )
        if (offset == 0) caches?.écrireDisque("commu-problemes-$filtre", enveloppe(réponse))
        return PageCommunautaire(
            éléments = CommunNormalizers.liste(réponse.corps, CommunNormalizers::problèmeHoraire),
            total = réponse.total,
        )
    }

    /** Première page déjà vue, relu hors connexion. */
    suspend fun problèmesEnCache(filtre: FiltreHoraire = FiltreHoraire.Tous): PageCommunautaire<ProblèmeHoraire>? =
        deEnvelope("commu-problemes-$filtre")?.let { (corps, total) ->
            PageCommunautaire(
                éléments = CommunNormalizers.liste(corps, CommunNormalizers::problèmeHoraire),
                total = total,
            )
        }

    suspend fun corrections(
        problèmeId: Long? = null,
        offset: Int = 0,
        limite: Int = CommunParams.LIMITE_DÉFAUT,
    ): PageCommunautaire<CorrectionHoraire> {
        val réponse = api.lecture(
            "edt/corrections",
            CommunParams.listes(problèmeId = problèmeId, offset = offset, limite = limite),
        )
        if (offset == 0 && problèmeId == null) caches?.écrireDisque("commu-corrections", enveloppe(réponse))
        return PageCommunautaire(
            éléments = CommunNormalizers.liste(réponse.corps, CommunNormalizers::correctionHoraire),
            total = réponse.total,
        )
    }

    /** Première page déjà vue, relu hors connexion. */
    suspend fun correctionsEnCache(): PageCommunautaire<CorrectionHoraire>? =
        deEnvelope("commu-corrections")?.let { (corps, total) ->
            PageCommunautaire(
                éléments = CommunNormalizers.liste(corps, CommunNormalizers::correctionHoraire),
                total = total,
            )
        }

    /** Signalements d'abus — liste publique SANS pagination (APP.md §3). */
    suspend fun signalements(): List<SignalementAbus> {
        val réponse = api.lecture("signalements")
        caches?.écrireDisque("commu-abus", enveloppe(réponse))
        return CommunNormalizers.liste(réponse.corps, CommunNormalizers::signalementAbus)
    }

    /** Liste déjà vue, relu hors connexion. */
    suspend fun signalementsEnCache(): List<SignalementAbus>? =
        deEnvelope("commu-abus")?.let { (corps, _) ->
            CommunNormalizers.liste(corps, CommunNormalizers::signalementAbus)
        }

    // — Hors-ligne ---------------------------------------------------------

    /** Enveloppe disque : corps brut + total serveur. */
    private fun enveloppe(réponse: Lecture): kotlinx.serialization.json.JsonElement =
        kotlinx.serialization.json.buildJsonObject {
            put("corps", réponse.corps)
            réponse.total?.let { put("total", kotlinx.serialization.json.JsonPrimitive(it)) }
        }

    private suspend fun deEnvelope(nom: String): Pair<kotlinx.serialization.json.JsonElement, Int?>? {
        val brut = runCatching { caches?.lireDisque(nom) }.getOrNull() as? JsonObject ?: return null
        val corps = brut["corps"] ?: return null
        val total = (brut["total"] as? JsonPrimitive)?.content?.toIntOrNull()
        return corps to total
    }

    // — Écritures de contenu -----------------------------------------------

    /** Vote ±1 : un second vote REMPLACE le premier — on reprend le total
     *  renvoyé par le serveur, jamais un incrément local. */
    suspend fun voter(id: Long, vote: Int): DevoirSuggéré = écrire { jeton ->
        val réponse = api.écriture(
            méthode = "POST",
            chemin = "devoirs/$id/vote",
            corps = buildJsonObject { put("vote", JsonPrimitive(vote)) },
            jeton = jeton,
        )
        objetRéponse(réponse).let(CommunNormalizers::devoirSuggéré)
            ?: throw CommunErreur(500, "Réponse incompréhensible")
    }

    suspend fun créerDevoir(
        matière: String,
        contenu: String,
        dateRemise: String?,
        fichiers: List<File> = emptyList(),
    ): DevoirSuggéré =
        écrire { jeton ->
            val réponse = api.écriture(
                méthode = "POST",
                chemin = "devoirs",
                corps = buildJsonObject {
                    put("matière", JsonPrimitive(matière.trim().take(LIMITE_MATIÈRE)))
                    put("contenu", JsonPrimitive(contenu.trim().take(LIMITE_TEXTE)))
                    dateRemise?.trim()?.takeIf { it.isNotEmpty() }?.let { put("dateRemise", JsonPrimitive(it)) }
                },
                jeton = jeton,
            )
            val créé = (objetRéponse(réponse)).let(CommunNormalizers::devoirSuggéré)
                ?: throw CommunErreur(500, "Réponse incompréhensible")
            val pièces = try {
                fichiers.map { fichier ->
                    val résultat = api.téléverser("devoirs/${créé.id}/pieces-jointes", fichier, jeton) as? JsonObject
                        ?: throw CommunErreur(500, "Réponse de pièce jointe incompréhensible")
                    CommunNormalizers.pièceJointe(résultat)
                        ?: throw CommunErreur(500, "Réponse de pièce jointe incompréhensible")
                }
            } catch (erreur: Exception) {
                // Une proposition sans tous ses fichiers serait trompeuse :
                // la suppression serveur retire aussi les uploads déjà reçus.
                runCatching { api.écriture("DELETE", "devoirs/${créé.id}", corps = null, jeton = jeton) }
                throw erreur
            }
            créé.copy(piècesJointes = urlsComplètes(pièces))
        }

    private suspend fun urlsComplètes(pièces: List<PièceJointeCommunautaire>) = pièces.map { pièce ->
        if (pièce.url.startsWith("http://") || pièce.url.startsWith("https://")) pièce
        else pièce.copy(url = api.urlPublique(pièce.url))
    }

    suspend fun supprimerDevoir(id: Long) {
        écrire { jeton -> api.écriture("DELETE", "devoirs/$id", corps = null, jeton = jeton) }
    }

    suspend fun créerProblème(description: String, date: String): ProblèmeHoraire =
        écrire { jeton ->
            val réponse = api.écriture(
                méthode = "POST",
                chemin = "edt/problemes",
                corps = buildJsonObject {
                    put("description", JsonPrimitive(description.trim().take(LIMITE_TEXTE)))
                    put("date", JsonPrimitive(date.trim()))
                },
                jeton = jeton,
            )
            (objetRéponse(réponse)).let(CommunNormalizers::problèmeHoraire)
                ?: throw CommunErreur(500, "Réponse incompréhensible")
        }

    suspend fun supprimerProblème(id: Long) {
        écrire { jeton -> api.écriture("DELETE", "edt/problemes/$id", corps = null, jeton = jeton) }
    }

    suspend fun créerCorrection(
        problèmeId: Long?,
        description: String,
        date: String,
    ): CorrectionHoraire = écrire { jeton ->
        val réponse = api.écriture(
            méthode = "POST",
            chemin = "edt/corrections",
            corps = buildJsonObject {
                problèmeId?.let { put("problèmeId", JsonPrimitive(it)) }
                put("description", JsonPrimitive(description.trim().take(LIMITE_TEXTE)))
                put("date", JsonPrimitive(date.trim()))
            },
            jeton = jeton,
        )
        objetRéponse(réponse).let(CommunNormalizers::correctionHoraire)
            ?: throw CommunErreur(500, "Réponse incompréhensible")
    }

    suspend fun supprimerCorrection(id: Long) {
        écrire { jeton -> api.écriture("DELETE", "edt/corrections/$id", corps = null, jeton = jeton) }
    }

    /** Signaler un abus — `cible` exactement `devoir`, `probleme` ou
     *  `correction` (sans accent). Déjà signalé → 409. */
    suspend fun signaler(cible: CibleSignalement, cibleId: Long, raison: String): SignalementAbus =
        écrire { jeton ->
            val réponse = api.écriture(
                méthode = "POST",
                chemin = "signalements",
                corps = buildJsonObject {
                    put("cible", JsonPrimitive(cible.param))
                    put("cibleId", JsonPrimitive(cibleId))
                    put("raison", JsonPrimitive(raison.trim().take(LIMITE_TEXTE)))
                },
                jeton = jeton,
            )
            (objetRéponse(réponse)).let(CommunNormalizers::signalementAbus)
                ?: throw CommunErreur(500, "Réponse incompréhensible")
        }

    /** Le corps d'une écriture doit être un objet JSON — sinon c'est une
     *  erreur serveur, pas une donnée à deviner. */
    private fun objetRéponse(élément: kotlinx.serialization.json.JsonElement?): JsonObject =
        élément as? JsonObject ?: throw CommunErreur(500, "Réponse incompréhensible")
}
