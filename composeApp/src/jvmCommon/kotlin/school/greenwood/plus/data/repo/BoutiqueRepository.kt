package school.greenwood.plus.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.model.CantineJour
import school.greenwood.plus.model.CommandeBoutique
import school.greenwood.plus.model.ProduitBoutique
import school.greenwood.plus.model.ProduitDétail
import school.greenwood.plus.model.RésultatCommande
import school.greenwood.plus.model.RubriqueBoutique
import school.greenwood.plus.util.extractDate
import school.greenwood.plus.util.frenchNumeric

/*
 * La Boutique de l'école (docs/product/DESIGN.md §4), une seule route `shop`
 * pilotée par un drapeau de mode. Formes vérifiées en sonde le 21/09/2026 sur
 * un vrai compte : catalogue (rubrique + search), détail produit (product),
 * historique (new_history), commande directe (POST product) et suppression
 * d'une commande (POST delete_history).
 *
 * Deux faits sonde qui décident du périmètre :
 * - le POST d'un produit crée la commande immédiatement (état « en-cours »,
 *   alerte « Commande passée avec succès ») — le panier serveur (cart=true)
 *   reste vide dans ce déploiement, il n'est pas exposé ;
 * - le GET catalogue sans `rubrique` fait cracher au serveur des notices PHP
 *   avant le JSON (sonde du 21/09/2026) : `rubrique` et `search` partent
 *   toujours, « -1 » et « » en valeurs par défaut.
 */

/** Une page de catalogue : la liste des produits d'une rubrique, la liste
 *  complète des rubriques (toujours renvoyée), le compteur de panier et le
 *  planning cantine (renseigné sur la rubrique « Repas invité »). */
data class CatalogueBoutique(
    val produits: List<ProduitBoutique>,
    val rubriques: List<RubriqueBoutique>,
    val compteurPanier: Int,
    val cantines: List<CantineJour> = emptyList(),
)

class BoutiqueRepository(
    private val client: BotiClient,
    private val session: SessionStore,
) {

    /**
     * Signal « la liste des commandes a bougé » (commande passée, article ou
     * commande supprimé) — le catalogue et l'historique s'y réabonnent pour
     * rafraîchir au retour, comme le signal de quiz en jeu de la coquille.
     */
    private val _commandesChangées = MutableStateFlow(0)
    val commandesChangées: StateFlow<Int> = _commandesChangées.asStateFlow()
    private fun signalerChangement() {
        _commandesChangées.value += 1
    }

    /** Catalogue d'une rubrique. Serveur : GET `shop?rubrique=…&search=…`. */
    suspend fun catalogue(rubrique: String = "-1", recherche: String = ""): CatalogueBoutique {
        val rep = client.get(
            "shop",
            mapOf(
                "rubrique" to rubrique,
                "search" to recherche,
            ),
        )
        return CatalogueBoutique(
            produits = Normalizers.arr(rep, "products")
                .mapNotNull { (it as? JsonObject)?.let(Normalizers::produitCatalogue) },
            rubriques = Normalizers.arr(rep, "rubriques")
                .mapNotNull { (it as? JsonObject)?.let(Normalizers::rubrique) },
            compteurPanier = Normalizers.int(rep, "cart_count") ?: 0,
            cantines = Normalizers.arr(rep, "cantines")
                .mapNotNull { (it as? JsonObject)?.let(Normalizers::cantine) },
        )
    }

    /** Détail d'un produit ; [commandeId] le passe en mode modification d'une
     *  commande existante (le serveur renvoie alors `commande{size,qte,comment}`). */
    suspend fun détail(produitId: String, commandeId: String? = null): ProduitDétail {
        val rep = client.get(
            "shop",
            buildMap {
                put("product", produitId)
                commandeId?.let { put("commande", it) }
            },
        )
        return Normalizers.produitDétail(rep) ?: error("Produit illisible")
    }

    /**
     * Passer commande — le POST crée la commande immédiatement (sonde du
     * 21/09/2026 : alerte « Commande passée avec succès », état « en-cours »).
     * Avec [commandeId], c'est une modification de la commande existante
     * (même POST que le bundle, qui repart du détail en mode `commande`).
     * [prix] est le prix unitaire choisi (variante sinon produit) — c'est le
     * serveur qui facture, la valeur n'est que l'écho du bundle officiel.
     */
    suspend fun commander(
        produitId: String,
        varianteId: String?,
        taille: String?,
        quantité: Int,
        commentaire: String,
        prix: String?,
        commandeId: String? = null,
    ): RésultatCommande {
        val s = session.state.first() ?: error("Session absente")
        val rep = client.post(
            endpoint = "shop",
            fields = buildMap {
                put("product", produitId)
                commandeId?.let { put("commande", it) }
                varianteId?.let { put("variants", it) }
                put("size", taille ?: "")
                put("quantity", quantité.toString())
                put("comment", commentaire)
                put("price", prix ?: "")
                put("eleve_id", s.eleveId)
                put("user_id", s.userId)
                put("parent_id", s.parentId)
            },
        )
        // L'échec métier peut venir en enveloppe `error` (BotiErreur) ou en
        // alerte portant un drapeau `error` — le bundle teste les deux.
        return RésultatCommande(
            succès = Normalizers.bool(rep, "error") != true,
            titre = Normalizers.str(rep, "header_msg"),
            message = Normalizers.str(rep, "message"),
        ).also { if (it.succès) signalerChangement() }
    }

    /**
     * Réserver un repas invité : même POST que la boutique (sonde du
     * 21/09/2026 — le produit 25 « Repas invité » se commande comme un autre,
     * variante unique « Commander »). Le jour choisi part dans le
     * commentaire de la commande, seule mention du jour côté client — le
     * libellé de réservation du planning (« Réservé 1/1 ») ne compte que les
     * commandes validées.
     */
    suspend fun commanderRepas(produitId: String, jourValeur: String?): RésultatCommande {
        val détail = détail(produitId)
        val variante = détail.variantes.firstOrNull()
        return commander(
            produitId = produitId,
            varianteId = variante?.id,
            taille = null,
            quantité = 1,
            commentaire = commentaireRepas(jourValeur),
            prix = variante?.montant ?: détail.prixRaw,
        )
    }

    /** Historique des commandes, la plus récente d'abord (GET `new_history`). */
    suspend fun historique(): List<CommandeBoutique> {
        val rep = client.get("shop", mapOf("new_history" to "true"))
        return Normalizers.arr(rep, "products")
            .mapNotNull { (it as? JsonObject)?.let(Normalizers::commande) }
    }

    /**
     * Suppression d'une commande — entière, ou d'un seul article avec
     * [articleId] (POST `delete_history`, mêmes drapeaux `can_delete` que le
     * bundle). Réponse vide observée en sonde : `[]` quand ça passe.
     */
    suspend fun supprimer(commandeId: String, articleId: String? = null): Boolean {
        val s = session.state.first() ?: error("Session absente")
        val rep = client.post(
            endpoint = "shop",
            fields = buildMap {
                put("commande", commandeId)
                articleId?.let { put("detailsId", it) }
                put("delete_history", "true")
                put("eleve_id", s.eleveId)
                put("user_id", s.userId)
                put("parent_id", s.parentId)
            },
        )
        return Normalizers.bool(rep, "error") != true
            .also { succès -> if (succès) signalerChangement() }
    }
}

/**
 * Le commentaire qui porte le jour du repas (« Repas invité du 23/09/2026 »).
 * L'ISO du planning (`date.value`) passe par le parseur tolérant ; sans date
 * lisible, l'ISO brute — testé.
 */
fun commentaireRepas(jourValeur: String?): String {
    val jour = jourValeur?.let { extractDate(it) }
    return if (jour != null) "Repas invité du ${jour.frenchNumeric()}"
    else "Repas invité du ${jourValeur ?: ""}".trim()
}
