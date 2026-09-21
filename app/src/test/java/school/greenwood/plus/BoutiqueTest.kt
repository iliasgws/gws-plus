package school.greenwood.plus

import school.greenwood.plus.data.repo.Normalizers
import school.greenwood.plus.model.ProduitDétail
import school.greenwood.plus.model.ProduitBoutique
import school.greenwood.plus.ui.screens.boutique.boutonCommander
import school.greenwood.plus.ui.screens.boutique.libelléPrix
import school.greenwood.plus.ui.filtrerProduits
import school.greenwood.plus.ui.prixUnitaire
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * La boutique : normalisation des formes sondées le 21/09/2026 (catalogue,
 * détail, historique) et calculs d'affichage du prix — fonctions pures.
 */
class BoutiqueTest {

    // — Normaliseurs (formes réelles de la sonde) ---------------------------

    @Test
    fun `rubrique - id en nombre comme en chaîne, icône aplatie`() {
        val tout = Json.parseToJsonElement(
            """{"id": -1, "label": "Tout", "icon": {"link": "http://x/c.jpeg", "bg": "#F3F3F3", "notif": 0}}""",
        ).jsonObject
        val uniforme = Json.parseToJsonElement(
            """{"id": "1", "label": "Uniforme scolaire ", "icon": {"link": "http://x/u.jpeg", "bg": "#FCCD84", "notif": "19"}}""",
        ).jsonObject
        val a = Normalizers.rubrique(tout)!!
        val b = Normalizers.rubrique(uniforme)!!
        assertEquals("-1", a.id)
        assertEquals("Tout", a.label)
        assertEquals("http://x/c.jpeg", a.icone)
        assertEquals("1", b.id)
        assertEquals(19, b.notif)
    }

    @Test
    fun `produit du catalogue - prix en libellé d'affichage`() {
        val raw = Json.parseToJsonElement(
            """{"id":"2","label":"BOMBER","barcode":null,"image":"https://media/p.png","price":"250 DH"}""",
        ).jsonObject
        val produit = Normalizers.produitCatalogue(raw)!!
        assertEquals("2", produit.id)
        assertEquals("BOMBER", produit.label)
        assertEquals("250 DH", produit.prix)
    }

    @Test
    fun `détail produit - variantes, préremplissage de modification`() {
        val raw = Json.parseToJsonElement(
            """
            {"product":{"id":"7","label":"CHEMISE BLANCHE","image":"https://media/p.png",
             "price":"150","inCart":false,"can_add_to_cart":true},
             "variants":[{"id":"30","label":"10 ANS","color":"#000000","amount":"150","qte":"5"},
                         {"id":"31","label":"12 ANS","color":"#000000","amount":"150","qte":"3"}],
             "commande":{"qte":2,"size":"2XL","comment":"prénom cousu"}}
            """.trimIndent(),
        ).jsonObject
        val détail = Normalizers.produitDétail(raw)!!
        assertEquals("7", détail.id)
        assertEquals(true, détail.peutCommander)
        assertEquals(2, détail.variantes.size)
        assertEquals(5, détail.variantes[0].stock)
        assertEquals("150", détail.variantes[0].montant)
        assertEquals("2XL", détail.prérempli?.taille)
        assertEquals(2, détail.prérempli?.quantité)
        assertEquals("prénom cousu", détail.prérempli?.commentaire)
    }

    @Test
    fun `détail produit sans commande - pas de préremplissage`() {
        val raw = Json.parseToJsonElement(
            """{"product":{"id":"6","label":"CASQUETTE","price":"100","can_add_to_cart":false},"variants":[]}""",
        ).jsonObject
        val détail = Normalizers.produitDétail(raw)!!
        assertEquals(false, détail.peutCommander)
        assertEquals(0, détail.variantes.size)
        assertNull(détail.prérempli)
    }

    @Test
    fun `commande de l'historique - état, articles, drapeaux`() {
        val raw = Json.parseToJsonElement(
            """
            {"id":"2340","date":"21 Sep 2026 22:21","price":"150","articles_count":"x1 Articles",
             "articles":[{"id":"3508","product_id":"7","image":"https://media/p.png",
                          "label":"CHEMISE BLANCHE","size":"12 ANS","quantity":"x1","price":"150",
                          "can_edit":true,"can_delete":true}],
             "state":{"alias":"en-cours","label":"En cours"},"can_delete":true}
            """.trimIndent(),
        ).jsonObject
        val commande = Normalizers.commande(raw)!!
        assertEquals("2340", commande.id)
        assertEquals("en-cours", commande.étatAlias)
        assertEquals(true, commande.supprimable)
        assertEquals(1, commande.articles.size)
        val article = commande.articles[0]
        assertEquals("12 ANS", article.taille)
        assertEquals(1, article.quantité)
        assertEquals(true, article.modifiable)
    }

    // — Calculs d'affichage --------------------------------------------------

    @Test
    fun `prix unitaire - la variante gagne, base sinon`() {
        val détail = ProduitDétail(
            id = "7",
            label = "CHEMISE",
            prixRaw = "150",
            variantes = listOf(
                school.greenwood.plus.model.VarianteBoutique(id = "31", label = "12 ANS", montant = "170"),
            ),
        )
        assertEquals("170", prixUnitaire(détail, "31"))
        assertEquals("150", prixUnitaire(détail, null))
        // Variante inconnue : retour au prix de base, jamais un plantage.
        assertEquals("150", prixUnitaire(détail, "999"))
    }

    @Test
    fun `bouton commander - total lisible, verbe de modification`() {
        assertEquals("Commander — 300 DH", boutonCommander("150", 2, modification = false))
        assertEquals("Mettre à jour — 150 DH", boutonCommander("150", 1, modification = true))
        assertEquals("Commander", boutonCommander(null, 3, modification = false))
        assertEquals("Commander", boutonCommander("abc", 3, modification = false))
    }

    @Test
    fun `libellé du prix - entier sans décimales, décimales sinon`() {
        assertEquals("150 DH", libelléPrix(150.0))
        assertEquals("157.5 DH", libelléPrix(157.5))
    }

    @Test
    fun `recherche locale du catalogue - casse ignorée, vide renvoie tout`() {
        val liste = listOf(
            ProduitBoutique(id = "2", label = "BOMBER"),
            ProduitBoutique(id = "7", label = "CHEMISE BLANCHE"),
        )
        assertEquals(listOf("2"), filtrerProduits(liste, "bom").map { it.id })
        assertEquals(listOf("2", "7"), filtrerProduits(liste, "").map { it.id })
        assertEquals(emptyList<String>(), filtrerProduits(liste, "chaussure").map { it.id })
    }
}
